package info.ijaeg.mgnl.ai.command;

import com.google.inject.Inject;
import info.ijaeg.mgnl.ai.agent.FactChecker;
import info.ijaeg.mgnl.ai.service.FactCheckerService;
import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.commands.impl.BaseRepositoryCommand;
import info.magnolia.context.Context;
import info.magnolia.context.MgnlContext;
import info.magnolia.jcr.predicate.AbstractPredicate;
import info.magnolia.jcr.util.NodeTypes;
import info.magnolia.jcr.util.NodeUtil;
import info.magnolia.jcr.util.PropertyUtil;
import info.magnolia.module.site.SiteManager;
import io.micrometer.common.util.StringUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j
public class FactCheckerCommand extends BaseRepositoryCommand {
    public static final String COMMAND_NAME = "factChecker";
    public static final String FACTCHECK_RESULTS_ATTR = "FACTCHECK_RESULTS";

    private final FactCheckerService factCheckerService;
    private final SiteManager siteManager;
    private final I18nContentSupport i18nContentSupport;

    @Getter
    @Setter
    private List<String> nodePropertyNames = List.of();

    @Getter
    @Setter
    private boolean includeSubnodes = false;

    @Getter
    @Setter
    private boolean checkAllLanguages = false;

    @Inject
    public FactCheckerCommand(FactCheckerService factCheckerService, SiteManager siteManager, I18nContentSupport i18nContentSupport) {
        this.factCheckerService = factCheckerService;
        this.siteManager = siteManager;
        this.i18nContentSupport = i18nContentSupport;
    }

    @Override
    public boolean execute(Context context) throws Exception {
        Node node = getJCRNode(context);
        List<FactChecker.ClaimCheckResult> results = new ArrayList<>();
        I18nContentSupport i18n = getI18n(node);
        doExecute(node, results, i18n);
        if (includeSubnodes) {
            Iterator<Node> subnodes = NodeUtil.collectAllChildren(node, new PageSubnodesPredicate(node)).iterator();
            while (subnodes.hasNext()) {
                Node subnode = subnodes.next();
                doExecute(subnode, results, i18n);
            }
        }
        MgnlContext.setAttribute(FACTCHECK_RESULTS_ATTR, results, Context.LOCAL_SCOPE);
        return true;
    }

    private void doExecute(Node node, List<FactChecker.ClaimCheckResult> results, I18nContentSupport i18n) {
        nodePropertyNames.forEach(nodePropertyName -> {
            i18n.getLocales().forEach(locale -> {
                if (checkAllLanguages || locale.equals(i18n.getDefaultLocale())) {
                    String propertyValue = PropertyUtil.getString(node, nodePropertyName);
                    if (locale != i18n.getLocale()) {
                        try {
                            Property property = i18n.getProperty(node, nodePropertyName, locale);
                            if (property != null) {
                                propertyValue = property.getString();
                            }
                        } catch (RepositoryException e) {
                            log.warn("Failed to read '{}' for locale {} on {}", nodePropertyName, locale, node, e);
                        }
                    }
                    if (StringUtils.isNotEmpty(propertyValue)) {
                        results.addAll(factCheckerService.check(propertyValue, locale.getLanguage()));
                    }
                }
            });
        });
    }

    private I18nContentSupport getI18n(Node node) {
        I18nContentSupport i18n = siteManager.getAssignedSite(node).getI18n();
        return i18n != null ? i18n : i18nContentSupport;
    }

    public class PageSubnodesPredicate extends AbstractPredicate<Node> {
        private final Node pageNode;

        public PageSubnodesPredicate(Node pageNode) {
            this.pageNode = pageNode;
        }

        @Override
        public boolean evaluateTyped(Node node) {
            try {
                return node.isNodeType(NodeTypes.ContentNode.NAME)
                        && NodeUtil.getNearestAncestorOfType(node, NodeTypes.Page.NAME).getPath().equals(pageNode.getPath());
            } catch (RepositoryException e) {
                log.error(e.getMessage(), e);
                return false;
            }
        }
    }
}
