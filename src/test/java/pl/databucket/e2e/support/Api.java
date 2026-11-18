package pl.databucket.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.qameta.allure.Allure;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A REST client for the tests (REST Assured underneath). Every call is a step in the report
 * ("POST /api/buckets → 201") with the request and the response attached as one HTML view (see {@link HttpView}).
 * Tokens and passwords are masked, so reports can be shared.
 * <p>
 * Quiet clients (used to prepare test data) skip the steps and attachments.
 * <p>
 * Thread-safe: every call builds its own request, nothing global of REST Assured is changed.
 */
public final class Api {

    private static final RestAssuredConfig CONFIG = RestAssuredConfig.config()
            // Content-Type exactly as given (no "; charset=ISO-8859-1" added)
            .encoderConfig(EncoderConfig.encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false));

    private final String user;
    private final String token;
    private final boolean quiet;

    /** A user without a token, e.g. {@code new Api("anonymous")}. */
    public Api(String user) {
        this(user, null, false);
    }

    Api(String user, String token, boolean quiet) {
        this.user = user;
        this.token = token;
        this.quiet = quiet;
    }

    /** Who calls: the role in the test project (admin, member...) or e.g. "anonymous"; not shown in the report. */
    public String user() {
        return user;
    }

    public String token() {
        return token;
    }

    /** The same user with his token. */
    public Api withToken(String token) {
        return new Api(user, token, quiet);
    }

    /** The same user, reported in the steps. */
    public Api loud() {
        return new Api(user, token, false);
    }

    /** The same user, not reported: for preparing data. */
    public Api quiet() {
        return new Api(user, token, true);
    }

    public ApiResponse get(String path) {
        return call("GET", path, Call.call());
    }

    public ApiResponse get(String path, Call options) {
        return call("GET", path, options);
    }

    public ApiResponse post(String path, Call options) {
        return call("POST", path, options);
    }

    public ApiResponse put(String path, Call options) {
        return call("PUT", path, options);
    }

    public ApiResponse delete(String path) {
        return call("DELETE", path, Call.call());
    }

    public ApiResponse delete(String path, Call options) {
        return call("DELETE", path, options);
    }

    /**
     * Calls the API. The path is sent as given (no URL encoding): encode query values yourself, e.g.
     * {@code URLEncoder.encode("$.total", UTF_8)}.
     */
    public ApiResponse call(String method, String path, Call options) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.putAll(options.headers);
        if (token != null) headers.put("Authorization", "Bearer " + token);
        String body = options.body == null ? null : options.body instanceof String text ? text : Json.write(options.body);
        if (body != null) headers.put("Content-Type", "application/json");

        if (quiet) return send(method, path, headers, body, options, null);
        return Allure.step(method + " " + path, (Allure.ThrowableContextRunnable<ApiResponse, Allure.StepContext>) step -> send(method, path, headers, body, options, step));
    }

    private ApiResponse send(String method, String path, Map<String, String> headers, String body, Call options,
                             Allure.StepContext step) {
        RequestSpecification request = RestAssured.given()
                .config(CONFIG)
                .baseUri(Env.BASE_URL)
                .urlEncodingEnabled(false)
                .headers(headers);
        if (body != null) request.body(body);

        long start = System.nanoTime();
        Response response = request.request(method, path);
        String text = response.asString();
        long took = (System.nanoTime() - start) / 1_000_000;

        Map<String, String> responseHeaders = new LinkedHashMap<>();
        for (Header header : response.getHeaders()) responseHeaders.put(header.getName().toLowerCase(Locale.ROOT), header.getValue());
        ApiResponse result = new ApiResponse(response.getStatusCode(), responseHeaders, text);

        if (step != null) {
            step.name(method + " " + path + " → " + result.status());
            JsonNode requestJson = body == null ? null : Json.parseOrNull(body);
            JsonNode responseJson = Json.parseOrNull(text);
            String view = HttpView.render(new HttpView.Exchange(
                    method,
                    Env.BASE_URL + path,
                    maskHeaders(headers),
                    requestJson != null ? mask(requestJson) : body == null ? null : TextNode.valueOf(body),
                    result.status(),
                    statusText(response.getStatusLine()),
                    took,
                    maskHeaders(shown(responseHeaders)),
                    responseJson != null ? mask(responseJson) : text.isEmpty() ? null : TextNode.valueOf(text)));
            Allure.addAttachment(options.name != null ? options.name : "request / response", "text/html", view, ".html");
        }
        if (options.expectStatus != null && result.status() != options.expectStatus)
            throw new AssertionError(method + " " + path + ": expected status " + options.expectStatus + ", got "
                    + result.status() + "\n" + text.substring(0, Math.min(text.length(), 2000)));
        return result;
    }

    /** "HTTP/1.1 200 OK" → "OK". */
    private static String statusText(String statusLine) {
        String[] parts = statusLine == null ? new String[0] : statusLine.split(" ", 3);
        return parts.length == 3 ? parts[2].trim() : "";
    }

    private static JsonNode mask(JsonNode value) {
        JsonNode copy = value.deepCopy();
        maskInPlace(copy);
        return copy;
    }

    private static void maskInPlace(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (Map.Entry<String, JsonNode> field : object.properties()) {
                if (ReportConfig.SECRET_FIELDS.contains(field.getKey())) field.setValue(TextNode.valueOf("***"));
                else maskInPlace(field.getValue());
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(Api::maskInPlace);
        }
    }

    private static Map<String, String> maskHeaders(Map<String, String> headers) {
        Map<String, String> masked = new LinkedHashMap<>();
        headers.forEach((name, value) -> masked.put(name, ReportConfig.SECRET_HEADERS.contains(name.toLowerCase(Locale.ROOT))
                ? value.replaceAll("^(Bearer )?.+$", "$1***") : value));
        return masked;
    }

    /** The response headers worth seeing: {@link ReportConfig#RESPONSE_HEADERS}. */
    private static Map<String, String> shown(Map<String, String> headers) {
        Map<String, String> shown = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            if (ReportConfig.RESPONSE_HEADERS.contains(name)) shown.put(name, value);
        });
        return shown;
    }
}
