package pl.databucket.e2e.support;

import java.util.Set;

/** What the report shows of the API calls (see {@link Api} and {@link HttpView}). Names are compared in lower case. */
public final class ReportConfig {

    /**
     * Response headers shown in the report (and copied "with headers"); the others (Cache-Control, Vary,
     * X-Frame-Options...) are the same for every call. Add e.g. "x-correlation-id" or "traceparent" when needed.
     */
    public static final Set<String> RESPONSE_HEADERS = Set.of("content-type", "location", "date", "x-request-id");

    /** Request and response headers whose values are masked (***), so reports can be shared. */
    public static final Set<String> SECRET_HEADERS = Set.of("authorization", "cookie", "set-cookie");

    /** JSON fields, at any depth of a request or response body, whose values are masked (***). */
    public static final Set<String> SECRET_FIELDS = Set.of("password", "newPassword", "token", "ssoToken");

    /**
     * The checks of a response ({@code call().verify(...)}, {@code BucketApi.get(id, expected)}): every check in the
     * step "verification: 4 of 5 passed" (true), or only the failed ones (false). The default here; for a run
     * {@code -PshowPassedChecks=false} or the environment variable {@code SHOW_PASSED_CHECKS=false}.
     */
    public static final boolean SHOW_PASSED_CHECKS =
            Boolean.parseBoolean(Env.value("showPassedChecks", "SHOW_PASSED_CHECKS", "true"));

    private ReportConfig() {
    }
}
