package pl.databucket.e2e.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

/** Rules every kind of object in the settings follows. */
@ApiSuite
@Area(Area.SETTINGS)
@DisplayName("Common rules")
class SettingsRulesTest {

    /** The settings with a name of at most 30 characters, and a valid object of each. */
    static Stream<Arguments> named() {
        return Stream.of(
                Arguments.of("/api/buckets", Map.of("icon", Map.of("name", "Storage"))),
                Arguments.of("/api/classes", Map.of()),
                Arguments.of("/api/enums", Map.of("items", java.util.List.of())),
                Arguments.of("/api/tags", Map.of()),
                Arguments.of("/api/groups", Map.of("shortName", "G")),
                Arguments.of("/api/teams", Map.of()),
                Arguments.of("/api/filters", Map.of()),
                Arguments.of("/api/tasks", Map.of()));
    }

    static Stream<String> settings() {
        return Stream.of("/api/buckets", "/api/classes", "/api/enums", "/api/columns", "/api/filters", "/api/groups",
                "/api/tags", "/api/tasks", "/api/teams", "/api/views", "/api/svg");
    }

    /** What the web application reads for every user: how the data looks (classes, enums, tags, icons, filters, tasks). */
    static Stream<String> readByAll() {
        return Stream.of("/api/classes", "/api/enums", "/api/filters", "/api/tags", "/api/tasks", "/api/svg");
    }

    /** Who sees what: only for an ADMIN. */
    static Stream<String> adminOnly() {
        return Stream.of("/api/buckets", "/api/columns", "/api/groups", "/api/teams", "/api/views");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("readByAll")
    @DisplayName("a MEMBER reads how the data looks")
    void member_reads(String path, TestProject project) {
        verify("status", project.as(MEMBER).get(path).status(), 200);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adminOnly")
    @DisplayName("a MEMBER can not read who sees what")
    void member_can_not_read(String path, TestProject project) {
        verify("status", project.as(MEMBER).get(path).status(), 403);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("named")
    @DisplayName("a MEMBER can not create settings")
    void member_can_not_create(String path, Map<String, Object> valid, TestProject project) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(valid);
        body.put("name", "by-member");

        verify("status", project.as(MEMBER).post(path, call().body(body)).status(), 403);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settings")
    @DisplayName("removing an object that does not exist gives 404")
    void delete_missing(String path, TestProject project) {
        // 30000: within the ids of every kind (teams have short ids)
        verify("status", project.as(ADMIN).delete(path + "/30000").status(), 404);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("named")
    @DisplayName("a name longer than 30 characters is refused")
    void long_name(String path, Map<String, Object> valid, TestProject project) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(valid);
        body.put("name", "x".repeat(31));

        ApiResponse response = project.as(ADMIN).post(path, call().body(body));

        verify("status", response.status(), 400);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("named")
    @DisplayName("changing an object without an id is refused")
    void update_without_id(String path, Map<String, Object> valid, TestProject project) {
        Map<String, Object> body = new java.util.LinkedHashMap<>(valid);
        body.put("name", "nameless");

        ApiResponse response = project.as(ADMIN).put(path, call().body(body));

        // 404 for all but teams (400); the same message
        verifyAll(v -> v
                .check("status: 400 or 404", () -> assertThat(response.status()).isIn(400, 404))
                .check("message: contains 'null id'", () -> assertThat(response.<String>path("message")).contains("null id")));
    }
}
