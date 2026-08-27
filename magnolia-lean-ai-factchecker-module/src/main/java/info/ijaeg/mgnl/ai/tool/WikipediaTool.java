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
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.joining;

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

    public record SearchHit(String key, String title, String description) {
        String pageUrl(String language) {
            return MessageFormat.format( "https://{0}.wikipedia.org/wiki/{1}", language, key);
        }
    }

    public record WikipediaResult(Status status, String title, String extract, String sourceUrl) {
        public enum Status { FOUND, NOT_FOUND, AMBIGUOUS }

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
