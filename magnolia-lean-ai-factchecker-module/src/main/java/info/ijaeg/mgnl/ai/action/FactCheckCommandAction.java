package info.ijaeg.mgnl.ai.action;

import info.ijaeg.mgnl.ai.service.FactCheckerServiceImpl;
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

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.text.MessageFormat;
import java.util.List;

@Slf4j
public class FactCheckCommandAction extends JcrCommandAction<Node, FactCheckCommandActionDefinition> {
    private final Context context;

    @Inject
    public FactCheckCommandAction(FactCheckCommandActionDefinition definition, CommandsManager commandsManager, ValueContext<Node> valueContext, Context context, AsyncActionExecutor asyncActionExecutor, JcrDatasource jcrDatasource, DatasourceObservation.Manual datasourceObservation) {
        super(definition, commandsManager, valueContext, context, asyncActionExecutor, jcrDatasource, datasourceObservation);
        this.context = context;
    }

    @Override
    protected boolean executeCommand(Node node) throws Exception {
        boolean returnVAlue = super.executeCommand(node);
        List<FactCheckerServiceImpl.FactCheckResult> results = MgnlContext.getAttribute("FACTCHECK_RESULTS");
        MessagesManager messagesManager = Components.getComponent(MessagesManager.class);
        Message msg = new Message();
        msg.setSubject("Fact Checker");
        msg.setMessage(getMessage(node));
        msg.addProperty("description", getDescription(results));
        msg.setType(getMessageType(results));
        msg.setView("magnolia-lean-ai-factchecker-module:factCheck");
        messagesManager.sendLocalMessage(msg);
        return returnVAlue;
    }

    private String getMessage(Node node) throws RepositoryException{
        return MessageFormat.format("{0}:/{1} has been fact checked.", node.getSession().getWorkspace().getName(), node.getPath());
    }

    private String getDescription(List<FactCheckerServiceImpl.FactCheckResult> results) {
        if (CollectionUtils.isEmpty(results)) {
            return "No specific claims could be identified for this content.";
        }
        StringBuilder sb = new StringBuilder();
        results.forEach(result -> {
            sb.append("<p>");
            sb.append("<b>Claim: </b>" + result.claim() + "<br/>");
            sb.append("<b>Verdict: </b>" + result.verdict() + "<br/>");
            sb.append("<b>Explanation: </b>" + result.explanation() + "<br/>");
            sb.append("<b>Source Url: </b><a href=\"" + result.sourceUrl() + "\">" + result.sourceUrl() + "</a><br/>");
            sb.append("</p>");
        });
        return sb.toString();
    }

    private MessageType getMessageType(List<FactCheckerServiceImpl.FactCheckResult> results) {
        if (results.stream().anyMatch(result -> result.verdict() == FactCheckerServiceImpl.FactCheckResult.Verdict.INCORRECT)) {
            return MessageType.WARNING;
        } else {
            return MessageType.INFO;
        }
    }
}