package info.ijaeg.mgnl.ai.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jaeger.mgnl.tools.WikipediaTool;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import info.ijaeg.mgnl.ai.FactCheckerModule;
import info.magnolia.module.ModuleRegistry;
import info.magnolia.rest.client.factory.RestClientFactory;
import info.magnolia.rest.client.registry.RestClientRegistry;
import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class FactCheckerServiceImpl implements FactCheckerService {
    private final ModuleRegistry moduleRegistry;
    private final RestClientRegistry restClientRegistry;
    private final RestClientFactory restClientFactory;

    @Inject
    public FactCheckerServiceImpl(ModuleRegistry moduleRegistry, RestClientRegistry restClientRegistry, RestClientFactory restClientFactory) {
        this.moduleRegistry = moduleRegistry;
        this.restClientRegistry = restClientRegistry;
        this.restClientFactory = restClientFactory;
    }

    public record FactCheckResult(
            Verdict verdict,
            String explanation,
            String sourceUrl,
            String claim) {
        public enum Verdict { CORRECT, INCORRECT, UNVERIFIABLE }
    }

    public interface FactChecker {
        @SystemMessage("""
        You are an objective fact-checker. Use the ‘wikipediaLookup’ tool to verify claims.
        Respond ONLY in JSON format according to the schema.
        
        When calling wikipediaLookup, always search for the specific named entity
        (a place, person, organization, or object) the claim is about — never a
        paraphrase of the whole claim or an abstract concept (e.g. search "Ho Chi
        Minh City", not "Ho Chi Minh City traffic"; search "Saigon River", not
        "river through Ho Chi Minh City"). If a claim contains no specific named
        entity to look up (e.g. it is a vague or subjective statement), respond
        UNVERIFIABLE directly without calling the tool.
        
        When comparing the claim to the retrieved information, judge factual
        substance, not exact wording. Minor differences in phrasing, precision, or
        figures of speech (e.g. "set on a river" vs. "located along/near a river",
        "a few kilometers" vs. an exact distance that roughly matches) are NOT
        grounds for INCORRECT if the underlying fact is confirmed. Only use
        INCORRECT when the retrieved information actually contradicts the claim's
        substance — a fact, number, name, or relationship that does not match.
        """)
        @UserMessage("Prüfe folgende Behauptung: {{claim}}")
        FactCheckResult check(@V("claim") String claim);
    }

    public record ClaimList(List<String> claims) {}

    public interface ClaimExtractor {
        @SystemMessage("""
        You are a fact-extraction assistant. Your task is to read an arbitrary piece
        of text and extract every objectively verifiable factual claim it contains —
        and ONLY what the text itself asserts, nothing more.

        Extract a claim if it is a statement that:
        - Refers to a concrete, checkable fact (e.g. numbers, dates, locations,
          measurements, historical events, scientific or geographic statements,
          named entities and their properties).
        - Could in principle be proven true or false by consulting an external,
          reliable source (e.g. an encyclopedia).
        - Is actually stated in the text — not merely plausible or generally true.

        Do NOT extract a claim if it is:
        - An opinion, evaluation, or subjective judgment (e.g. "breathtaking",
          "the best", "highly recommended").
        - A reputation, ranking, acclaim, or superlative statement, even when
          phrased as if factual (e.g. "is considered one of the finest museums",
          "has earned a place among the great cuisines", "is one of the world's
          most exotic destinations", "is known as the city of lights"). Treat
          these as marketing language, not as verifiable facts, regardless of
          how the superlative or acclaim is worded.
        - Marketing or promotional language with no checkable content.
        - An instruction, question, or a call to action.
        - A vague or unfalsifiable statement (e.g. "many people enjoy this place").
        - A quantity, duration, or total that is not explicitly stated — do not
          calculate or infer totals from partial information (e.g. if the text
          says "the first week we do X, then we do Y", do NOT conclude "the trip
          lasts more than 7 days" — that total is never actually stated).

        CRITICAL — do not add outside knowledge:
        - Only rewrite what the text itself says. Do not add facts, relationships,
          categorizations, or context that you know to be true but that the text
          does not state, even if they are common knowledge and even if they seem
          like harmless connective context.
        - Only extract claims that are directly traceable to specific words in
          the text.

        CRITICAL — resolve references across the whole text, not just within
        one sentence:
        - If an entity is named in one sentence and a later sentence attributes
          a property to it via a reference word ("the capital", "the city",
          "it", "there", "the building"), resolve that reference using the
          nearest unambiguous preceding mention — even across sentence or
          paragraph boundaries — and produce a self-contained claim about the
          named entity. Do not drop such statements just because the entity
          name and the claim appear in different sentences.
        - Only resolve a reference if it is unambiguous. If multiple candidate
          entities were mentioned and it is unclear which one a reference word
          points to, do not guess — omit the claim instead.
        - Do not add a relationship or fact that requires resolving a reference
          to an entity that was never named in the text at all — that is
          outside knowledge (see rule above), not reference resolution.

        For each extracted claim:
        - Rewrite it as a single, self-contained sentence.
        - Split any sentence that bundles multiple distinct, independently
          checkable facts into separate claims — one claim per fact. A sentence
          combining a duration, a location, and a behavioural/factual detail
          must not be collapsed into a single claim; each checkable part gets
          its own claim. Only keep facts together in one claim if they are not
          independently checkable (e.g. a single measurement with its unit).

        If the text contains no verifiable factual claims, respond with exactly
        {"claims": []} — never omit the "claims" key entirely.

        ## Examples

        Text: "The Eiffel Tower, built in 1889, is truly a magical sight and a
        must-see for every visitor to Paris."
        Claims:
        - "The Eiffel Tower was built in 1889." (snippet: "built in 1889")
        (Opinions "magical" / "must-see" excluded.)

        Text: "Malaysian cooking has earned a place for itself among other great
        global cuisines."
        Claims: (none)
        (Reputation/acclaim statement — excluded even though factual-sounding.)

        Text: "Vietnam is one of the world's most exotic and culturally rich
        destinations."
        Claims: (none)
        (Superlative/acclaim statement in a different surface form from the
        example above — still excluded. The underlying pattern is the same:
        an unfalsifiable ranking or reputation claim, not a checkable fact.)

        Text: "We'll spend the first week in Kuala Lumpur, then head south before
        the finale in Singapore."
        Claims:
        - "The first week of the trip is spent in Kuala Lumpur." (snippet:
          "spend the first week in Kuala Lumpur")
        - "The trip finishes in Singapore." (snippet: "the finale in Singapore")
        (No claim about Singapore's geography or relationship to Malaysia is
        added — never stated. No total trip duration is added — never stated.)

        Text: "Set on the Saigon river, Ho Chi Minh City is a place where old
        and new coexist. The capital is the perfect example of progress and
        tradition living side-by-side."
        Claims:
        - "Ho Chi Minh City is set on the Saigon river." (snippet: "Set on the
          Saigon river")
        - "Ho Chi Minh City is the capital." (snippet: "The capital is the
          perfect example...")
        (The reference "The capital" in the second sentence is resolved to
        "Ho Chi Minh City", the only entity named so far — even though the
        claim and the entity name are in different sentences. This claim must
        NOT be dropped.)

        Text: "A two day tour of the Mekong delta will immerse you in a
        water-world maze where everything happens on the boats - even local
        markets which form every day from a raft of vendors boats."
        Claims:
        - "The tour of the Mekong delta lasts two days." (snippet: "A two day
          tour of the Mekong delta")
        - "Local markets in the Mekong delta form every day from vendors'
          boats." (snippet: "local markets which form every day from a raft
          of vendors boats")
        (Split into two independently checkable claims — duration and market
        behaviour. "Water-world maze" is marketing language and is dropped;
        the generic "everything happens on the boats" is too vague to be an
        independently checkable claim on its own and is not extracted
        separately.)

        Respond only with the structured result — no additional commentary.
        """)
        @UserMessage("Text:\n{{text}}")
        ClaimList extract(@V("text") String text);
    }

    @Override
    public List<FactCheckResult> check(String text) {
        FactCheckerModule module = moduleRegistry.getModuleInstance(FactCheckerModule.class);
        List<FactCheckResult> factCheckResultList = new ArrayList<>();
        FactChecker factChecker = AiServices.builder(FactChecker.class)
                .chatModel(getOllamaChatModel(module.getFactCheckerConfig().getFactCheckerChatModelConfg(), false))
                .tools(new WikipediaTool(restClientRegistry, restClientFactory))
                .build();
        List<String> claims = extractClaims(text, module);
        claims.forEach(claim -> {
            FactCheckResult result = factChecker.check(claim);
            factCheckResultList.add(result);
        });
        return factCheckResultList;
    }

    private List<String> extractClaims(String text, FactCheckerModule module) {
        ClaimExtractor extractor = AiServices.builder(ClaimExtractor.class)
                .chatModel(getOllamaChatModel(module.getFactCheckerConfig().getClaimExtractorChatModelConfg(), true))
                .build();
        ClaimList claimList = extractor.extract(Jsoup.parse(text).text());
        return (claimList != null && claimList.claims() != null) ? claimList.claims() : List.of();
    }

    private OllamaChatModel getOllamaChatModel(FactCheckerModule.OllamaChatModelConfig config, boolean format) {
        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .numCtx(config.getNumCtx())
                .temperature(config.getTemperature())
                .logRequests(config.isLogRequests())
                .logResponses(config.isLogResponses());

        if (format) {
            builder.supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
        }
        return builder.build();
    }
}
