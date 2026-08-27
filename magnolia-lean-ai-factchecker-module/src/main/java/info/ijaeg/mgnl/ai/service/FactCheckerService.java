package info.ijaeg.mgnl.ai.service;

import info.ijaeg.mgnl.ai.agent.FactChecker;

import java.util.List;

public interface FactCheckerService {
    List<FactChecker.ClaimCheckResult> check(String text, String language);
}
