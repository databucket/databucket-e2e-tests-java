package pl.databucket.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.model.Stage;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.model.StepResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Soft checks shown in the report: one step "verification: 4 of 5 passed" with every check as a step with its result
 * (only the failed ones with {@link ReportConfig#SHOW_PASSED_CHECKS} false). All checks of a verification are made,
 * then the test fails with all the failures in the message.
 * <p>
 * Right after an API call the verification goes into the step of that call, after its request / response;
 * otherwise (GUI tests, values computed by the test) it is a step of its own.
 * <pre>{@code
 * verify(orders.get(id).orElseThrow().properties(), new Order("Alice", "PAID", 10.5)); // field by field
 * verify("rows", page.rows().size(), 3);                                                // one value
 * check("all rows from PL", () -> assertThat(countries).containsOnly("PL"));             // any AssertJ check
 * verifyAll(v -> v.that("total", page.total(), 3).that("first", page.rows().get(0).properties(), alice));
 * }</pre>
 * Objects of the model (records, maps, lists) are compared field by field: each field of the expected object is a
 * check (nested fields and list items too, a list also by its size); a field that is null in the expected object is
 * not checked. Numbers are compared by value (10 = 10.0).
 */
public final class Verify {

    /** An AssertJ (or any) assertion run as one check. */
    @FunctionalInterface
    public interface Assertion {
        void run() throws Throwable;
    }

    /** The checks of one verification. */
    public static final class Checks {

        final List<Row> rows = new ArrayList<>();

        private Checks() {
        }

        /** The actual value or object against the expected one, field by field. */
        public Checks that(Object actual, Object expected) {
            return that("", actual, expected);
        }

        /** As {@link #that(Object, Object)}, named in the report ("rows", "properties", "total"...). */
        public Checks that(String what, Object actual, Object expected) {
            JsonNode actualTree = actual instanceof JsonNode node ? node : Json.MAPPER.valueToTree(actual);
            compare(what, actualTree, "", Json.MAPPER.valueToTree(expected));
            return this;
        }

        /** Any assertion as one check, e.g. {@code check("all from PL", () -> assertThat(c).containsOnly("PL"))}. */
        public Checks check(String what, Assertion assertion) {
            try {
                assertion.run();
                rows.add(new Row(what, true));
            } catch (AssertionError e) {
                rows.add(new Row(what + ": " + firstLines(e.getMessage()), false));
            } catch (Throwable e) {
                rows.add(new Row(what + ": " + e, false));
            }
            return this;
        }

        /** The status of an API response. */
        Checks status(int actual, int expected) {
            rows.add(actual == expected ? new Row("status = " + expected, true)
                    : new Row("status: expected " + expected + ", was " + actual, false));
            return this;
        }

        /** The JSON of a response at the path ("" = all of it) against the expected value or object. */
        Checks json(String path, JsonNode root, Object expected) {
            compare(path, root, path, Json.MAPPER.valueToTree(expected));
            return this;
        }

        // the expected value against the actual tree: each leaf of the expected value is a check
        private void compare(String shown, JsonNode actualRoot, String actualPath, JsonNode expected) {
            if (expected == null || expected.isNull()) {
                if (shown.isEmpty()) rows.add(new Row("null expected: nothing to check", true));
                return;
            }
            if (expected.isObject() && !expected.isEmpty()) {
                expected.properties().forEach(field -> compare(join(shown, field.getKey()), actualRoot,
                        join(actualPath, field.getKey()), field.getValue()));
                return;
            }
            JsonNode actual = actualRoot == null ? null : find(actualRoot, actualPath);
            String name = shown.isEmpty() ? "value" : shown;
            if (expected.isArray() && !expected.isEmpty()) {
                if (actual == null || !actual.isArray()) {
                    rows.add(new Row(name + ": expected a list of " + expected.size()
                            + (actual == null ? ", the field is missing" : ", was " + Json.write(actual)), false));
                    return;
                }
                rows.add(actual.size() == expected.size() ? new Row(name + ": size = " + expected.size(), true)
                        : new Row(name + ": expected size " + expected.size() + ", was " + actual.size(), false));
                for (int i = 0; i < expected.size(); i++)
                    compare(shown + "[" + i + "]", actualRoot, actualPath + "[" + i + "]", expected.get(i));
                return;
            }
            String value = Json.write(expected);
            if (actual == null)
                rows.add(new Row(name + ": expected " + value + ", the field is missing", false));
            else if (same(expected, actual))
                rows.add(new Row(name + " = " + value, true));
            else
                rows.add(new Row(name + ": expected " + value + ", was " + Json.write(actual), false));
        }
    }

    record Row(String text, boolean ok) {
    }

    private static final Pattern PART = Pattern.compile("([^.\\[\\]]+)|\\[(\\d+)]");

    // the step of the last API call of the thread (Api), to which a verification right after it is added
    private static final ThreadLocal<StepResult> LAST_CALL = new ThreadLocal<>();

    private Verify() {
    }

    // ---------------------------------------------------------------- for the tests

    /** The actual value or object against the expected one, field by field. */
    public static void verify(Object actual, Object expected) {
        verifyAll(v -> v.that(actual, expected));
    }

    /** As {@link #verify(Object, Object)}, named in the report. */
    public static void verify(String what, Object actual, Object expected) {
        verifyAll(v -> v.that(what, actual, expected));
    }

    /** Any assertion as one check. */
    public static void check(String what, Assertion assertion) {
        verifyAll(v -> v.check(what, assertion));
    }

    /** Several checks in one verification. */
    public static void verifyAll(Consumer<Checks> checks) {
        Checks all = new Checks();
        checks.accept(all);
        report(all.rows, false);
    }

    // ---------------------------------------------------------------- for Api

    /** The step of an API call has just been made (called from inside it). */
    static void callStarted() {
        Allure.getLifecycle().updateStep(LAST_CALL::set);
    }

    static Checks checks() {
        return new Checks();
    }

    /**
     * Reports the rows as a verification and throws when any failed: inside the current step (an API call with
     * {@code call().verify(...)}), or else in the step of the API call made right before, or else as a step of its own.
     */
    static void report(List<Row> rows, boolean inCurrentStep) {
        if (rows.isEmpty()) return;
        long failed = rows.stream().filter(row -> !row.ok()).count();
        String name = "verification: " + (rows.size() - failed) + " of " + rows.size() + " passed";
        String failures = rows.stream().filter(row -> !row.ok()).map(Row::text)
                .reduce((a, b) -> a + "\n  " + b).orElse("");

        StepResult lastCall = inCurrentStep ? null : lastCallStep();
        if (lastCall != null) {
            lastCall.getSteps().add(step(name, rows));
            if (failed > 0) {
                lastCall.setStatus(Status.FAILED);
                lastCall.setStatusDetails(new StatusDetails().setMessage(failed + " of " + rows.size()
                        + " checks failed:\n  " + failures));
            }
        } else {
            AllureLifecycle lifecycle = Allure.getLifecycle();
            String uuid = UUID.randomUUID().toString();
            lifecycle.startStep(uuid, new StepResult().setName(name));
            for (Row row : rows)
                if (!row.ok() || ReportConfig.SHOW_PASSED_CHECKS)
                    Allure.step(row.text(), row.ok() ? Status.PASSED : Status.FAILED);
            lifecycle.updateStep(uuid, step -> step.setStatus(failed > 0 ? Status.FAILED : Status.PASSED));
            lifecycle.stopStep(uuid);
        }
        if (failed > 0) {
            String where = lastCall != null ? lastCall.getName() + ": " : "";
            throw new AssertionError(where + failed + " of " + rows.size() + " checks failed:\n  " + failures);
        }
    }

    // the step of the API call that is the last step of the current test or step, or null
    private static StepResult lastCallStep() {
        StepResult call = LAST_CALL.get();
        if (call == null) return null;
        AllureLifecycle lifecycle = Allure.getLifecycle();
        List<List<StepResult>> current = new ArrayList<>();
        if (Objects.equals(lifecycle.getCurrentTestCaseOrStep(), lifecycle.getCurrentTestCase()))
            lifecycle.updateTestCase(test -> current.add(test.getSteps()));
        else
            lifecycle.updateStep(step -> current.add(step.getSteps()));
        if (current.isEmpty() || current.get(0).isEmpty()) return null;
        List<StepResult> steps = current.get(0);
        return steps.get(steps.size() - 1) == call ? call : null;
    }

    private static StepResult step(String name, List<Row> rows) {
        long now = System.currentTimeMillis();
        StepResult step = new StepResult().setName(name).setStage(Stage.FINISHED).setStart(now).setStop(now)
                .setStatus(rows.stream().allMatch(Row::ok) ? Status.PASSED : Status.FAILED);
        for (Row row : rows)
            if (!row.ok() || ReportConfig.SHOW_PASSED_CHECKS)
                step.getSteps().add(new StepResult().setName(row.text()).setStage(Stage.FINISHED).setStart(now)
                        .setStop(now).setStatus(row.ok() ? Status.PASSED : Status.FAILED));
        return step;
    }

    // ---------------------------------------------------------------- helpers

    private static String join(String path, String field) {
        return path.isEmpty() ? field : path + "." + field;
    }

    /** The node at the path ("" = the root), or null when a part of it is missing (a JSON null is a node). */
    static JsonNode find(JsonNode root, String path) {
        JsonNode node = root;
        Matcher matcher = PART.matcher(path);
        while (matcher.find()) {
            if (node == null) return null;
            if (matcher.group(1) != null) {
                if (!node.isObject() || !node.has(matcher.group(1))) return null;
                node = node.get(matcher.group(1));
            } else {
                int index = Integer.parseInt(matcher.group(2));
                if (!node.isArray() || index >= node.size()) return null;
                node = node.get(index);
            }
        }
        return node;
    }

    /** Equal JSON values; numbers by value (10 = 10.0). */
    private static boolean same(JsonNode expected, JsonNode actual) {
        if (expected.isNumber() && actual.isNumber())
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        return expected.equals((a, b) -> a.isNumber() && b.isNumber()
                ? a.decimalValue().compareTo(b.decimalValue()) : a.equals(b) ? 0 : 1, actual);
    }

    private static String firstLines(String message) {
        if (message == null) return "failed";
        String text = message.strip().replaceAll("\\s*\\R\\s*", " ");
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
