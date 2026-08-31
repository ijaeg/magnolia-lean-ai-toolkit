package info.ijaeg.mgnl.ai.regression;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.Assert.assertTrue;

/**
 * Generische Basis für die Regressions-Testklassen (JUnit 4, wie der Rest
 * des Moduls). Kennt weder {@link ExtractorCase} noch {@link CheckerCase} –
 * nur ihre Subklassen wissen, welchen konkreten Falltyp sie durchlaufen.
 */
abstract class AbstractRegressionIT {

    /** Sammelt alle Fehlschläge statt beim ersten abzubrechen. */
    protected <T> void runAllAndAssert(List<T> cases, Consumer<T> singleCaseCheck,
                                       Function<T, String> caseNameFn) {
        List<String> failures = new ArrayList<>();
        for (T testCase : cases) {
            try {
                singleCaseCheck.accept(testCase);
            } catch (AssertionError e) {
                failures.add(caseNameFn.apply(testCase) + ": " + e.getMessage());
            }
        }
        assertTrue(failures.size() + " von " + cases.size() + " Fällen fehlgeschlagen:\n"
                        + String.join("\n", failures),
                failures.isEmpty());
    }
}