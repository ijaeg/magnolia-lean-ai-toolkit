package info.ijaeg.mgnl.ai.regression;

import java.util.List;

/**
 * A regression case for {@code ClaimExtractor}. Standalone top-level
 * class instead of nested in {@link AbstractRegressionIT}, because only
 * {@link ClaimExtractorRegressionIT} and {@link RegressionCaseLoader} need
 * to know it – the generic base class needs no reference to it.
 */
public record ExtractorCase(String name, String text, String languageCode,
                     List<String> mustContainSubstrings,
                     List<String> mustNotContainSubstrings) {
}