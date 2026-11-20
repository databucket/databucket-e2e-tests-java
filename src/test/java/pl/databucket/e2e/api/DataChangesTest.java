package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.arr;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.AGENT;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;

@ApiSuite
@DisplayName("Changing data")
class DataChangesTest {

    private static final String BUCKET = "orders";
    private static final String DATA = "/api/bucket/" + BUCKET;

    private Api robot;

    @BeforeEach
    void bucket(TestProject project) {
        project.createBucket(BUCKET);
        robot = project.as(ROBOT);
    }

    private static ApiResponse insert(Api api, Map<String, Object> properties) {
        return api.post(DATA, call().body(obj("properties", properties)).expect(201));
    }

    private static long id(ApiResponse row) {
        return row.<Number>path("id").longValue();
    }

    private static ApiResponse all(Api api) {
        return api.post(DATA + "/get?limit=100", call().body(obj()).expect(200));
    }

    private static List<Object> statuses(Api api) {
        List<Object> statuses = all(api).path("data.properties.status");
        return statuses == null ? List.of() : statuses;
    }

    private static int count(Api api) {
        return all(api).<Integer>path("total");
    }

    private static List<Object> withStatuses(String... statuses) {
        List<Object> rows = new ArrayList<>();
        for (String status : statuses) rows.add(obj("properties", obj("status", status)));
        return rows;
    }

    @Test
    void inserts_many_rows_at_once(TestProject project) {
        List<Object> rows = new ArrayList<>();
        for (int i = 1; i <= 50; i++) rows.add(obj("properties", obj("no", i)));
        robot.post(DATA + "/multi", call().body(rows).expect(201));

        ApiResponse count = robot.post(DATA + "/get?limit=0", call().body(obj()).expect(200));
        assertThat(count.<Integer>path("total")).isEqualTo(50);
    }

    @Test
    void a_row_inserted_as_reserved_belongs_to_the_user(TestProject project) {
        ApiResponse row = robot.post(DATA, call().body(obj("properties", obj("login", "anna"), "reserved", true)).expect(201));

        assertThat(row.<Boolean>path("reserved")).isTrue();
        assertThat(row.<String>path("owner")).isEqualTo(project.user(ROBOT).username());
    }

    @Test
    void a_row_with_a_tag_that_does_not_exist_is_refused(TestProject project) {
        ApiResponse response = robot.post(DATA, call().body(obj("properties", obj("a", 1), "tagId", 999999)));

        assertThat(response.status()).isEqualTo(406);
        assertThat(count(robot)).isZero();
    }

    @Test
    void many_rows_at_once_are_all_saved_or_none(TestProject project) {
        ApiResponse response = robot.post(DATA + "/multi", call().body(arr(
                obj("properties", obj("no", 1)),
                obj("properties", obj("no", 2), "tagId", 999999))));

        assertThat(response.status()).isEqualTo(406);
        assertThat(count(robot)).isZero();
    }

    @Test
    void gets_several_rows_by_ids_and_skips_the_ids_that_do_not_exist(TestProject project) {
        long a = id(insert(robot, obj("name", "a")));
        long b = id(insert(robot, obj("name", "b")));

        ApiResponse response = robot.get(DATA + "/" + a + ",999999," + b, call().expect(200));

        assertThat(response.<List<String>>path("properties.name")).containsExactly("a", "b");
    }

    @Test
    void changes_several_rows_by_ids(TestProject project) {
        long a = id(insert(robot, obj("status", "NEW")));
        insert(robot, obj("status", "NEW"));
        long c = id(insert(robot, obj("status", "NEW")));

        robot.put(DATA + "/" + a + "," + c, call().body(obj("propertiesToSet", obj("$.status", "PAID"))).expect(200));

        assertThat(statuses(robot)).containsExactly("PAID", "NEW", "PAID");
    }

    @Test
    void removes_properties_and_sets_nested_ones(TestProject project) {
        long id = id(insert(robot, obj("customer", "Alice", "comment", "call first", "address", obj("city", "Paris"))));

        robot.put(DATA + "/" + id, call().body(obj(
                "propertiesToRemove", arr("$.comment"),
                "propertiesToSet", obj("$.address.zip", "75001", "$.payment.method", "card"))).expect(200));

        ApiResponse changed = robot.get(DATA + "/" + id, call().expect(200));
        assertThat(changed.<Map<String, Object>>path("properties")).isEqualTo(Map.of(
                "customer", "Alice", "address", Map.of("city", "Paris", "zip", "75001"), "payment", Map.of("method", "card")));
    }

