package info.ijaeg.mgnl.ai.command;

import info.ijaeg.mgnl.ai.service.FactCheckerService;
import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.jcr.util.NodeTypes;
import info.magnolia.module.site.SiteManager;
import org.junit.Before;
import org.junit.Test;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PageSubnodesPredicateTest {

    private FactCheckerCommand command;

    @Before
    public void setUp() {
        command = new FactCheckerCommand(mock(FactCheckerService.class), mock(SiteManager.class), mock(I18nContentSupport.class));
    }

    /** A mock JCR node that behaves like a mgnl:page node for NodeUtil.getNearestAncestorOfType(). */
    private Node mockPageNode(String path) throws RepositoryException {
        Node node = mock(Node.class);
        when(node.getPath()).thenReturn(path);
        when(node.isNodeType(anyString())).thenReturn(false);
        when(node.isNodeType(NodeTypes.Page.NAME)).thenReturn(true);
        Property primaryType = mock(Property.class);
        when(primaryType.getString()).thenReturn("mgnl:contentNode"); // any non-frozen type works here
        when(node.getProperty(anyString())).thenReturn(primaryType);
        return node;
    }

    @Test
    public void evaluateTyped_subnodeOfSamePage_returnsTrue() throws RepositoryException {
        Node pageNode = mockPageNode("/travel/vietnam");

        Node subnode = mock(Node.class);
        when(subnode.isNodeType(NodeTypes.ContentNode.NAME)).thenReturn(true);
        when(subnode.getDepth()).thenReturn(3);
        when(subnode.getParent()).thenReturn(pageNode);

        FactCheckerCommand.PageSubnodesPredicate predicate = command.new PageSubnodesPredicate(pageNode);

        assertTrue(predicate.evaluateTyped(subnode));
    }

    @Test
    public void evaluateTyped_subnodeOfDifferentPage_returnsFalse() throws RepositoryException {
        Node pageNode = mockPageNode("/travel/vietnam");
        Node otherPageNode = mockPageNode("/travel/thailand");

        Node subnode = mock(Node.class);
        when(subnode.isNodeType(NodeTypes.ContentNode.NAME)).thenReturn(true);
        when(subnode.getDepth()).thenReturn(3);
        when(subnode.getParent()).thenReturn(otherPageNode);

        FactCheckerCommand.PageSubnodesPredicate predicate = command.new PageSubnodesPredicate(pageNode);

        assertFalse(predicate.evaluateTyped(subnode));
    }

    @Test
    public void evaluateTyped_notAContentNode_returnsFalse() throws RepositoryException {
        Node pageNode = mock(Node.class);
        Node subnode = mock(Node.class);
        when(subnode.isNodeType(NodeTypes.ContentNode.NAME)).thenReturn(false);

        FactCheckerCommand.PageSubnodesPredicate predicate = command.new PageSubnodesPredicate(pageNode);

        assertFalse(predicate.evaluateTyped(subnode));
    }

    @Test
    public void evaluateTyped_repositoryExceptionDuringLookup_returnsFalseInsteadOfThrowing() throws RepositoryException {
        Node pageNode = mock(Node.class);
        Node subnode = mock(Node.class);
        when(subnode.isNodeType(anyString())).thenThrow(new RepositoryException("boom"));

        FactCheckerCommand.PageSubnodesPredicate predicate = command.new PageSubnodesPredicate(pageNode);

        assertFalse(predicate.evaluateTyped(subnode));
    }
}
