package info.ijaeg.mgnl.ai.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.output.OutputParsingException;
import info.ijaeg.mgnl.ai.FactCheckerModule;
import info.ijaeg.mgnl.ai.agent.ClaimExtractor;
import info.ijaeg.mgnl.ai.agent.FactChecker;
import info.ijaeg.mgnl.ai.factory.ChatModelFactory;
import info.ijaeg.mgnl.ai.tool.WikipediaTool;
import info.magnolia.module.ModuleRegistry;
import info.magnolia.rest.client.factory.RestClientFactory;
import info.magnolia.rest.client.registry.RestClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link FactCheckerService} implementation. Wires the two LangChain4j
 * agents ({@link ClaimExtractor}, {@link FactChecker}) with independently
 * configured chat models (see {@code config.md} for the two {@code
 * ChatModelConfig} properties on {@link FactCheckerModule}) and the
 * {@link WikipediaTool}.
 */
@Singleton
@Slf4j
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

    /**
     * {@inheritDoc}
     *
     * <p>Each claim is checked independently. If the fact-checking model
     * returns a response {@code FactChecker} cannot parse ({@link
     * OutputParsingException} — typically an incomplete/truncated JSON
     * response, a known local-model failure mode, see {@code CLAUDE.md}),
     * the check is retried once for that claim. If the retry also fails,
     * that single claim is dropped from the result instead of failing the
     * whole request — the already-checked claims are still returned.</p>
     */
    @Override
    public List<FactChecker.ClaimCheckResult> check(String text, String language) {
        List<FactChecker.ClaimCheckResult> claimCheckResultList = new ArrayList<>();
        FactChecker factChecker = AiServices.builder(FactChecker.class)
                .chatModel(chatModelFactory.createChatModel(getModule().getFactCheckerChatModelConfg()))
                .tools(new WikipediaTool(restClientRegistry, restClientFactory))
                .build();
        List<String> claims = extractClaims(text, language);
        claims.forEach(claim -> {
            FactChecker.FactCheckResult result = null;
            try {
                result = factChecker.check(claim, language);
            } catch (OutputParsingException e) {
                try {
                    result = factChecker.check(claim, language);
                } catch (OutputParsingException e1) {
                    log.warn(e1.getMessage());
                }
            }
            if (result != null) {
                claimCheckResultList.add(new FactChecker.ClaimCheckResult(claim, result.verdict(), result.explanation(), result.sourceUrl()));
            }
        });
        return claimCheckResultList;
    }

    private List<String> extractClaims(String text, String language) {
        ClaimExtractor extractor = AiServices.builder(ClaimExtractor.class)
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
