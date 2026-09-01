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
import java.time.Duration;

/**
 * Builds a LangChain4j {@link ChatModel} from a {@link
 * FactCheckerModule.ChatModelConfig}, dispatching on {@code providerType}
 * to the matching provider-specific builder (Ollama, OpenAI, or Anthropic).
 * Used identically by both production code ({@code FactCheckerServiceImpl})
 * and the {@code *RegressionIT} prompt regression tests, so a change here
 * affects both the same way — see {@code CLAUDE.md}.
 */
@Singleton
@Slf4j
public class ChatModelFactory {
    private final PasswordRegistry passwordRegistry;

    @Inject
    public ChatModelFactory(PasswordRegistry passwordRegistry) {
        this.passwordRegistry = passwordRegistry;
    }

    /**
     * @param config provider type plus model parameters; see {@code
     *               config.md} for the full property reference
     * @return a configured, ready-to-use chat model
     * @throws IllegalArgumentException if {@code providerType} is {@code
     *                                  null} or not one of the supported providers
     */
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
                .timeout(Duration.ofSeconds(config.getTimeoutInSeconds()))
                .build();
    }

    private OpenAiChatModel createOpenAiChatModel(FactCheckerModule.ChatModelConfig config) {
        return OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(getApiKeyFromKeyStore(config.getApiKey()))
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .logRequests(config.isLogRequests())
                .logResponses(config.isLogResponses())
                .supportedCapabilities(config.getSupportedCapabilities())
                .reasoningEffort("none")
                .timeout(Duration.ofSeconds(config.getTimeoutInSeconds()))
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

    /**
     * Resolves an API key stored in Magnolia's Passwords App keystore.
     * {@code nodePath} is a JCR node path into the keystore workspace, not a
     * plaintext secret itself — nothing resembling a real API key should
     * ever appear directly in config YAML (see {@code CLAUDE.md}, "Security
     * note").
     *
     * @return the decrypted key, or {@code null} if the node doesn't exist
     * or decryption failed (logged, not thrown, so a misconfigured
     * key surfaces as an authentication failure from the provider
     * rather than an opaque internal error here)
     */
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
