package info.ijaeg.mgnl.ai;

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
    private FactCheckerConfig factCheckerConfig = new FactCheckerConfig();

    @Data
    public static class FactCheckerConfig {
        private OllamaChatModelConfig claimExtractorChatModelConfg = new OllamaChatModelConfig();
        private OllamaChatModelConfig factCheckerChatModelConfg = new OllamaChatModelConfig();
    }

    @Data
    public static class OllamaChatModelConfig {
        private String baseUrl;
        private String modelName;
        private int numCtx;
        private double temperature;
        private boolean logRequests;
        private boolean logResponses;
    }
}
