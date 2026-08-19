package info.ijaeg.mgnl.ai.command;

import com.google.inject.Inject;
import info.ijaeg.mgnl.ai.service.FactCheckerService;
import info.ijaeg.mgnl.ai.service.FactCheckerServiceImpl;
import info.magnolia.context.MgnlContext;
import info.magnolia.commands.impl.BaseRepositoryCommand;
import info.magnolia.context.Context;
import info.magnolia.jcr.util.PropertyUtil;
import lombok.extern.slf4j.Slf4j;

import javax.jcr.Node;
import java.util.List;

@Slf4j
public class FactCheckerCommand extends BaseRepositoryCommand {
    private final FactCheckerService factCheckerService;

    @Inject
    public FactCheckerCommand(FactCheckerService factCheckerService) {
        this.factCheckerService = factCheckerService;
    }

    @Override
    public boolean execute(Context context) throws Exception {
        Node node = getJCRNode(context);
        List<FactCheckerServiceImpl.FactCheckResult> results = factCheckerService.check(PropertyUtil.getString(node, "body"));
        MgnlContext.setAttribute("FACTCHECK_RESULTS", results, Context.LOCAL_SCOPE);
        log.info("FERTIG!");
        return true;
    }
}
