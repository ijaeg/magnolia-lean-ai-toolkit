package info.ijaeg.mgnl.ai.factory;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import info.ijaeg.mgnl.ai.FactCheckerModule;
import info.magnolia.jcr.util.SessionUtil;
import info.magnolia.keystore.Password;
import info.magnolia.keystore.registry.PasswordRegistry;
import org.junit.Test;
import org.mockito.MockedStatic;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ChatModelFactoryTest {

    private final PasswordRegistry passwordRegistry = mock(PasswordRegistry.class);
    private final ChatModelFactory factory = new ChatModelFactory(passwordRegistry);

    @Test
    public void createChatModel_throwsWhenProviderTypeIsNull() {
        FactCheckerModule.ChatModelConfig config = new FactCheckerModule.ChatModelConfig();

        assertThrows(IllegalArgumentException.class, () -> factory.createChatModel(config));
    }

    @Test
    public void createChatModel_ollama_buildsOllamaChatModel() {
        FactCheckerModule.ChatModelConfig config = new FactCheckerModule.ChatModelConfig();
        config.setProviderType(ModelProvider.OLLAMA);
        config.setBaseUrl("http://localhost:11434");
        config.setModelName("llama3.1:8b-instruct-q4_K_M");
        config.setTimeoutInSeconds(150);

        ChatModel model = factory.createChatModel(config);

        assertTrue(model instanceof OllamaChatModel);
    }

    @Test
    public void createChatModel_openAi_buildsOpenAiChatModel() {
        FactCheckerModule.ChatModelConfig config = new FactCheckerModule.ChatModelConfig();
        config.setProviderType(ModelProvider.OPEN_AI);
        config.setApiKey("test-key");
        config.setModelName("gpt-4o-mini");
        config.setTimeoutInSeconds(150);

        ChatModel model;
        try (MockedStatic<SessionUtil> sessionUtil = mockStatic(SessionUtil.class)) {
            sessionUtil.when(() -> SessionUtil.getNode(anyString(), anyString())).thenReturn(null);
            model = factory.createChatModel(config);
        }

        assertTrue(model instanceof OpenAiChatModel);
    }

    @Test
    public void createChatModel_anthropic_buildsAnthropicChatModel() throws RepositoryException {
        FactCheckerModule.ChatModelConfig config = new FactCheckerModule.ChatModelConfig();
        config.setProviderType(ModelProvider.ANTHROPIC);
        config.setApiKey("test-key");
        config.setModelName("claude-3-5-sonnet-20241022");

        Node keystoreNode = mock(Node.class);
        when(keystoreNode.getIdentifier()).thenReturn("keystore-node-id");
        Password password = mock(Password.class);
        when(password.getDecryptedValue()).thenReturn("resolved-api-key");
        when(passwordRegistry.getPassword("keystore-node-id")).thenReturn(password);

        ChatModel model;
        try (MockedStatic<SessionUtil> sessionUtil = mockStatic(SessionUtil.class)) {
            sessionUtil.when(() -> SessionUtil.getNode(anyString(), anyString())).thenReturn(keystoreNode);
            model = factory.createChatModel(config);
        }

        assertTrue(model instanceof AnthropicChatModel);
    }

    @Test
    public void createChatModel_unsupportedProvider_throws() {
        FactCheckerModule.ChatModelConfig config = new FactCheckerModule.ChatModelConfig();
        config.setProviderType(ModelProvider.GOOGLE_AI_GEMINI);

        assertThrows(IllegalArgumentException.class, () -> factory.createChatModel(config));
    }
}
