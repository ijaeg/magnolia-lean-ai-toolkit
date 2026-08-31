package info.ijaeg.mgnl.ai.regression;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads {@link ExtractorCase} and {@link CheckerCase} regression cases from
 * JSON files on the classpath – exactly one file per test case, all files
 * of one case type together in one directory, e.g.:
 *
 * <pre>
 * src/test/resources/regression-testdata/extractor-cases/thematically-foreign-fact-in-marketing-copy.json
 * src/test/resources/regression-testdata/extractor-cases/self-referential-we-framing.json
 * src/test/resources/regression-testdata/checker-cases/false-title-checked-against-real-occupation.json
 * ...
 * </pre>
 *
 * Usage:
 * <pre>
 * List&lt;ExtractorCase&gt; cases = RegressionCaseLoader.loadExtractorCases(
 *         "/.../regression/extractor-cases");
 * List&lt;CheckerCase&gt; checkerCases = RegressionCaseLoader.loadCheckerCases(
 *         "/.../regression/checker-cases");
 * </pre>
 *
 * <p><b>Important for Records + Jackson:</b> compile with the Maven
 * compiler plugin's {@code <parameters>true</parameters>}, so Jackson can
 * automatically map the record component names (name, text, languageCode, ...)
 * to the JSON fields.</p>
 *
 * <p><b>Classpath assumption:</b> the directory listing via
 * {@code Files.list} assumes the test resources exist as plain files (the
 * standard case for {@code mvn test}/{@code mvn verify}, since
 * Surefire/Failsafe run directly against {@code target/test-classes}, not
 * against a packaged JAR). If the regression tests should ever run from a
 * JAR, this would instead need a manifest file listing the filenames
 * instead of the directory listing.</p>
 */
final class RegressionCaseLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            // Typos/stale fields in the JSON should fail loudly on load,
            // not silently default in the test assertion.
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);

    private RegressionCaseLoader() {
    }

    static List<ExtractorCase> loadExtractorCases(String classpathDirectory) {
        return loadAllJsonFilesInDirectory(classpathDirectory, ExtractorCase.class);
    }

    static List<CheckerCase> loadCheckerCases(String classpathDirectory) {
        return loadAllJsonFilesInDirectory(classpathDirectory, CheckerCase.class);
    }

    private static <T> List<T> loadAllJsonFilesInDirectory(String classpathDirectory, Class<T> type) {
        URL dirUrl = RegressionCaseLoader.class.getResource(classpathDirectory);
        if (dirUrl == null) {
            throw new IllegalArgumentException(
                    "Regression test data directory not found: " + classpathDirectory);
        }

        Path dirPath;
        try {
            dirPath = Paths.get(dirUrl.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    "Invalid classpath URI for directory " + classpathDirectory, e);
        }

        List<Path> jsonFiles;
        try (Stream<Path> files = Files.list(dirPath)) {
            jsonFiles = files
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted() // deterministic order independent of the filesystem
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Error listing the regression test data in " + classpathDirectory, e);
        }

        if (jsonFiles.isEmpty()) {
            throw new IllegalStateException(
                    "No JSON files found in " + classpathDirectory);
        }

        List<T> cases = new ArrayList<>(jsonFiles.size());
        for (Path file : jsonFiles) {
            cases.add(readOne(file, type));
        }
        return List.copyOf(cases);
    }

    private static <T> T readOne(Path file, Class<T> type) {
        try {
            return MAPPER.readValue(file.toFile(), type);
        } catch (IOException e) {
            throw new UncheckedIOException("Error parsing " + file, e);
        }
    }
}