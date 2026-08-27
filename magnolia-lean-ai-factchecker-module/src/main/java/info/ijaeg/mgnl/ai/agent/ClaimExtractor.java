package info.ijaeg.mgnl.ai.agent;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

public interface ClaimExtractor {
    @UserMessage("""
            Language of the text: {{language}}
            Text:
            {{text}}
            """)
    ClaimList extract(@V("text") String text, @V("language") String language);

    record ClaimList(List<String> claims, String language) {}
}
