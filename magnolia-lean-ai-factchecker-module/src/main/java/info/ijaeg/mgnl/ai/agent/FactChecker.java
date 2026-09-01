package info.ijaeg.mgnl.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LangChain4j {@code AiServices} interface for the fact-checking step: takes
 * one previously extracted claim and verifies it against Wikipedia via the
 * {@code wikipediaLookup} tool (see {@link info.ijaeg.mgnl.ai.tool.WikipediaTool}).
 *
 * <p>As with {@link ClaimExtractor}, the prompt itself lives outside this
 * interface as plain-text resources ({@code prompts/factCheckerSystemMessage.txt}
 * / {@code prompts/factCheckerUserMessage.txt}). See {@code CLAUDE.md} for
 * why {@code RESPONSE_FORMAT_JSON_SCHEMA} is deliberately not enabled for
 * this chat model (it broke tool-calling in combination with schema
 * enforcement) and for other model-specific pitfalls found while tuning
 * this prompt.</p>
 */
public interface FactChecker {
    @SystemMessage(fromResource = "prompts/factCheckerSystemMessage.txt")
    @UserMessage(fromResource = "prompts/factCheckerUserMessage.txt")
    FactCheckResult check(@V("claim") String claim, @V("language") String language);

    /**
     * Raw model output for a single claim, before the claim text itself is
     * re-attached by {@link info.ijaeg.mgnl.ai.service.FactCheckerServiceImpl}
     * (see {@link ClaimCheckResult}).
     *
     * @param explanation reasoning behind the verdict, in the language of
     *                    the checked claim; written before {@code verdict}
     *                    in the JSON response on purpose, so the model's
     *                    reasoning precedes and constrains its verdict
     *                    rather than the other way around
     * @param verdict     the model's judgment on the claim
     * @param sourceUrl   Wikipedia URL the verdict is based on, or an empty
     *                    string if no matching article was found ({@link
     *                    Verdict#UNVERIFIABLE})
     */
    record FactCheckResult(
            String explanation,
            FactCheckResult.Verdict verdict,
            String sourceUrl) {
        /**
         * Outcome of checking one claim against the retrieved Wikipedia article.
         */
        public enum Verdict {
            /**
             * Retrieved information directly and specifically confirms the claim's substance.
             */
            CORRECT,
            /**
             * Retrieved information directly contradicts the claim's substance.
             */
            INCORRECT,
            /**
             * No named entity to look up, no matching Wikipedia article, or
             * the retrieved article neither confirms nor contradicts the
             * specific detail claimed.
             */
            UNVERIFIABLE
        }
    }

    /**
     * A {@link FactCheckResult} paired back up with the claim text it
     * belongs to, for presentation to the editor. The claim itself is not
     * part of {@link FactCheckResult} to avoid having the model retype (and
     * potentially alter) text it already received verbatim as input.
     */
    record ClaimCheckResult(String claim, FactCheckResult.Verdict verdict, String explanation, String sourceUrl) {
    }
}
