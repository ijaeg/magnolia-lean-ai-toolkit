package info.ijaeg.mgnl.ai.tool;

import info.magnolia.config.registry.DefinitionProvider;
import info.magnolia.config.registry.Registry;
import info.magnolia.rest.client.RestClient;
import info.magnolia.rest.client.RestClientDefinition;
import info.magnolia.rest.client.factory.RestClientFactory;
import info.magnolia.rest.client.registry.RestClientRegistry;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class WikipediaToolTest {

    private RestClientRegistry restClientRegistry;
    private RestClientFactory restClientFactory;
    private RestClient restClient;
    private WikipediaTool tool;

    @Before
    public void setUp() {
        restClientRegistry = mock(RestClientRegistry.class);
        restClientFactory = mock(RestClientFactory.class);
        restClient = mock(RestClient.class);

        DefinitionProvider<RestClientDefinition> provider = mock(DefinitionProvider.class);
        RestClientDefinition definition = mock(RestClientDefinition.class);
        when(provider.get()).thenReturn(definition);
        when(restClientRegistry.getProvider("en.wikipedia")).thenReturn(provider);
        when(restClientFactory.createClient(definition)).thenReturn(restClient);

        tool = new WikipediaTool(restClientRegistry, restClientFactory);
    }

    // NOTE: never pass the result of this method directly as an argument to another when(...)/thenReturn(...)
    // call - it runs its own when(...) internally, and Mockito's stubbing state does not support interleaving
    // two when(...) calls (the outer one's argument would be evaluated before the outer thenReturn() completes,
    // raising UnfinishedStubbingException). Always assign it to a local variable first.
    private Response jsonResponse(String json) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(response.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
        when(response.getEntity()).thenReturn(json);
        return response;
    }

    @Test
    public void wikipediaLookup_emptyQuery_returnsNotFoundWithoutCallingRestClient() {
        WikipediaTool.WikipediaResult result = tool.wikipediaLookup("", "", "en");

        assertEquals(WikipediaTool.WikipediaResult.Status.NOT_FOUND, result.status());
        verifyNoInteractions(restClientFactory);
    }

    @Test
    public void wikipediaLookup_singleHit_returnsFoundWithCorrectPageUrl() {
        String searchJson = "{\"pages\":[{\"key\":\"Ho_Chi_Minh_City\",\"title\":\"Ho Chi Minh City\",\"description\":\"city in Vietnam\"}]}";
        String summaryJson = "{\"extract\":\"Ho Chi Minh City is the largest city in Vietnam.\"}";
        Response searchResponse = jsonResponse(searchJson);
        Response summaryResponse = jsonResponse(summaryJson);
        when(restClient.invoke(eq("search"), anyMap())).thenReturn(searchResponse);
        when(restClient.invoke(eq("summary"), anyMap())).thenReturn(summaryResponse);

        WikipediaTool.WikipediaResult result = tool.wikipediaLookup("Ho Chi Minh City", "", "en");

        assertEquals(WikipediaTool.WikipediaResult.Status.FOUND, result.status());
        assertEquals("Ho Chi Minh City", result.title());
        assertEquals("Ho Chi Minh City is the largest city in Vietnam.", result.extract());
        assertEquals("https://en.wikipedia.org/wiki/Ho_Chi_Minh_City", result.sourceUrl());
    }

    @Test
    public void wikipediaLookup_multipleHitsNoContext_returnsAmbiguous() {
        String searchJson = "{\"pages\":[" +
                "{\"key\":\"White_Palace_1\",\"title\":\"White Palace (Vung Tau)\",\"description\":\"colonial villa\"}," +
                "{\"key\":\"White_Palace_2\",\"title\":\"White Palace (Munich)\",\"description\":\"cinema\"}" +
                "]}";
        Response searchResponse = jsonResponse(searchJson);
        when(restClient.invoke(eq("search"), anyMap())).thenReturn(searchResponse);

        WikipediaTool.WikipediaResult result = tool.wikipediaLookup("White Palace", "", "en");

        assertEquals(WikipediaTool.WikipediaResult.Status.AMBIGUOUS, result.status());
        // empty context must not trigger a second, context-refined search
        verify(restClient, times(1)).invoke(eq("search"), anyMap());
    }

    @Test
    public void wikipediaLookup_ambiguousTitlesResolvedByContext_returnsFoundForBestMatch() {
        // both hits share the exact same title, so only the description/context check can disambiguate them
        String searchJson = "{\"pages\":[" +
                "{\"key\":\"White_Palace_Vung_Tau\",\"title\":\"White Palace\",\"description\":\"Vung Tau colonial villa\"}," +
                "{\"key\":\"White_Palace_Munich\",\"title\":\"White Palace\",\"description\":\"Munich cinema\"}" +
                "]}";
        String summaryJson = "{\"extract\":\"A colonial villa in Vung Tau.\"}";
        Response searchResponse = jsonResponse(searchJson);
        Response summaryResponse = jsonResponse(summaryJson);
        when(restClient.invoke(eq("search"), anyMap())).thenReturn(searchResponse);
        when(restClient.invoke(eq("summary"), anyMap())).thenReturn(summaryResponse);

        WikipediaTool.WikipediaResult result = tool.wikipediaLookup("White Palace", "Vung Tau colonial villa", "en");

        assertEquals(WikipediaTool.WikipediaResult.Status.FOUND, result.status());
        assertEquals("https://en.wikipedia.org/wiki/White_Palace_Vung_Tau", result.sourceUrl());
        // resolved from the bare-query search alone, no context-refined fallback search needed
        verify(restClient, times(1)).invoke(eq("search"), anyMap());
    }

    @Test
    public void wikipediaLookup_ambiguousBareQuery_fallsBackToContextRefinedSearch() {
        String ambiguousJson = "{\"pages\":[" +
                "{\"key\":\"White_Palace_1\",\"title\":\"White Palace (Vung Tau)\",\"description\":\"colonial villa\"}," +
                "{\"key\":\"White_Palace_2\",\"title\":\"White Palace (Munich)\",\"description\":\"cinema\"}" +
                "]}";
        String refinedJson = "{\"pages\":[" +
                "{\"key\":\"White_Palace_Vung_Tau\",\"title\":\"White Palace (Vung Tau)\",\"description\":\"Vung Tau colonial villa\"}" +
                "]}";
        String summaryJson = "{\"extract\":\"A colonial villa in Vung Tau.\"}";
        Response ambiguousResponse = jsonResponse(ambiguousJson);
        Response refinedResponse = jsonResponse(refinedJson);
        Response summaryResponse = jsonResponse(summaryJson);
        when(restClient.invoke(eq("search"), anyMap()))
                .thenReturn(ambiguousResponse)
                .thenReturn(refinedResponse);
        when(restClient.invoke(eq("summary"), anyMap())).thenReturn(summaryResponse);

        WikipediaTool.WikipediaResult result = tool.wikipediaLookup("White Palace", "Vung Tau colonial villa", "en");

        assertEquals(WikipediaTool.WikipediaResult.Status.FOUND, result.status());
        assertEquals("https://en.wikipedia.org/wiki/White_Palace_Vung_Tau", result.sourceUrl());
        verify(restClient, times(2)).invoke(eq("search"), anyMap());
    }

    @Test
    public void wikipediaLookup_encodesQuerySpacesAsPercent20NotPlus() {
        Response emptyResponse = jsonResponse("{\"pages\":[]}");
        when(restClient.invoke(eq("search"), anyMap())).thenReturn(emptyResponse);

        tool.wikipediaLookup("Ho Chi Minh City", "", "en");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(restClient).invoke(eq("search"), captor.capture());
        assertEquals("Ho%20Chi%20Minh%20City", captor.getValue().get("q"));
    }

    @Test
    public void wikipediaLookup_unknownLanguage_fallsBackToEnglishRestClient() {
        when(restClientRegistry.getProvider("de.wikipedia")).thenThrow(new Registry.NoSuchDefinitionException("no de client registered"));
        Response emptyResponse = jsonResponse("{\"pages\":[]}");
        when(restClient.invoke(eq("search"), anyMap())).thenReturn(emptyResponse);

        WikipediaTool.WikipediaResult result = tool.wikipediaLookup("Berlin", "", "de");

        assertEquals(WikipediaTool.WikipediaResult.Status.NOT_FOUND, result.status());
        verify(restClientFactory, times(1)).createClient(any());
    }
}
