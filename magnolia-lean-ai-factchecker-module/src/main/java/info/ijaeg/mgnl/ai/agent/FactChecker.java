package info.ijaeg.mgnl.ai.agent;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface FactChecker {
    @UserMessage("""
       Claim to verify: {{claim}}

       Respond with a single JSON object, with the fields in exactly this order:
       {
          "explanation": "<write your reasoning here, IN {{language}} — this
            is the language of the claim above, regardless of what language the
            system prompt, tool descriptions, or Wikipedia results are in>",
          "verdict": "<CORRECT, INCORRECT, or UNVERIFIABLE — must follow logically
            from the explanation you just wrote, never decided beforehand>",
          "sourceUrl": "<the Wikipedia URL you used, or an empty string>"
       }
       No text before or after the JSON object.
        """)
    FactCheckResult check(@V("claim") String claim, @V("language") String language);

    record FactCheckResult(
            String explanation,
            FactCheckResult.Verdict verdict,
            String sourceUrl) {
        public enum Verdict { CORRECT, INCORRECT, UNVERIFIABLE }
    }
    record ClaimCheckResult(String claim, FactCheckResult.Verdict verdict, String explanation, String sourceUrl) {}
}
