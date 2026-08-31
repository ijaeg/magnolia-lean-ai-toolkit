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
     * Baut den ChatModel für den FactChecker exakt über {@link ChatModelFactory}
     * (dieselbe Fabrik/derselbe Weg wie {@code FactCheckerServiceImpl}).
     * Config-Werte aus {@code config.yaml} (factCheckerChatModelConfg +
     * includes/ollamaChatModelConfig.yaml) übernommen: kein
     * {@code supportedCapabilities}-Override, bleibt leer wie im Original -
     * siehe CLAUDE.md, warum RESPONSE_FORMAT_JSON_SCHEMA hier bewusst NICHT
     * gesetzt wird (bricht Tool-Calling in Kombination mit Schema-Zwang).
     *
     * <p>Bewusst kein Caching über {@code @BeforeClass}: pro Testfall wird
     * ohnehin ein neuer {@code FactChecker} mit eigenem
     * {@link FakeWikipediaTool} gebaut (siehe {@link #checkSingleCase}),
     * das Neu-Erzeugen des ChatModel selbst kostet dabei nichts
     * Nennenswertes gegenüber dem eigentlichen Ollama-Aufruf.</p>
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

        assertEquals(testCase.name() + ": erwartetes Verdict stimmt nicht",
                testCase.expectedVerdict(), result.verdict());
    }
}