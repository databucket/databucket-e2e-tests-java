package pl.databucket.e2e.support;

import com.fasterxml.jackson.databind.JavaType;

import java.util.List;
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

    /** The body read as the model: {@code response.as(Tag.class)}. */
    public <T> T as(Class<T> type) {
        return Json.read(text, Json.MAPPER.constructType(type));
    }

    /** The body read as a generic model: {@code response.as(Json.type(DataPage.class, Person.class))}. */
    public <T> T as(JavaType type) {
        return Json.read(text, type);
    }

    /** The body read as a list: {@code response.asListOf(Tag.class)}. */
    public <T> List<T> asListOf(Class<T> type) {
        return Json.read(text, Json.listOf(type));
    }
}
