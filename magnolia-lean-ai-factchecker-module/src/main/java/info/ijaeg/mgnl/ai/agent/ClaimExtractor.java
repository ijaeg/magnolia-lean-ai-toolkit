package info.ijaeg.mgnl.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

/**
 * LangChain4j {@code AiServices} interface for the claim-extraction step:
 * turns arbitrary page text into a list of self-contained, independently
 * verifiable factual claims.
 *
 * <p>The actual prompt logic lives outside this interface, as plain-text
 * resources under {@code src/main/resources/prompts/} ({@code
 * claimExtractorSystemMessage.txt} / {@code claimExtractorUserMessage.txt}),
 * loaded via {@code @SystemMessage}/{@code @UserMessage}'s {@code
 * fromResource}. This class only declares the method signature LangChain4j
 * proxies. See {@code CLAUDE.md} for the extraction rules and the rationale
 * behind them (few-shot examples, cross-sentence reference resolution,
 * marketing-language exclusion, etc.).</p>
 */
public interface ClaimExtractor {
    @SystemMessage(fromResource = "prompts/claimExtractorSystemMessage.txt")
    @UserMessage(fromResource = "prompts/claimExtractorUserMessage.txt")
    ClaimList extract(@V("text") String text, @V("language") String language);

    /**
     * @param claims   self-contained factual claims extracted from the input
     *                 text; empty (never {@code null} in a well-formed
     *                 response) if none were found
     * @param language language the claims were written in, echoed back by
     *                 the model rather than assumed by the caller
     */
    record ClaimList(List<String> claims, String language) {
    }
}
