package info.ijaeg.mgnl.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import info.magnolia.config.registry.Registry;
import info.magnolia.rest.client.RestClient;
import info.magnolia.rest.client.RestClientDefinition;
import info.magnolia.rest.client.factory.RestClientFactory;
import info.magnolia.rest.client.registry.RestClientRegistry;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.jsoup.Jsoup;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.joining;

/**
 * LangChain4j {@code @Tool} used by {@link info.ijaeg.mgnl.ai.agent.FactChecker}
 * to look up a named entity on Wikipedia. Search strategy and disambiguation
 * logic were shaped by extensive trial and error against real (occasionally
 * adversarial) content — see {@code CLAUDE.md} for the reasoning behind the
 * "bare query first" search order, the {@code %20} vs {@code +} URL-encoding
 * fix, the required, policy-compliant {@code User-Agent} header (configured
 * per REST client, not in this class), and {@link #isClearlyBestMatch}'s
 * disambiguation heuristic. Returns a typed {@link WikipediaResult} rather
 * than throwing on anything short of a clear match, so the calling model can
 * fall back to {@code UNVERIFIABLE} instead of being handed a guess.
 */
@Slf4j
public class WikipediaTool {
    public static final String REST_CLIENT_BASE_NAME = "wikipedia";
    public static final String DEFAULT_REST_CLIENT_NAME = "en." + REST_CLIENT_BASE_NAME;

    private final RestClientRegistry restClientRegistry;
    private final RestClientFactory restClientFactory;
    private final ObjectMapper objectMapper;

    @Inject
    public WikipediaTool(RestClientRegistry restClientRegistry, RestClientFactory restClientFactory) {
        this.restClientRegistry = restClientRegistry;
        this.restClientFactory = restClientFactory;
        this.objectMapper = new ObjectMapper();
    }

    @Tool("""
            Searches Wikipedia for a named entity in the given language and returns its summary. Only pass
            non-empty context when the entity name by itself is genuinely generic or
            reused across many places (e.g. "White Palace", "Old Town", "National
            Museum"). Well-known, specific proper nouns (city names, named rivers,
            named landmarks) should be looked up WITHOUT context — do not combine two
            proper nouns from the claim into one search request.
            If no sufficiently matching article can be found, this returns a
            NOT_FOUND marker instead of guessing.
            """)
    WikipediaResult wikipediaLookup(
            @P("The entity or claim subject to look up, e.g. 'White Palace'") String query,
            @P("Disambiguating context, e.g. 'Vung Tau Vietnam colonial villa' — pass an empty string only if the name is already unambiguous") String context,
            @P("ISO-639-1 language code, e.g. en, de") String language) {

        if (StringUtils.isEmpty(query)) {
            return WikipediaResult.notFound(query);
        }

        List<SearchHit> hits = search(query, language);
        if ((hits.isEmpty() || !isClearlyBestMatch(hits, query, context)) && !context.isBlank()) {
            hits = search(query + " " + context, language);
        }
        if (hits.isEmpty()) {
            return WikipediaResult.notFound(query);
        }
        if (!isClearlyBestMatch(hits, query, context)) {
            return WikipediaResult.ambiguous(query, hits);
        }

        SearchHit hit = hits.get(0);
        return WikipediaResult.found(hit.title, findWikpediaSummary(hit.key, language), hit.pageUrl(language));
    }

