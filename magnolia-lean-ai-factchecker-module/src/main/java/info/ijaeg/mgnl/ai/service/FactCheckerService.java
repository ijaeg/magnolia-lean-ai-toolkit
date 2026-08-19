package info.ijaeg.mgnl.ai.service;

import java.util.List;

public interface FactCheckerService {
    List<FactCheckerServiceImpl.FactCheckResult> check(String text);
}
