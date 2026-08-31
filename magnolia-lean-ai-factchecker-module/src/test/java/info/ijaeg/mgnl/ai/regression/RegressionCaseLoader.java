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
 * Lädt {@link ExtractorCase}- und {@link CheckerCase}-Regressionsfälle aus
 * JSON-Dateien im Klassenpfad – je Testfall genau eine Datei, alle Dateien
 * eines Falltyps gemeinsam in einem Verzeichnis, z. B.:
 *
 * <pre>
 * src/test/resources/.../regression/extractor-cases/thematically-foreign-fact-in-marketing-copy.json
 * src/test/resources/.../regression/extractor-cases/self-referential-we-framing.json
 * src/test/resources/.../regression/checker-cases/false-title-checked-against-real-occupation.json
 * ...
 * </pre>
 *
 * Aufruf:
 * <pre>
 * List&lt;ExtractorCase&gt; cases = RegressionCaseLoader.loadExtractorCases(
 *         "/.../regression/extractor-cases");
 * List&lt;CheckerCase&gt; checkerCases = RegressionCaseLoader.loadCheckerCases(
 *         "/.../regression/checker-cases");
 * </pre>
 *
 * <p><b>Wichtig für Records + Jackson:</b> Maven-Compiler-Plugin mit
 * {@code <parameters>true</parameters>} übersetzen, damit Jackson die
 * Record-Komponentennamen (name, text, languageCode, ...) automatisch den
 * JSON-Feldern zuordnen kann.</p>
 *
 * <p><b>Klassenpfad-Annahme:</b> die Verzeichnis-Auflistung per
 * {@code Files.list} setzt voraus, dass die Testressourcen als normale
 * Dateien vorliegen (Standardfall bei {@code mvn test}/{@code mvn verify},
 * da Surefire/Failsafe direkt gegen {@code target/test-classes} laufen,
 * nicht gegen ein gepacktes JAR). Falls die Regressionstests jemals aus
 * einem JAR heraus laufen sollten, bräuchte es stattdessen eine
 * Manifest-Datei mit der Liste der Dateinamen statt der Verzeichnis-Auflistung.</p>
 */
final class RegressionCaseLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            // Tippfehler/veraltete Felder im JSON sollen beim Laden hart auffallen,
            // nicht erst als stiller Default im Test-Assert.
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
                    "Regression-Testdaten-Verzeichnis nicht gefunden: " + classpathDirectory);
        }

        Path dirPath;
        try {
            dirPath = Paths.get(dirUrl.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    "Ungültige Klassenpfad-URI für Verzeichnis " + classpathDirectory, e);
        }

        List<Path> jsonFiles;
        try (Stream<Path> files = Files.list(dirPath)) {
            jsonFiles = files
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted() // deterministische Reihenfolge unabhängig vom Dateisystem
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Fehler beim Auflisten der Regression-Testdaten in " + classpathDirectory, e);
        }

        if (jsonFiles.isEmpty()) {
            throw new IllegalStateException(
                    "Keine JSON-Dateien in " + classpathDirectory + " gefunden");
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
            throw new UncheckedIOException("Fehler beim Parsen von " + file, e);
        }
    }
}