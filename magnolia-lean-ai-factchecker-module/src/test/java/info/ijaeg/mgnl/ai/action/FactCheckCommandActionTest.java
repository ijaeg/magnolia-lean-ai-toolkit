package info.ijaeg.mgnl.ai.action;

import info.ijaeg.mgnl.ai.agent.FactChecker;
import info.magnolia.ui.api.message.MessageType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FactCheckCommandActionTest {

    @Test
    public void getMessageType_noResults_returnsInfo() {
        assertEquals(MessageType.INFO, FactCheckCommandAction.getMessageType(List.of()));
    }

    @Test
    public void getMessageType_allCorrectOrUnverifiable_returnsInfo() {
        List<FactChecker.ClaimCheckResult> results = List.of(
                new FactChecker.ClaimCheckResult("claim1", FactChecker.FactCheckResult.Verdict.CORRECT, "expl", "url"),
                new FactChecker.ClaimCheckResult("claim2", FactChecker.FactCheckResult.Verdict.UNVERIFIABLE, "expl", "")
        );

        assertEquals(MessageType.INFO, FactCheckCommandAction.getMessageType(results));
    }

    @Test
    public void getMessageType_containsIncorrect_returnsWarning() {
        List<FactChecker.ClaimCheckResult> results = List.of(
                new FactChecker.ClaimCheckResult("claim1", FactChecker.FactCheckResult.Verdict.CORRECT, "expl", "url"),
                new FactChecker.ClaimCheckResult("claim2", FactChecker.FactCheckResult.Verdict.INCORRECT, "expl", "url")
        );

        assertEquals(MessageType.WARNING, FactCheckCommandAction.getMessageType(results));
    }

    @Test
    public void getSourceUrlLink_emptyUrl_returnsEmptyString() {
        assertEquals("", FactCheckCommandAction.getSourceUrlLink(""));
    }

    @Test
    public void getSourceUrlLink_nonEmptyUrl_returnsAnchorTag() {
        String url = "https://en.wikipedia.org/wiki/Ho_Chi_Minh_City";

        assertEquals("<a href=\"" + url + "\">" + url + "</a>", FactCheckCommandAction.getSourceUrlLink(url));
    }

    @Test
    public void getDescription_emptyResults_returnsNoClaimsMessage() {
        FactCheckCommandActionDefinition definition = new FactCheckCommandActionDefinition();

        assertEquals(definition.getMessageDescriptionNoClaims(), FactCheckCommandAction.getDescription(definition, List.of()));
    }

    @Test
    public void getDescription_withResults_formatsEachClaim() {
        FactCheckCommandActionDefinition definition = new FactCheckCommandActionDefinition();
        List<FactChecker.ClaimCheckResult> results = List.of(
                new FactChecker.ClaimCheckResult(
                        "The Eiffel Tower was built in 1889.",
                        FactChecker.FactCheckResult.Verdict.CORRECT,
                        "Confirmed by Wikipedia.",
                        "https://en.wikipedia.org/wiki/Eiffel_Tower")
        );

        String description = FactCheckCommandAction.getDescription(definition, results);

        assertTrue(description.contains("The Eiffel Tower was built in 1889."));
        assertTrue(description.contains("CORRECT"));
        assertTrue(description.contains("Confirmed by Wikipedia."));
        assertTrue(description.contains("<a href=\"https://en.wikipedia.org/wiki/Eiffel_Tower\">"));
    }
}
