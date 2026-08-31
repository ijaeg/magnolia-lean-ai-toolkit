package info.ijaeg.mgnl.ai.regression;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.Assert.assertTrue;

/**
 * Generic base for the regression test classes (JUnit 4, like the rest
 * of the module). Knows neither {@link ExtractorCase} nor {@link CheckerCase} –
 * only their subclasses know which concrete case type they iterate over.
 */
abstract class AbstractRegressionIT {

    /** Collects all failures instead of aborting on the first one. */
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
        assertTrue(failures.size() + " of " + cases.size() + " cases failed:\n"
                        + String.join("\n", failures),
                failures.isEmpty());
    }
}