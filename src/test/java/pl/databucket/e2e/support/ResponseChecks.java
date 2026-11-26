package pl.databucket.e2e.support;

import java.util.List;

/**
 * The checks of {@link Call#verify}: made in the step of the call, after its request / response, as a
 * {@link Verify verification}; the call fails when any of them failed.
 */
final class ResponseChecks {

    /** The expected value or object at the path of the response JSON ("" = all of it). */
    record Check(String path, Object expected) {
    }

    private ResponseChecks() {
    }

    /**
     * In the step of the call when reported; a quiet call (no step) only fails, with the call and its response in
     * the message (its request / response isn't in the report).
     */
    static void verify(ApiResponse response, Integer expectStatus, List<Check> checks, boolean report, String call) {
        Verify.Checks rows = Verify.checks();
        if (expectStatus != null) rows.status(response.status(), expectStatus);
        var root = checks.isEmpty() ? null : Json.parseOrNull(response.text());
        for (Check check : checks) rows.json(check.path(), root, check.expected());
        if (report) {
            Verify.report(rows.rows, true);
        } else if (rows.rows.stream().anyMatch(row -> !row.ok())) {
            throw new AssertionError(rows.rows.stream().filter(row -> !row.ok()).map(Verify.Row::text)
                    .reduce((a, b) -> a + "\n  " + b).orElse("") + "\n" + call);
        }
    }
}
