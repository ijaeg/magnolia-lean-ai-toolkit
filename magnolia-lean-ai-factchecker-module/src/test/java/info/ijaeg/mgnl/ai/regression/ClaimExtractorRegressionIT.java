package info.ijaeg.mgnl.ai.regression;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import info.ijaeg.mgnl.ai.FactCheckerModule;
import info.ijaeg.mgnl.ai.agent.ClaimExtractor;
import info.ijaeg.mgnl.ai.factory.ChatModelFactory;
import info.magnolia.keystore.registry.PasswordRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

// Nur ClaimExtractorRegressionIT kennt ExtractorCase - CheckerCase taucht
// hier nirgends auf.
class ClaimExtractorRegressionIT extends AbstractRegressionIT {

    private static ClaimExtractor claimExtractor;

    @BeforeAll
    static void setUp() {
        // buildExtractorChatModel(): echtes Ollama-Modell mit Produktions-Config
        // (numCtx=8192, siehe CLAUDE.md) - Aufbau hier aus Übersichtlichkeit ausgelassen.
        claimExtractor = AiServices.create(ClaimExtractor.class, buildExtractorChatModel());
    }

    @Test
    void claimExtractor_regressionSuite() {
        List<ExtractorCase> cases = RegressionCaseLoader.loadExtractorCases(
                "/regression-testdata/extractor-cases");
        runAllAndAssert(cases, this::checkSingleCase, ExtractorCase::name);
    }

    private static ChatModel buildExtractorChatModel() {
        ChatModelFactory chatModelFactory = new ChatModelFactory(mock(PasswordRegistry.class));

        FactCheckerModule.ChatModelConfig config = new FactCheckerModule.ChatModelConfig();
        config.setProviderType(ModelProvider.OLLAMA);
        config.setBaseUrl("http://localhost:11434");
        config.setModelName("qwen2.5:7b-instruct");
        config.setNumCtx(8192);
        config.setNumPredict(1024);
        config.setTemperature(0.1);
        config.setLogRequests(true);
        config.setLogResponses(true);
        config.setSupportedCapabilities(new Capability[]{Capability.RESPONSE_FORMAT_JSON_SCHEMA});

        return chatModelFactory.createChatModel(config);
    }

    private void checkSingleCase(ExtractorCase testCase) {
        List<String> claims = claimExtractor.extract(testCase.text(), testCase.languageCode()).claims();
        String joined = String.join(" | ", claims);

        for (String mustContain : testCase.mustContainSubstrings()) {
            assertTrue(joined.contains(mustContain),
                    testCase.name() + ": erwarteter Substring fehlt: \"" + mustContain + "\"");
        }
        for (String mustNotContain : testCase.mustNotContainSubstrings()) {
            assertFalse(joined.contains(mustNotContain),
                    testCase.name() + ": unerwarteter Substring vorhanden: \"" + mustNotContain + "\"");
        }
    }
}