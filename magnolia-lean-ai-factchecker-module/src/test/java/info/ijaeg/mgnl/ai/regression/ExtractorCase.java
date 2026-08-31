package info.ijaeg.mgnl.ai.regression;

import java.util.List;

/**
 * Ein Regressionsfall für {@code ClaimExtractor}. Eigenständige Top-Level-
 * Klasse statt in {@link AbstractRegressionIT} verschachtelt, weil nur
 * {@link ClaimExtractorRegressionIT} und {@link RegressionCaseLoader} sie
 * kennen müssen – die generische Basisklasse braucht keinen Bezug dazu.
 */
public record ExtractorCase(String name, String text, String languageCode,
                     List<String> mustContainSubstrings,
                     List<String> mustNotContainSubstrings) {
}