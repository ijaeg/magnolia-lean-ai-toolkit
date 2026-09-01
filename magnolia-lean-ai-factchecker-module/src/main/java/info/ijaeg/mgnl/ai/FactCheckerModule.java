package info.ijaeg.mgnl.ai;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import info.magnolia.module.ModuleLifecycle;
import lombok.Data;

/**
 * This class is optional and represents the configuration for the lean-ai-assistant-module module.
 * By exposing simple getter/setter/adder methods, this bean can be configured via content2bean
 * using the properties and node from <tt>config:/modules/lean-ai-assistant-module</tt>.
 * If you don't need this, simply remove the reference to this class in the module descriptor xml.
 * See https://documentation.magnolia-cms.com/display/DOCS/Module+configuration for information about module configuration.
 */
@Data
public class FactCheckerModule implements ModuleLifecycle {
    /* you can optionally implement info.magnolia.module.ModuleLifecycle */


    private ChatModelConfig claimExtractorChatModelConfg = new ChatModelConfig();
    private ChatModelConfig factCheckerChatModelConfg = new ChatModelConfig();

    @Data
    public static class ChatModelConfig {
        private ModelProvider providerType;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private int numCtx;
        private int numPredict;
        private double temperature;
        private boolean logRequests;
        private boolean logResponses;
        private Capability[] supportedCapabilities = new Capability[]{};
        private long timeoutInSeconds;
    }
}
