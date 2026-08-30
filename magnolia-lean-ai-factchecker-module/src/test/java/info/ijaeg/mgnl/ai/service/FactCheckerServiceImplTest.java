package info.ijaeg.mgnl.ai.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import info.ijaeg.mgnl.ai.FactCheckerModule;
import info.ijaeg.mgnl.ai.agent.ClaimExtractor;
import info.ijaeg.mgnl.ai.agent.FactChecker;
import info.ijaeg.mgnl.ai.factory.ChatModelFactory;
import info.magnolia.module.ModuleRegistry;
import info.magnolia.rest.client.factory.RestClientFactory;
import info.magnolia.rest.client.registry.RestClientRegistry;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FactCheckerServiceImplTest {

    private final ModuleRegistry moduleRegistry = mock(ModuleRegistry.class);
    private final RestClientRegistry restClientRegistry = mock(RestClientRegistry.class);
    private final RestClientFactory restClientFactory = mock(RestClientFactory.class);
    private final ChatModelFactory chatModelFactory = mock(ChatModelFactory.class);
    private final FactCheckerModule module = new FactCheckerModule();
    private final FactCheckerServiceImpl service =
            new FactCheckerServiceImpl(moduleRegistry, restClientRegistry, restClientFactory, chatModelFactory);

    @Before
    public void setUp() {
        when(moduleRegistry.getModuleInstance(FactCheckerModule.class)).thenReturn(module);
    }

    @Test
    public void toClaims_nullClaimList_returnsEmptyList() {
        assertTrue(FactCheckerServiceImpl.toClaims(null).isEmpty());
    }

    @Test
    public void toClaims_claimsFieldNull_returnsEmptyList() {
        // reproduces the observed Ollama/qwen2.5 behavior of returning {} instead of {"claims": []}
        ClaimExtractor.ClaimList claimList = new ClaimExtractor.ClaimList(null, "en");

        assertTrue(FactCheckerServiceImpl.toClaims(claimList).isEmpty());
    }

    @Test
    public void toClaims_normalList_returnsItUnchanged() {
        List<String> claims = List.of("The Eiffel Tower was built in 1889.");
        ClaimExtractor.ClaimList claimList = new ClaimExtractor.ClaimList(claims, "en");

        assertEquals(claims, FactCheckerServiceImpl.toClaims(claimList));
    }

    // a stub ChatModel that always answers with the given raw text; overriding only doChat()
    // lets ChatModel's own default chat(ChatRequest) plumbing (parameter merging, listener
    // notification) run unmodified, exactly as a real provider-backed implementation would.
    private static class StubChatModel implements ChatModel {
        private final String responseText;
        private final AtomicInteger callCount = new AtomicInteger();

        StubChatModel(String responseText) {
            this.responseText = responseText;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            callCount.incrementAndGet();
            return ChatResponse.builder().aiMessage(AiMessage.from(responseText)).build();
        }
    }

    private void stubChatModels(ChatModel extractorModel, ChatModel factCheckerModel) {
        when(chatModelFactory.createChatModel(any())).thenAnswer(invocation -> {
            FactCheckerModule.ChatModelConfig config = invocation.getArgument(0);
            return config == module.getClaimExtractorChatModelConfg() ? extractorModel : factCheckerModel;
        });
    }

    @Test
    public void check_factCheckerReturnsUnparseableResponseTwice_dropsClaimInsteadOfPropagating() {
        StubChatModel extractorModel = new StubChatModel(
                "{\"claims\": [\"Ho Chi Minh City is the capital of Vietnam.\"], \"language\": \"en\"}");
        StubChatModel factCheckerModel = new StubChatModel("I refuse to answer in JSON.");
        stubChatModels(extractorModel, factCheckerModel);

        List<FactChecker.ClaimCheckResult> results =
                service.check("Ho Chi Minh City is the capital of Vietnam.", "en");

        // both the initial attempt and the single retry threw OutputParsingException, so the
        // claim is silently dropped rather than the exception propagating out of check()
        assertTrue(results.isEmpty());
        assertEquals(2, factCheckerModel.callCount.get());
    }

    @Test
    public void check_factCheckerReturnsUnparseableResponseThenValidOnRetry_usesRetryResult() {
        StubChatModel extractorModel = new StubChatModel(
                "{\"claims\": [\"Ho Chi Minh City is the capital of Vietnam.\"], \"language\": \"en\"}");
        ChatModel factCheckerModel = new ChatModel() {
            private final AtomicInteger callCount = new AtomicInteger();

            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                String text = callCount.getAndIncrement() == 0
                        ? "I refuse to answer in JSON."
                        : "{\"explanation\": \"Hanoi, not Ho Chi Minh City, is Vietnam's capital.\", "
                          + "\"verdict\": \"INCORRECT\", \"sourceUrl\": \"https://en.wikipedia.org/wiki/Hanoi\"}";
                return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
            }
        };
        stubChatModels(extractorModel, factCheckerModel);

        List<FactChecker.ClaimCheckResult> results =
                service.check("Ho Chi Minh City is the capital of Vietnam.", "en");

        assertEquals(1, results.size());
        assertEquals(FactChecker.FactCheckResult.Verdict.INCORRECT, results.get(0).verdict());
        assertEquals("https://en.wikipedia.org/wiki/Hanoi", results.get(0).sourceUrl());
    }
}
