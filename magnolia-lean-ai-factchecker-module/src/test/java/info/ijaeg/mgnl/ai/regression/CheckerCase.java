package info.ijaeg.mgnl.ai.regression;

import info.ijaeg.mgnl.ai.agent.FactChecker;
import info.ijaeg.mgnl.ai.tool.WikipediaTool;

import java.util.Map;

/**
 * Ein Regressionsfall für {@code FactChecker}. Eigenständige Top-Level-
 * Klasse aus demselben Grund wie {@link ExtractorCase}: nur
 * {@link FactCheckerRegressionIT} und {@link RegressionCaseLoader} greifen
 * darauf zu, {@link AbstractRegressionIT} bleibt davon unabhängig.
 */
public record CheckerCase(String name, String claim, String languageCode,
                   Map<String, WikipediaTool.WikipediaResult> wikipediaFixtures,
                   FactChecker.FactCheckResult.Verdict expectedVerdict) {
}