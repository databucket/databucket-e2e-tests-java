package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.Json;
import pl.databucket.e2e.support.Role;
import pl.databucket.e2e.support.TestProject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.arr;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.ROBOT;

/** The settings of a project (ADMIN): buckets, classes, enums, tags, views. */
@ApiSuite
@DisplayName("Settings")
class SettingsTest {

    private static ApiResponse createBucket(Api admin, String name, Object... extra) {
        Map<String, Object> bucket = obj("icon", obj("name", "Storage"), "roleId", Role.AGENT.id, "name", name);
        bucket.putAll(obj(extra));
        return admin.post("/api/buckets", call().body(bucket));
    }

    /** The object of a response as a body to send back changed. */
    private static Map<String, Object> changed(ApiResponse response, Object... changes) {
        Map<String, Object> body = new LinkedHashMap<>(response.<Map<String, Object>>path("$"));
        body.putAll(obj(changes));
        return body;
    }

    @Nested
    @DisplayName("Settings: buckets")
    class Buckets {

        private Api admin;

        @BeforeEach
        void admin(TestProject project) {
            admin = project.as(ADMIN);
        }

        @Test
        void a_bucket_name_is_unique_in_the_project(TestProject project) {
            assertThat(createBucket(admin, "orders").status()).isEqualTo(201);

            assertThat(createBucket(admin, "orders").status()).isEqualTo(406);
        }

        @Test
        void a_bucket_name_has_at_most_30_characters(TestProject project) {
            assertThat(createBucket(admin, "x".repeat(31)).status()).isEqualTo(400);
        }

        @Test
        void a_renamed_bucket_keeps_its_data_under_the_new_name(TestProject project) {
            ApiResponse created = createBucket(admin, "orders-old");
            Api robot = project.as(ROBOT);
            robot.post("/api/bucket/orders-old", call().body(obj("properties", obj("no", 1))).expect(201));

            admin.put("/api/buckets", call().body(changed(created, "name", "orders-new")).expect(200));

            assertThat(robot.post("/api/bucket/orders-old/get", call().body(obj())).status()).isEqualTo(404);
            ApiResponse data = robot.post("/api/bucket/orders-new/get?limit=10", call().body(obj()).expect(200));
            assertThat(data.<List<Integer>>path("data.properties.no")).containsExactly(1);
        }

        @Test
        @DisplayName("a removed bucket is gone, and its name can be used again for a new, empty bucket")
        void removed_bucket(TestProject project) {
            ApiResponse created = createBucket(admin, "temp");
            Api robot = project.as(ROBOT);
            robot.post("/api/bucket/temp", call().body(obj("properties", obj("no", 1))).expect(201));

            admin.delete("/api/buckets/" + created.path("id"), call().expect(204));
            assertThat(robot.post("/api/bucket/temp/get", call().body(obj())).status()).isEqualTo(404);

            assertThat(createBucket(admin, "temp").status()).isEqualTo(201);
            ApiResponse data = robot.post("/api/bucket/temp/get?limit=10", call().body(obj()).expect(200));
            assertThat(data.<Integer>path("total")).isZero();
        }

        @Test
        void switching_the_history_off_stops_recording_the_changes(TestProject project) {
            ApiResponse created = createBucket(admin, "orders", "history", true);
            Api robot = project.as(ROBOT);
            ApiResponse row = robot.post("/api/bucket/orders", call().body(obj("properties", obj("status", "NEW"))).expect(201));

            admin.put("/api/buckets", call().body(changed(created, "history", false)).expect(200));
            robot.put("/api/bucket/orders/" + row.path("id"), call().body(obj("propertiesToSet", obj("$.status", "PAID"))).expect(200));

            ApiResponse history = robot.get("/api/bucket/orders/" + row.path("id") + "/history", call().expect(200));
            assertThat(history.<List<Object>>path("$")).hasSize(1);
        }

        @Test
        @DisplayName("indexed properties: a valid path and type, at most 5")
        void indexed_properties(TestProject project) {
            ApiResponse badPath = createBucket(admin, "idx-1", "indexedProperties",
                    arr(obj("path", "$.a'); DROP TABLE tags; --", "type", "text")));
            ApiResponse badType = createBucket(admin, "idx-2", "indexedProperties", arr(obj("path", "$.a", "type", "date")));
            List<Object> six = new ArrayList<>();
            for (String p : List.of("a", "b", "c", "d", "e", "f")) six.add(obj("path", "$." + p, "type", "text"));
            ApiResponse tooMany = createBucket(admin, "idx-3", "indexedProperties", six);
            ApiResponse fine = createBucket(admin, "idx-4", "indexedProperties",
                    arr(obj("path", "$.login", "type", "text"), obj("path", "$.age", "type", "number")));

            assertThat(List.of(badPath.status(), badType.status(), tooMany.status())).containsExactly(400, 400, 400);
            assertThat(fine.status()).isEqualTo(201);
            assertThat(fine.<List<Map<String, Object>>>path("indexedProperties")).containsExactly(
                    Map.of("path", "$.login", "type", "text"), Map.of("path", "$.age", "type", "number"));
        }
    }

