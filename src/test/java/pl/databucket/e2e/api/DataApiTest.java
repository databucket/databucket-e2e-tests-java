package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.arr;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;

@ApiSuite
@DisplayName("Data API v2")
class DataApiTest {

    private static final String BUCKET = "orders";
    private static final String DATA = "/api/bucket/" + BUCKET;

    @BeforeEach
    void bucket(TestProject project) {
        project.createBucket(BUCKET);
    }

    @Test
    void inserts_a_row_and_reads_it_by_id(TestProject project) {
        Api robot = project.as(ROBOT);

        ApiResponse inserted = robot.post(DATA, call().v2()
                .body(obj("properties", obj("customer", "Alice", "status", "NEW", "total", 120.5)))
                .expect(201));
        long id = inserted.<Number>path("id").longValue();
        assertThat(inserted.<Map<String, Object>>path("properties"))
                .isEqualTo(Map.of("customer", "Alice", "status", "NEW", "total", 120.5));
        assertThat(inserted.<Boolean>path("reserved")).isFalse();

        ApiResponse read = robot.get(DATA + "/" + id, call().v2().expect(200));
        assertThat(read.<Number>path("id").longValue()).isEqualTo(id);
        assertThat(read.<String>path("properties.customer")).isEqualTo("Alice");
        assertThat(read.<String>path("createdBy")).isEqualTo(project.user(ROBOT).username());
    }

    @Test
    @DisplayName("in a bucket with protected data a member inserts a free row and gets it back, but sees only own rows later")
    void protected_data(TestProject project) {
        project.createBucket("secrets", true, true);
        Api member = project.as(MEMBER);

        ApiResponse inserted = member.post("/api/bucket/secrets", call().v2()
                .body(obj("properties", obj("login", "erin")))
                .expect(201));
        assertThat(inserted.<Boolean>path("reserved")).isFalse();
        assertThat(inserted.<String>path("properties.login")).isEqualTo("erin");

        // not reserved by the member, so hidden from them afterwards (as an id that doesn't exist: no rows)
        ApiResponse read = member.get("/api/bucket/secrets/" + inserted.<Number>path("id"), call().v2().expect(200));
        assertThat(read.<List<?>>path("$")).isEmpty();
    }

    @Test
    @DisplayName("finds rows by JsonLogic rules with paging, sorting and counting")
    void finds_rows(TestProject project) {
        Api robot = project.as(ROBOT);
        robot.post(DATA + "/multi", call().v2().body(arr(
                obj("properties", obj("customer", "Alice", "status", "NEW", "total", 10)),
                obj("properties", obj("customer", "Bob", "status", "PAID", "total", 20)),
                obj("properties", obj("customer", "Carol", "status", "NEW", "total", 30)),
                obj("properties", obj("customer", "Dave", "status", "NEW", "total", 40))
        )).expect(201));
        Map<String, Object> newOrders = obj("logic", obj("==", arr(obj("var", "$.status"), "NEW")));

        ApiResponse count = robot.post(DATA + "/get?limit=0", call().name("Count new orders").v2().body(newOrders).expect(200));
        assertThat(count.<Integer>path("total")).isEqualTo(3);

        ApiResponse page = robot.post(DATA + "/get?limit=2&page=1&sort=desc(" + URLEncoder.encode("$.total", UTF_8) + ")",
                call().name("GET data by rules").v2().body(newOrders).expect(200));
        assertThat(page.<List<String>>path("data.properties.customer")).containsExactly("Dave", "Carol");
    }

    @Test
    void reserves_free_rows_only_once(TestProject project) {
        Api robot = project.as(ROBOT);
        robot.post(DATA + "/multi", call().v2()
                .body(arr(obj("properties", obj("customer", "Alice")), obj("properties", obj("customer", "Bob"))))
                .expect(201));

        ApiResponse first = robot.post(DATA + "/reserve?limit=1", call().v2().body(obj()).expect(200));
        ApiResponse second = robot.post(DATA + "/reserve?limit=5", call().v2().body(obj()).expect(200));
        ApiResponse third = robot.post(DATA + "/reserve?limit=1", call().v2().body(obj()));

        assertThat(first.<List<?>>path("data")).hasSize(1);
        assertThat(second.<List<?>>path("data")).hasSize(1);
        assertThat(second.<Integer>path("data[0].id")).isNotEqualTo(first.<Integer>path("data[0].id"));
        List<Object> reserved = new ArrayList<>(first.<List<Object>>path("data.reserved"));
        reserved.addAll(second.path("data.reserved"));
        assertThat(reserved).containsOnly(true);
        List<Object> owners = new ArrayList<>(first.<List<Object>>path("data.owner"));
        owners.addAll(second.path("data.owner"));
        assertThat(owners).containsOnly(project.user(ROBOT).username());
        List<?> none = third.path("data");
        assertThat(none == null ? List.of() : none).isEmpty();
    }

    @Test
    @DisplayName("updates one property, keeping the others, and deletes rows")
    void updates_and_deletes(TestProject project) {
        Api member = project.as(MEMBER);
        ApiResponse row = member.post(DATA, call().v2().body(obj("properties", obj("status", "NEW", "customer", "Alice"))).expect(201));
        String rowPath = DATA + "/" + row.<Number>path("id");

        member.put(rowPath, call().v2().body(obj("propertiesToSet", obj("$.status", "PAID"))).expect(200));
        ApiResponse updated = member.get(rowPath, call().v2().expect(200));
        assertThat(updated.<Map<String, Object>>path("properties")).isEqualTo(Map.of("status", "PAID", "customer", "Alice"));

        member.delete(rowPath, call().v2().expect(200));
        // a row that doesn't exist (any more) comes back as an empty list
        ApiResponse deleted = member.get(rowPath, call().v2().expect(200));
        assertThat(deleted.<List<?>>path("$")).isEmpty();
    }
}
