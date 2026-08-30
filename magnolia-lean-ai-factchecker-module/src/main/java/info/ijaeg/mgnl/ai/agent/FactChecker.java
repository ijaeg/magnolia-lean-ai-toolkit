package info.ijaeg.mgnl.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface FactChecker {
    @SystemMessage(fromResource = "prompts/factCheckerSystemMessage.txt")
    @UserMessage(fromResource = "prompts/factCheckerUserMessage.txt")
    FactCheckResult check(@V("claim") String claim, @V("language") String language);

    record FactCheckResult(
            String explanation,
            FactCheckResult.Verdict verdict,
            String sourceUrl) {
        public enum Verdict { CORRECT, INCORRECT, UNVERIFIABLE }
    }
    record ClaimCheckResult(String claim, FactCheckResult.Verdict verdict, String explanation, String sourceUrl) {}
}
