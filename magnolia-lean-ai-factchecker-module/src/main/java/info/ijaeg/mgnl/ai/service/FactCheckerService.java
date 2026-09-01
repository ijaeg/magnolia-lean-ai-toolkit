package info.ijaeg.mgnl.ai.service;

import info.ijaeg.mgnl.ai.agent.FactChecker;

import java.util.List;

/**
 * Public entry point for the fact-checking feature: extracts factual claims
 * from a piece of text and checks each one against Wikipedia.
 */
public interface FactCheckerService {

    /**
     * Extracts and checks factual claims from {@code text}.
     *
     * @param text     arbitrary content, e.g. HTML from a JCR property; HTML
     *                 markup is stripped before extraction
     * @param language ISO-639-1 code (e.g. {@code "en"}, {@code "de"}) used
     *                 both to steer claim extraction and to select the
     *                 language-specific Wikipedia REST client for
     *                 verification
     * @return one {@link FactChecker.ClaimCheckResult} per extracted claim
     * that could be checked; empty if no claims were found. A claim
     * is silently dropped (not included) only if the fact-checking
     * model failed to produce a parseable result twice in a row
     * (see {@link FactCheckerServiceImpl})
     */
    List<FactChecker.ClaimCheckResult> check(String text, String language);
}