    @Nested
    @DisplayName("Settings: classes, enums, tags, views")
    class Objects {

        private Api admin;

        @BeforeEach
        void admin(TestProject project) {
            admin = project.as(ADMIN);
        }

        @Test
        void a_class_describes_the_fields_of_a_bucket_its_name_is_unique(TestProject project) {
            List<Object> configuration = arr(obj("title", "Login", "path", "$.login", "type", "string", "uuid", "u-login"));
            ApiResponse created = admin.post("/api/classes", call().body(obj("name", "user", "configuration", configuration)).expect(201));
            ApiResponse again = admin.post("/api/classes", call().body(obj("name", "user", "configuration", configuration)));

            assertThat(created.<String>path("name")).isEqualTo("user");
            assertThat(created.<List<Map<String, Object>>>path("configuration")).hasSize(1);
            assertThat(created.<String>path("configuration[0].path")).isEqualTo("$.login");
            assertThat(again.status()).isEqualTo(406);
        }

        @Test
        void an_enum_keeps_its_items_in_order(TestProject project) {
            List<Object> items = arr(obj("value", "gold", "text", "Gold"), obj("value", "silver", "text", "Silver"),
                    obj("value", "bronze", "text", "Bronze"));
            admin.post("/api/enums", call().body(obj("name", "levels", "items", items)).expect(201));

            List<Map<String, Object>> enums = admin.get("/api/enums", call().expect(200)).path("$");

            Map<String, Object> levels = enums.stream().filter(e -> "levels".equals(e.get("name"))).findFirst().orElseThrow();
            assertThat(Json.<List<String>>path(Json.write(levels), "items.value")).containsExactly("gold", "silver", "bronze");
        }

        @Test
        void a_tag_name_is_unique_a_tag_in_use_can_be_renamed(TestProject project) {
            ApiResponse tag = admin.post("/api/tags", call().body(obj("name", "new")).expect(201));
            ApiResponse again = admin.post("/api/tags", call().body(obj("name", "new")));
            project.createBucket("orders");
            Api robot = project.quietly(ROBOT);
            ApiResponse row = robot.post("/api/bucket/orders", call().body(obj("properties", obj(), "tagId", tag.path("id"))).expect(201));

            admin.put("/api/tags", call().body(changed(tag, "name", "fresh")).expect(200));

            assertThat(again.status()).isEqualTo(406);
            ApiResponse read = robot.get("/api/bucket/orders/" + row.path("id"), call().expect(200));
            assertThat(read.<Number>path("tagId").longValue()).isEqualTo(tag.<Number>path("id").longValue());
            assertThat(admin.get("/api/tags", call().expect(200)).<List<String>>path("name")).containsExactly("fresh");
        }

        @Test
        void a_view_starts_sorted_only_by_an_allowed_field(TestProject project) {
            long bucketId = project.createBucket("orders");
            ApiResponse columns = admin.post("/api/columns", call().body(obj(
                    "name", "cols", "configuration", obj("columns", arr(), "properties", arr()))).expect(201));

            ApiResponse bad = admin.post("/api/views", call().body(view(columns, bucketId, "desc(password)")));
            ApiResponse injected = admin.post("/api/views", call().body(view(columns, bucketId, "asc($.a'))--)")));
            ApiResponse good = admin.post("/api/views", call().body(view(columns, bucketId, "asc($.customer.name)")));

            assertThat(bad.status()).isEqualTo(400);
            assertThat(injected.status()).isEqualTo(400);
            assertThat(good.status()).isEqualTo(201);
            assertThat(good.<String>path("sort")).isEqualTo("asc($.customer.name)");
        }

        private Map<String, Object> view(ApiResponse columns, long bucketId, String sort) {
            return obj("name", "all", "columnsId", columns.path("id"), "bucketsIds", arr(bucketId),
                    "roleId", Role.MEMBER.id, "sort", sort);
        }
    }
}
