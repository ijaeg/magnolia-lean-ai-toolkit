package info.ijaeg.mgnl.ai.action;

import info.magnolia.ui.api.action.ActionType;
import info.magnolia.ui.contentapp.action.JcrCommandActionDefinition;

@ActionType("factCheckCommandAction")
public class FactCheckCommandActionDefinition extends JcrCommandActionDefinition {
    public  FactCheckCommandActionDefinition() {
        setImplementationClass(FactCheckCommandAction.class);
    }
}
