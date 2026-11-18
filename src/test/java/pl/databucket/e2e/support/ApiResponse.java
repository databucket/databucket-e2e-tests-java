package pl.databucket.e2e.support;

import java.util.Map;

/**
 * The response of an API call.
 *
 * @param headers the response headers, names in lower case
 * @param text    the body as received
 */
public record ApiResponse(int status, Map<String, String> headers, String text) {

    /**
     * A value of the JSON body by a GPath of REST Assured: {@code path("token")}, {@code path("data.properties.customer")}
     * (the customer of every row), {@code path("$")} (the whole body), {@code path("data.size()")}.
     */
    public <T> T path(String path) {
        return Json.path(text, path);
    }
}
