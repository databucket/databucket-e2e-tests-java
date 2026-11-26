package pl.databucket.e2e.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Options of one API call: {@code robot.post(path, call().body(obj(...)).expect(201))}.
 */
public final class Call {

    Object body;
    final Map<String, String> headers = new LinkedHashMap<>();
    Integer expectStatus;
    String name;
    final List<ResponseChecks.Check> checks = new ArrayList<>();

    private Call() {
    }

    public static Call call() {
        return new Call();
    }

    /** The request body: a map / list / record written as JSON, or a JSON text. */
    public Call body(Object body) {
        this.body = body;
        return this;
    }

    public Call header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /** Fail the call when the status differs. */
    public Call expect(int status) {
        this.expectStatus = status;
        return this;
    }

    /**
     * Soft checks of the response against an object of the model, made in the step of the call: one check per field
     * (nested fields and list items too; a null field is not checked). All checks (and the status of
     * {@link #expect}) are steps of the report with their result; the call fails at the end when any of them failed.
     */
    public Call verify(Object expected) {
        return verify("", expected);
    }

    /** As {@link #verify(Object)}, for the part of the response at the path, e.g. {@code verify("properties", order)}. */
    public Call verify(String path, Object expected) {
        checks.add(new ResponseChecks.Check(path, expected));
        return this;
    }

    /** The name of the call in the report (the attachment), e.g. "GET data by rules"; "request / response" by default. */
    public Call name(String name) {
        this.name = name;
        return this;
    }
}
