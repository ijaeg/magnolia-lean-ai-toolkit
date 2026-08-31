package info.ijaeg.mgnl.ai.regression;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import info.ijaeg.mgnl.ai.FactCheckerModule;
import info.ijaeg.mgnl.ai.agent.FactChecker;
import info.ijaeg.mgnl.ai.factory.ChatModelFactory;
import info.magnolia.keystore.registry.PasswordRegistry;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class FactCheckerRegressionIT extends AbstractRegressionIT {

    /**
     * Builds the ChatModel for the FactChecker exactly via {@link ChatModelFactory}
     * (same factory/same path as {@code FactCheckerServiceImpl}).
     * Config values taken from {@code config.yaml} (factCheckerChatModelConfg +
     * includes/ollamaChatModelConfig.yaml): no
     * {@code supportedCapabilities} override, stays empty as in the original -
     * see CLAUDE.md for why RESPONSE_FORMAT_JSON_SCHEMA is deliberately NOT
     * set here (breaks tool-calling in combination with schema enforcement).
     *
     * <p>Deliberately no caching via {@code @BeforeClass}: a new
     * {@code FactChecker} with its own
     * {@link FakeWikipediaTool} is built per test case anyway (see
     * {@link #checkSingleCase}), so re-creating the ChatModel itself costs
     * nothing noteworthy compared to the actual Ollama call.</p>
     */
    private static ChatModel buildCheckerChatModel() {
        ChatModelFactory chatModelFactory = new ChatModelFactory(mock(PasswordRegistry.class));

        FactCheckerModule.ChatModelConfig config = new FactCheckerModule.ChatModelConfig();
        config.setProviderType(ModelProvider.OLLAMA);
        config.setBaseUrl("http://localhost:11434");
        config.setModelName("llama3.1:8b-instruct-q4_K_M");
        config.setNumCtx(4096);
        config.setNumPredict(1024);
        config.setTemperature(0.0);
        config.setLogRequests(true);
        config.setLogResponses(true);
        config.setTimeoutInSeconds(150);

        return chatModelFactory.createChatModel(config);
    }

    @Test
    public void factChecker_regressionSuite() {
        List<CheckerCase> cases = RegressionCaseLoader.loadCheckerCases(
                "/regression-testdata/checker-cases");
        runAllAndAssert(cases, this::checkSingleCase, CheckerCase::name);
    }

    private void checkSingleCase(CheckerCase testCase) {
        FactChecker factChecker = AiServices.builder(FactChecker.class)
                .chatModel(buildCheckerChatModel())
                .tools(new FakeWikipediaTool(testCase.wikipediaFixtures()))
                .build();

        FactChecker.FactCheckResult result = factChecker.check(testCase.claim(), testCase.languageCode());

        assertEquals(testCase.name() + ": expected verdict does not match",
                testCase.expectedVerdict(), result.verdict());
    }
}