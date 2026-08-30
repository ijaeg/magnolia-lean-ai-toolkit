package info.ijaeg.mgnl.ai.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.langchain4j.service.output.OutputParsingException;
import info.ijaeg.mgnl.ai.agent.ClaimExtractor;
import info.ijaeg.mgnl.ai.agent.FactChecker;
import info.ijaeg.mgnl.ai.factory.ChatModelFactory;
import info.ijaeg.mgnl.ai.tool.WikipediaTool;
import dev.langchain4j.service.AiServices;
import info.ijaeg.mgnl.ai.FactCheckerModule;
import info.magnolia.module.ModuleRegistry;
import info.magnolia.rest.client.factory.RestClientFactory;
import info.magnolia.rest.client.registry.RestClientRegistry;
import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class FactCheckerServiceImpl implements FactCheckerService {
    private final ModuleRegistry moduleRegistry;
    private final RestClientRegistry restClientRegistry;
    private final RestClientFactory restClientFactory;
    private final ChatModelFactory chatModelFactory;

    @Inject
    public FactCheckerServiceImpl(ModuleRegistry moduleRegistry, RestClientRegistry restClientRegistry, RestClientFactory restClientFactory, ChatModelFactory chatModelFactory) {
        this.moduleRegistry = moduleRegistry;
        this.restClientRegistry = restClientRegistry;
        this.restClientFactory = restClientFactory;
        this.chatModelFactory = chatModelFactory;
    }

    @Override
    public List<FactChecker.ClaimCheckResult> check(String text, String language) {
        List<FactChecker.ClaimCheckResult> claimCheckResultList = new ArrayList<>();
        FactChecker factChecker = AiServices.builder(FactChecker.class)
                .systemMessage(getModule().getFactCheckerSystemMessage())
                .chatModel(chatModelFactory.createChatModel(getModule().getFactCheckerChatModelConfg()))
                .tools(new WikipediaTool(restClientRegistry, restClientFactory))
                .build();
        List<String> claims = extractClaims(text, language);
        claims.forEach(claim -> {
            FactChecker.FactCheckResult result;
            try {
                result = factChecker.check(claim, language);
            } catch (OutputParsingException e) {
                result = factChecker.check(claim, language);
            }
            claimCheckResultList.add(new FactChecker.ClaimCheckResult(claim, result.verdict(), result.explanation(), result.sourceUrl()));
        });
        return claimCheckResultList;
    }

    private List<String> extractClaims(String text, String language) {
        ClaimExtractor extractor = AiServices.builder(ClaimExtractor.class)
                .systemMessage(getModule().getClaimExtractorSystemMessage())
                .chatModel(chatModelFactory.createChatModel(getModule().getClaimExtractorChatModelConfg()))
                .build();
        String parsedText = Jsoup.parse(text).text();
        return toClaims(extractor.extract(parsedText, language));
    }

    static List<String> toClaims(ClaimExtractor.ClaimList claimList) {
        return (claimList != null && claimList.claims() != null) ? claimList.claims() : List.of();
    }

    private FactCheckerModule getModule() {
        return moduleRegistry.getModuleInstance(FactCheckerModule.class);
    }
}
