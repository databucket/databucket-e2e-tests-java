package pl.databucket.e2e.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.restassured.common.config.NumberReturnType;
import io.restassured.path.json.JsonPath;
import io.restassured.path.json.config.JsonPathConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON for the tests: request bodies in the order written, reading responses by path, pretty printing. */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    /** Pretty-printed as JSON.stringify(value, null, 2) does: 2 spaces, "key": value, every array item on its own line. */
    private static final ObjectWriter PRETTY = MAPPER.writer(new DefaultPrettyPrinter()
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n"))
            .withSeparators(Separators.createDefaultInstance()
                    .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                    .withObjectEmptySeparator("")
                    .withArrayEmptySeparator("")));

    /** Numbers with a fraction as Double (Integer / Long otherwise), so they compare with literals such as 120.5. */
    private static final JsonPathConfig PATH_CONFIG = JsonPathConfig.jsonPathConfig()
            .numberReturnType(NumberReturnType.DOUBLE);

    private Json() {
    }

    /** A JSON object, keys in the given order: {@code obj("customer", "Alice", "total", 10)}. */
    public static Map<String, Object> obj(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) throw new IllegalArgumentException("obj(...) needs pairs of key and value");
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        return map;
    }

    /** A JSON array. */
    public static List<Object> arr(Object... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    /**
     * A value of the JSON text by a GPath of REST Assured: {@code "data.properties.customer"} (a list of every row's
     * customer), {@code "$"} (the whole document), {@code "data.size()"}.
     */
    public static <T> T path(String json, String path) {
        return new JsonPath(json).using(PATH_CONFIG).get(path);
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static String pretty(JsonNode value) {
        try {
            return PRETTY.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** The parsed JSON, or null when the text is empty or not JSON. */
    public static JsonNode parseOrNull(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
