package info.ijaeg.mgnl.ai.action;

import info.ijaeg.mgnl.ai.agent.FactChecker;
import info.magnolia.commands.CommandsManager;
import info.magnolia.context.Context;
import info.magnolia.context.MgnlContext;
import info.magnolia.objectfactory.Components;
import info.magnolia.ui.ValueContext;
import info.magnolia.ui.api.message.Message;
import info.magnolia.ui.api.message.MessageType;
import info.magnolia.ui.contentapp.action.JcrCommandAction;
import info.magnolia.ui.contentapp.async.AsyncActionExecutor;
import info.magnolia.ui.datasource.jcr.JcrDatasource;
import info.magnolia.ui.framework.message.MessagesManager;
import info.magnolia.ui.observation.DatasourceObservation;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.text.MessageFormat;
import java.util.List;

import static info.ijaeg.mgnl.ai.command.FactCheckerCommand.FACTCHECK_RESULTS_ATTR;

@Slf4j
public class FactCheckCommandAction extends JcrCommandAction<Node, FactCheckCommandActionDefinition> {
    public static final String MSG_PROP_DESCRIPTION = "description";

    @Inject
    public FactCheckCommandAction(FactCheckCommandActionDefinition definition, CommandsManager commandsManager, ValueContext<Node> valueContext, Context context, AsyncActionExecutor asyncActionExecutor, JcrDatasource jcrDatasource, DatasourceObservation.Manual datasourceObservation) {
        super(definition, commandsManager, valueContext, context, asyncActionExecutor, jcrDatasource, datasourceObservation);
    }

    @Override
    protected boolean executeCommand(Node node) throws Exception {
        boolean returnValue = super.executeCommand(node);
        List<FactChecker.ClaimCheckResult> results = MgnlContext.getAttribute(FACTCHECK_RESULTS_ATTR);
        MessagesManager messagesManager = Components.getComponent(MessagesManager.class);
        Message msg = new Message();
        msg.setSubject(getDefinition().getMessageSubject());
        msg.setMessage(getMessage(node));
        msg.addProperty(MSG_PROP_DESCRIPTION, getDescription(getDefinition(), results));
        msg.setType(getMessageType(results));
        msg.setView(getDefinition().getMessageView());
        messagesManager.sendLocalMessage(msg);
        return returnValue;
    }

    private String getMessage(Node node) throws RepositoryException{
        return MessageFormat.format(getDefinition().getMessagePattern(), node.getSession().getWorkspace().getName(), node.getPath());
    }

    static String getDescription(FactCheckCommandActionDefinition definition, List<FactChecker.ClaimCheckResult> results) {
        if (CollectionUtils.isEmpty(results)) {
            return definition.getMessageDescriptionNoClaims();
        }
        StringBuilder sb = new StringBuilder();
        results.forEach(result -> {
           sb.append(MessageFormat.format(definition.getMessageDescriptionPattern(), result.claim(), result.verdict(), result.explanation(), getSourceUrlLink(result.sourceUrl())));
        });
        return sb.toString();
    }

    static MessageType getMessageType(List<FactChecker.ClaimCheckResult> results) {
        if (results.stream().anyMatch(result -> result.verdict() == FactChecker.FactCheckResult.Verdict.INCORRECT)) {
            return MessageType.WARNING;
        } else {
            return MessageType.INFO;
        }
    }

    static String getSourceUrlLink(String soureUrl) {
        return StringUtils.isEmpty(soureUrl) ? StringUtils.EMPTY : MessageFormat.format("<a href=\"{0}\">{0}</a>", soureUrl);
    }
}