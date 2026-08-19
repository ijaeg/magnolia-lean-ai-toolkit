package com.jaeger.mgnl.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import info.magnolia.rest.client.RestClient;
import info.magnolia.rest.client.RestClientDefinition;
import info.magnolia.rest.client.factory.RestClientFactory;
import info.magnolia.rest.client.registry.RestClientRegistry;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.joining;

public class WikipediaTool {
    private final RestClientDefinition restClientDefinition;
    private final RestClient restClient;

    @Inject
    public WikipediaTool(RestClientRegistry restClientRegistry, RestClientFactory restClientFactory) {
        restClientDefinition = restClientRegistry.getProvider("wikipedia").get();
        restClient = restClientFactory.createClient(restClientDefinition);
    }

    @Tool("""
    Searches Wikipedia for a named entity and returns its summary. Only pass
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
            @P("Disambiguating context, e.g. 'Vung Tau Vietnam colonial villa' — pass an empty string only if the name is already unambiguous") String context) {

        List<SearchHit> hits = search(query);
        if ((hits.isEmpty() || !isClearlyBestMatch(hits, query, context)) && !context.isBlank()) {
            hits = search(query + " " + context);
        }
        if (hits.isEmpty()) {
            return WikipediaResult.notFound(query);
        }
        if (!isClearlyBestMatch(hits, query, context)) {
            return WikipediaResult.ambiguous(query, hits);
        }

        SearchHit hit = hits.get(0);
        return WikipediaResult.found(hit.title, findWikpediaSummary(hit.key), hit.pageUrl());
    }

    private List<SearchHit> search(String query) {
        List<SearchHit> hits = new ArrayList<>();
        Response response = restClient.invoke("search", Map.of("q", URLEncoder.encode(query, StandardCharsets.UTF_8).replaceAll("\\+", "%20")));
        JsonNode jsonNode = (JsonNode) response.getEntity();
        Iterator<JsonNode> iterator = jsonNode.get("pages").elements();
        while (iterator.hasNext()) {
            JsonNode page = iterator.next();
            String key = page.get("key").asText();
            String title =  page.get("title").asText();
            if (StringUtils.isAnyEmpty(key, title)) {
                continue;
            }
            String description = page.hasNonNull("description")
                    ?  page.get("description").asText()
                    : Jsoup.parse(page.get("excerpt").asText()).text();
            hits.add(new SearchHit(key, title, description));
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

    private String findWikpediaSummary(String key) {
        Response response = restClient.invoke("summary", Map.of("key", key));
        JsonNode jsonNode = (JsonNode) response.getEntity();
        return jsonNode.get("extract").textValue();
    }

    public record SearchHit(String key, String title, String description) {
        String pageUrl() {
            return "https://en.wikipedia.org/wiki/" + key;
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