    @Test
    void changes_the_rows_matching_the_rules_and_says_how_many(TestProject project) {
        robot.post(DATA + "/multi", call().body(withStatuses("NEW", "PAID", "NEW")).expect(201));

        ApiResponse response = robot.put(DATA, call().body(obj(
                "rules", arr(arr("$.status", "==", "NEW")),
                "propertiesToSet", obj("$.status", "CANCELLED"))).expect(200));

        assertThat(response.<String>path("message")).isEqualTo("Modified 2 data row(s)");
        assertThat(statuses(robot)).containsExactly("CANCELLED", "PAID", "CANCELLED");
    }

    @Test
    void changing_or_removing_by_rules_needs_at_least_one_rule(TestProject project) {
        insert(robot, obj("status", "NEW"));

        ApiResponse change = robot.put(DATA, call().body(obj("propertiesToSet", obj("$.status", "X"))));
        ApiResponse remove = robot.delete(DATA, call().body(obj()));

        assertThat(change.status()).isEqualTo(406);
        assertThat(remove.status()).isEqualTo(406);
        assertThat(statuses(robot)).containsExactly("NEW");
    }

    @Test
    void removes_the_rows_matching_the_rules(TestProject project) {
        robot.post(DATA + "/multi", call().body(withStatuses("NEW", "PAID", "NEW")).expect(201));

        ApiResponse response = robot.delete(DATA, call().body(obj("rules", arr(arr("$.status", "==", "NEW")))).expect(200));

        assertThat(response.<String>path("message")).isEqualTo("Removed 2 data row(s)");
        assertThat(statuses(robot)).containsExactly("PAID");
    }

    @Test
    @DisplayName("an AGENT changes rows but can not remove them")
    void agent_can_not_remove(TestProject project) {
        Api agent = project.as(AGENT);
        long id = id(insert(agent, obj("status", "NEW")));
        agent.put(DATA + "/" + id, call().body(obj("propertiesToSet", obj("$.status", "PAID"))).expect(200));

        ApiResponse byId = agent.delete(DATA + "/" + id);
        ApiResponse byRules = agent.delete(DATA, call().body(obj("rules", arr(arr("$.status", "==", "PAID")))));

        assertThat(byId.status()).isEqualTo(403);
        assertThat(byRules.status()).isEqualTo(403);
        assertThat(count(robot)).isEqualTo(1);
    }

    @Test
    void a_bucket_that_does_not_exist_gives_404(TestProject project) {
        ApiResponse get = robot.post("/api/bucket/no-such-bucket/get", call().body(obj()));
        ApiResponse insertion = robot.post("/api/bucket/no-such-bucket", call().body(obj("properties", obj())));

        assertThat(get.status()).isEqualTo(404);
        assertThat(insertion.status()).isEqualTo(404);
    }

    @Test
    void sets_and_clears_the_tag_of_a_row(TestProject project) {
        ApiResponse tag = project.quietly(ADMIN).post("/api/tags", call().body(obj("name", "checked")).expect(201));
        long id = id(insert(robot, obj("a", 1)));

        robot.put(DATA + "/" + id, call().body(obj("tagId", tag.path("id"))).expect(200));
        ApiResponse tagged = robot.get(DATA + "/" + id, call().expect(200));

        assertThat(tagged.<Number>path("tagId").longValue()).isEqualTo(tag.<Number>path("id").longValue());
    }

    @Test
    void records_who_changed_a_row_and_when(TestProject project) {
        long id = id(insert(robot, obj("status", "NEW")));
        Api member = project.as(MEMBER);

        member.put(DATA + "/" + id, call().body(obj("propertiesToSet", obj("$.status", "PAID"))).expect(200));
        ApiResponse changed = robot.get(DATA + "/" + id, call().expect(200));

        assertThat(changed.<String>path("createdBy")).isEqualTo(project.user(ROBOT).username());
        assertThat(changed.<String>path("modifiedBy")).isEqualTo(project.user(MEMBER).username());
        assertThat(time(changed.path("modifiedAt"))).isAfterOrEqualTo(time(changed.path("createdAt")));
    }

    private static Instant time(String text) {
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (RuntimeException e) {
            return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
        }
    }
}
