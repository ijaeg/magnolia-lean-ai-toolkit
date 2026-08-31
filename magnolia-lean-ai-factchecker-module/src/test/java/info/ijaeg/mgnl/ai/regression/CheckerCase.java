package info.ijaeg.mgnl.ai.regression;

import info.ijaeg.mgnl.ai.agent.FactChecker;
import info.ijaeg.mgnl.ai.tool.WikipediaTool;

import java.util.Map;

/**
 * A regression case for {@code FactChecker}. Standalone top-level
 * class for the same reason as {@link ExtractorCase}: only
 * {@link FactCheckerRegressionIT} and {@link RegressionCaseLoader} access it,
 * {@link AbstractRegressionIT} stays independent of it.
 */
public record CheckerCase(String name, String claim, String languageCode,
                   Map<String, WikipediaTool.WikipediaResult> wikipediaFixtures,
                   FactChecker.FactCheckResult.Verdict expectedVerdict) {
}