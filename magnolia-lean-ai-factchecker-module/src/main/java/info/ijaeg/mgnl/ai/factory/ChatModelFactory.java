package info.ijaeg.mgnl.ai.factory;

import com.google.inject.Singleton;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import info.ijaeg.mgnl.ai.FactCheckerModule;

@Singleton
public class ChatModelFactory {
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
            .apiKey(config.getApiKey())
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
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(config.getTemperature())
            .logRequests(config.isLogRequests())
            .logResponses(config.isLogResponses())
            .supportedCapabilities(config.getSupportedCapabilities())
            .build();
    }
}
