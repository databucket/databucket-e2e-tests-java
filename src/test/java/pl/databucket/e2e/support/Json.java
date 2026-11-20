package pl.databucket.e2e.support;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.common.config.NumberReturnType;
import io.restassured.path.json.JsonPath;
import io.restassured.path.json.config.JsonPathConfig;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON for the tests: request bodies in the order written, reading responses by path, pretty printing. */
public final class Json {

    /**
     * Writes request bodies and reads responses into the model ({@link ApiResponse#as}): dates as ISO 8601, and a
     * field of a response the model doesn't know fails the test, so every change of the API shows up.
     */
    private static final DateTimeFormatter SERVER_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            // dates as the server writes and reads them: yyyy-MM-dd'T'HH:mm:ss.SSS'Z' (it refuses other ISO 8601 forms)
            .registerModule(new SimpleModule().addSerializer(Instant.class, new JsonSerializer<Instant>() {
                @Override
                public void serialize(Instant value, JsonGenerator generator, SerializerProvider provider) throws IOException {
                    generator.writeString(SERVER_DATE.format(value));
                }
            }))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

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

    /** The JSON text read as the given type, e.g. {@code Json.read(text, Json.type(DataPage.class, Person.class))}. */
    public static <T> T read(String text, JavaType type) {
        try {
            return MAPPER.readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new AssertionError("The response doesn't match " + type + ": " + e.getOriginalMessage() + "\n" + text, e);
        }
    }

    /** A type with type parameters, e.g. {@code type(DataPage.class, Person.class)} for {@code DataPage<Person>}. */
    public static JavaType type(Class<?> type, Class<?>... parameters) {
        return MAPPER.getTypeFactory().constructParametricType(type, parameters);
    }

    public static JavaType type(Class<?> type, JavaType... parameters) {
        return MAPPER.getTypeFactory().constructParametricType(type, parameters);
    }

    public static JavaType listOf(Class<?> type) {
        return MAPPER.getTypeFactory().constructCollectionType(java.util.List.class, type);
    }

    public static JavaType listOf(JavaType type) {
        return MAPPER.getTypeFactory().constructCollectionType(java.util.List.class, type);
    }
}
