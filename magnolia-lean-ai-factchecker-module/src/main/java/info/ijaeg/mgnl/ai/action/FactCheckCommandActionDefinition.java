package info.ijaeg.mgnl.ai.action;

import info.magnolia.ui.api.action.ActionType;
import info.magnolia.ui.contentapp.action.JcrCommandActionDefinition;
import lombok.Getter;
import lombok.Setter;

import static info.ijaeg.mgnl.ai.command.FactCheckerCommand.COMMAND_NAME;

/**
 * Definition for the {@code factCheckCommandAction} ({@code $type:
 * factCheckCommandAction}) — the "Fact Check" action wired into an app's
 * actionbar. For a description of each property below and how they're used,
 * see {@code config.md} (kept there, not duplicated here, to avoid the two
 * drifting apart).
 */
@ActionType("factCheckCommandAction")
@Getter
@Setter
public class FactCheckCommandActionDefinition extends JcrCommandActionDefinition {
    private String messageView;
    private String messageSubject;
    private String messagePattern;
    private String messageDescriptionNoClaims;
    private String messageDescriptionPattern;

    public FactCheckCommandActionDefinition() {
        setImplementationClass(FactCheckCommandAction.class);
        setIcon(" icon-preview-app");
        setCommand(COMMAND_NAME);
        setAsynchronous(true);
        setAlwaysShowSuccessMessage(false);
        setNotifyUser(false);
        setMessageView("magnolia-lean-ai-factchecker-module:factCheck");
        setMessageSubject("Fact Checker");
        setMessagePattern("{0}:/{1} has been fact checked.");
        setMessageDescriptionNoClaims("No specific claims could be identified for this content.");
        setMessageDescriptionPattern("""
                <p>
                    <b>Claim: </b>{0}<br/>
                    <b>Verdict: </b>{1}<br/>
                    <b>Explanation: </b>{2}<br/>
                    <b>Source Url: </b>{3}<br/>
                </p>
                """);
    }
}
