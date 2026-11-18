package pl.databucket.e2e.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Options of one API call: {@code robot.post(path, call().v2().body(obj(...)).expect(201))}.
 */
public final class Call {

    Object body;
    final Map<String, String> headers = new LinkedHashMap<>();
    Integer expectStatus;
    String name;

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

    /** Version 2 of the data API (header Databucket-Api-Version: 2). */
    public Call v2() {
        return header("Databucket-Api-Version", "2");
    }

    /** Fail the call when the status differs. */
    public Call expect(int status) {
        this.expectStatus = status;
        return this;
    }

    /** The name of the call in the report (the attachment), e.g. "GET data by rules"; "request / response" by default. */
    public Call name(String name) {
        this.name = name;
        return this;
    }
}