    private List<SearchHit> search(String query, String language) {
        List<SearchHit> hits = new ArrayList<>();
        String jsonString = null;
        try {
            Response response = getRestClient(language).invoke("search", Map.of("q", URLEncoder.encode(query, StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
            verifyResponse(response);
            jsonString = response.getEntity().toString();
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            Iterator<JsonNode> iterator = jsonNode.get("pages").elements();
            while (iterator.hasNext()) {
                try {
                    JsonNode page = iterator.next();
                    String key = page.get("key").asText();
                    String title = page.get("title").asText();
                    if (StringUtils.isAnyEmpty(key, title)) {
                        continue;
                    }
                    String description = page.hasNonNull("description")
                            ? page.get("description").asText()
                            : Jsoup.parse(page.get("excerpt").asText()).text();
                    hits.add(new SearchHit(key, title, description));
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    log.info("query: " + query);
                    log.info("response: " + jsonString);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            log.info("query: " + query);
            log.info("response: " + jsonString);
        }
        return hits;
    }

    private boolean isClearlyBestMatch(List<SearchHit> hits, String query, String context) {
        if (hits.size() == 1) return true;

        SearchHit top = hits.get(0);
        SearchHit runnerUp = hits.get(1);

        boolean topIsExactMatch = top.title().equalsIgnoreCase(query);
        boolean runnerUpIsExactMatch = runnerUp.title().equalsIgnoreCase(query);
        if (topIsExactMatch && !runnerUpIsExactMatch) {
            return true;
        }

        String ctxLower = context.toLowerCase();
        if (ctxLower.isBlank()) {
            return false;
        }

        String firstWord = ctxLower.split(" ")[0];
        boolean topMatchesContext = top.description().toLowerCase().contains(firstWord);
        boolean runnerUpAlsoMatches = runnerUp.description().toLowerCase().contains(firstWord);
        return topMatchesContext && !runnerUpAlsoMatches;
    }

    private String findWikpediaSummary(String key, String language) {
        String jsonString = null;
        try {
            Response response = getRestClient(language).invoke("summary", Map.of("key", key));
            verifyResponse(response);
            jsonString = response.getEntity().toString();
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            return jsonNode.get("extract").textValue();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            log.info("key: " + key);
            log.info("response: " + jsonString);
            return StringUtils.EMPTY;
        }
    }

    private RestClient getRestClient(String language) {
        try {
            RestClientDefinition restClientDefinition = restClientRegistry.getProvider(language + "." + REST_CLIENT_BASE_NAME).get();
            return restClientFactory.createClient(restClientDefinition);
        } catch (Registry.NoSuchDefinitionException e) {
            log.error(e.getMessage(), e);
            return restClientFactory.createClient(restClientRegistry.getProvider(DEFAULT_REST_CLIENT_NAME).get());
        }
    }

    /**
     * Guards against treating a non-JSON response body as JSON. Wikimedia's
     * edge/CDN occasionally returns an HTTP error or a plain-text block page
     * instead of the expected JSON API response (e.g. under rate limiting or
     * a rejected {@code User-Agent}) — without this check, that body would
     * otherwise reach {@code objectMapper.readTree(...)} and fail with a
     * confusing {@code JsonParseException} pointing at RESTEasy's own error
     * text rather than the real cause. See {@code CLAUDE.md}.
     *
     * @throws IllegalStateException if the response is not a {@code 200} with
     *                               an {@code application/json} content type; caught by the callers
     *                               of this method, which then degrade to an empty result rather
     *                               than propagating the failure
     */
    private void verifyResponse(Response response) {
        if (response.getStatus() != HttpStatus.SC_OK || !response.getMediaType().isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            throw new IllegalStateException(MessageFormat.format("Unexpected response - status code: {0}, content type: {1}", response.getStatus(), response.getMediaType()));
        }
    }

    public record SearchHit(String key, String title, String description) {
        String pageUrl(String language) {
            return MessageFormat.format("https://{0}.wikipedia.org/wiki/{1}", language, key);
        }
    }

    /**
     * Result of a {@link #wikipediaLookup} call, returned to the calling
     * model instead of a raw exception so it can decide how to proceed
     * (typically: fall back to {@code UNVERIFIABLE} on anything but {@link
     * Status#FOUND}).
     *
     * @param status    whether a confident match was found
     * @param title     matched article title ({@link Status#FOUND}), the
     *                  original query ({@link Status#NOT_FOUND}), or {@code
     *                  null} ({@link Status#AMBIGUOUS})
     * @param extract   article summary ({@link Status#FOUND}), or a
     *                  human-readable explanation of why no result could be
     *                  returned (otherwise)
     * @param sourceUrl Wikipedia article URL, or {@code null} if there is
     *                  none to point to
     */
    public record WikipediaResult(Status status, String title, String extract, String sourceUrl) {
        /**
         * Outcome of a single {@link #wikipediaLookup} call.
         */
        public enum Status {
            /**
             * Exactly one confident match was found; {@code extract} is its summary.
             */
            FOUND,
            /**
             * No search results at all for the given query.
             */
            NOT_FOUND,
            /**
             * Multiple candidates were found and none is clearly the best match.
             */
            AMBIGUOUS
        }

        static WikipediaResult found(String title, String extract, String url) {
            return new WikipediaResult(Status.FOUND, title, extract, url);
        }

        static WikipediaResult notFound(String query) {
            return new WikipediaResult(Status.NOT_FOUND, query, null, null);
        }

        static WikipediaResult ambiguous(String query, List<SearchHit> hits) {
            String candidates = hits.stream().map(SearchHit::title).collect(joining(", "));
            return new WikipediaResult(Status.AMBIGUOUS, null,
                    "No confident match for '" + query + "'. Candidates: " + candidates, null);
        }
    }
}
