package info.ijaeg.mgnl.ai.regression;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import info.ijaeg.mgnl.ai.tool.WikipediaTool;

import java.util.Map;

/**
 * Fake für {@link WikipediaTool} mit identischer {@code @Tool}/{@code @P}-
 * Signatur - Beschreibungstext 1:1 aus dem echten Tool übernommen, da er in
 * den Prompt einfließt (siehe {@code WikipediaTool.wikipediaLookup}).
 */
class FakeWikipediaTool {

    private final Map<String, WikipediaTool.WikipediaResult> fixtures;

    FakeWikipediaTool(Map<String, WikipediaTool.WikipediaResult> fixtures) {
        this.fixtures = fixtures;
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
    WikipediaTool.WikipediaResult wikipediaLookup(
            @P("The entity or claim subject to look up, e.g. 'White Palace'") String query,
            @P("Disambiguating context, e.g. 'Vung Tau Vietnam colonial villa' — pass an empty string only if the name is already unambiguous") String context,
            @P("ISO-639-1 language code, e.g. en, de") String language) {

        // Fuzzy statt exaktem Key-Vergleich: das Modell sucht ggf. mit leicht
        // abweichendem Begriff (z. B. "Jonas Vetter Bundespräsident").
        return fixtures.entrySet().stream()
                .filter(e -> query.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> new WikipediaTool.WikipediaResult(
                        WikipediaTool.WikipediaResult.Status.NOT_FOUND, query, null, null));
    }
}