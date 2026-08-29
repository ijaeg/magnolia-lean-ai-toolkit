package info.ijaeg.mgnl.ai.service;

import info.ijaeg.mgnl.ai.agent.ClaimExtractor;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FactCheckerServiceImplTest {

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
}
