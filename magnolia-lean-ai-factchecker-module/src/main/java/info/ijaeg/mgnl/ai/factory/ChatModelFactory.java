package info.ijaeg.mgnl.ai.factory;

import com.google.inject.Singleton;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import info.ijaeg.mgnl.ai.FactCheckerModule;
import info.magnolia.jcr.util.SessionUtil;
import info.magnolia.keystore.PasswordManagerCoreModule;
import info.magnolia.keystore.registry.PasswordRegistry;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

@Singleton
@Slf4j
public class ChatModelFactory {
    private final PasswordRegistry passwordRegistry;

    @Inject
    public ChatModelFactory(PasswordRegistry passwordRegistry) {
        this.passwordRegistry = passwordRegistry;
    }

    public ChatModel createChatModel(FactCheckerModule.ChatModelConfig config) {
        if (config.getProviderType() == null) {
            throw new IllegalArgumentException("Provider type cannot be null");
        }
        return switch (config.getProviderType()) {
            case OLLAMA -> createOllamaChatModel(config);
            case OPEN_AI -> createOpenAiChatModel(config);
            case ANTHROPIC -> createAnthropicChatModel(config);
            default -> throw new IllegalArgumentException("Provider type not supported: " + config.getProviderType());
        };
    }

    private OllamaChatModel createOllamaChatModel(FactCheckerModule.ChatModelConfig config) {
        return OllamaChatModel.builder()
            .baseUrl(config.getBaseUrl())
            .modelName(config.getModelName())
            .numCtx(config.getNumCtx())
            .numPredict(config.getNumPredict())
            .temperature(config.getTemperature())
            .logRequests(config.isLogRequests())
            .logResponses(config.isLogResponses())
            .supportedCapabilities(config.getSupportedCapabilities())
            .build();
    }

    private OpenAiChatModel createOpenAiChatModel(FactCheckerModule.ChatModelConfig config) {
        return OpenAiChatModel.builder()
            .baseUrl(config.getBaseUrl())
            .apiKey(getApiKeyFromKeyStore(config.getApiKey()))
            .modelName(config.getModelName())
            .logRequests(config.isLogRequests())
            .logResponses(config.isLogResponses())
            .supportedCapabilities(config.getSupportedCapabilities())
            .reasoningEffort("none")
            .build();
    }

    private AnthropicChatModel createAnthropicChatModel(FactCheckerModule.ChatModelConfig config) {
        return AnthropicChatModel.builder()
            .baseUrl(config.getBaseUrl())
            .apiKey(getApiKeyFromKeyStore(config.getApiKey()))
            .modelName(config.getModelName())
            .temperature(config.getTemperature())
            .logRequests(config.isLogRequests())
            .logResponses(config.isLogResponses())
            .supportedCapabilities(config.getSupportedCapabilities())
            .build();
    }

    private String getApiKeyFromKeyStore(String nodePath) {
        Node node = SessionUtil.getNode(PasswordManagerCoreModule.KEYSTORE_WORKSPACE, nodePath);
        if (node != null) {
            try {
                return passwordRegistry.getPassword(node.getIdentifier()).getDecryptedValue();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return null;
    }
}
