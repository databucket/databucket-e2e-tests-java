package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.arr;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.AGENT;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;

@ApiSuite
@DisplayName("History of changes")
class HistoryChangesTest {

    private static final String BUCKET = "orders";
    private static final String DATA = "/api/bucket/" + BUCKET;

    private Api robot;

    @BeforeEach
    void bucket(TestProject project) {
        project.createBucket(BUCKET, true, false);
        robot = project.as(ROBOT);
    }

    private static long insert(Api api, Map<String, Object> properties) {
        return insert(api, properties, BUCKET);
    }

    private static long insert(Api api, Map<String, Object> properties, String bucket) {
        return api.post("/api/bucket/" + bucket, call().body(obj("properties", properties)).expect(201)).<Number>path("id").longValue();
    }

    private static void setStatus(Api api, long id, String status) {
        setStatus(api, id, status, BUCKET);
    }

    private static void setStatus(Api api, long id, String status, String bucket) {
        api.put("/api/bucket/" + bucket + "/" + id, call().body(obj("propertiesToSet", obj("$.status", status))).expect(200));
    }

    private static List<Map<String, Object>> historyOf(Api api, long id) {
        return historyOf(api, id, BUCKET);
    }

    private static List<Map<String, Object>> historyOf(Api api, long id, String bucket) {
        return api.get("/api/bucket/" + bucket + "/" + id + "/history", call().expect(200)).path("$");
    }

    @Test
    void gives_the_properties_of_a_row_as_they_were_in_each_version(TestProject project) {
        long id = insert(robot, obj("status", "NEW"));
        setStatus(robot, id, "PAID");
        setStatus(robot, id, "SHIPPED");
        String entries = historyOf(robot, id).stream().map(entry -> String.valueOf(((Number) entry.get("id")).longValue()))
                .collect(Collectors.joining(","));

        ApiResponse versions = robot.get(DATA + "/" + id + "/history/" + entries, call().expect(200));

        assertThat(versions.<List<String>>path("properties.status")).containsExactly("NEW", "PAID", "SHIPPED");
    }

    @Test
    void records_reservations_and_their_owner(TestProject project) {
        long id = insert(robot, obj("login", "anna"));
        project.quietly(MEMBER).post(DATA + "/reserve?limit=1", call().body(obj()).expect(200));

        List<Map<String, Object>> entries = historyOf(robot, id);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(1)).containsEntry("reserved", true).containsEntry("modified_by", project.user(MEMBER).username());
    }

    @Test
    @DisplayName("a ROBOT clears the history of a row; the row stays")
    void robot_clears(TestProject project) {
        long id = insert(robot, obj("status", "NEW"));
        setStatus(robot, id, "PAID");

        robot.delete(DATA + "/" + id + "/history/clear", call().expect(200));

        assertThat(historyOf(robot, id)).isEmpty();
        assertThat(robot.get(DATA + "/" + id, call().expect(200)).<String>path("properties.status")).isEqualTo("PAID");
    }

    @Test
    @DisplayName("an AGENT can not clear the history")
    void agent_can_not_clear(TestProject project) {
        long id = insert(robot, obj("status", "NEW"));
        Api agent = project.as(AGENT);

        ApiResponse one = agent.delete(DATA + "/" + id + "/history/clear");
        ApiResponse byRules = agent.delete(DATA + "/history/clear", call().body(obj("rules", arr(arr("id", "==", id)))));

        assertThat(one.status()).isEqualTo(403);
        assertThat(byRules.status()).isEqualTo(403);
        assertThat(historyOf(robot, id)).hasSize(1);
    }

    @Test
    void clears_the_history_of_the_rows_matching_the_rules_only(TestProject project) {
        long paid = insert(robot, obj("status", "NEW", "customer", "Alice"));
        long other = insert(robot, obj("status", "NEW", "customer", "Bob"));
        setStatus(robot, paid, "PAID");
        setStatus(robot, other, "PAID");

        robot.delete(DATA + "/history/clear", call().body(obj("rules", arr(arr("$.customer", "==", "Alice")))).expect(200));

        assertThat(historyOf(robot, paid)).isEmpty();
        assertThat(historyOf(robot, other)).hasSize(2);
    }

    @Test
    void a_bucket_without_history_keeps_no_entries(TestProject project) {
        project.createBucket("plain", false, false);
        long id = insert(robot, obj("status", "NEW"), "plain");
        setStatus(robot, id, "PAID", "plain");

        assertThat(historyOf(robot, id, "plain")).isEmpty();
    }

    @Test
    void searches_the_entries_by_who_changed_the_rows(TestProject project) {
        long id = insert(robot, obj("status", "NEW"));
        setStatus(project.quietly(MEMBER), id, "PAID");
        setStatus(robot, id, "SHIPPED");
        String member = project.user(MEMBER).username();

        ApiResponse byMember = robot.post(DATA + "/history/get?limit=10", call()
                .body(obj("historyRules", arr(arr("modifiedBy", "==", member)))).expect(200));

        assertThat(byMember.<Integer>path("total")).isEqualTo(1);
        assertThat(byMember.<Number>path("history[0].dataId").longValue()).isEqualTo(id);
        assertThat(byMember.<String>path("history[0].modifiedBy")).isEqualTo(member);
    }

    @Test
    void removing_a_row_removes_its_history(TestProject project) {
        long id = insert(robot, obj("status", "NEW"));
        setStatus(robot, id, "PAID");

        robot.delete(DATA + "/" + id, call().expect(200));

        ApiResponse search = robot.post(DATA + "/history/get?limit=10", call()
                .body(obj("historyRules", arr(arr("dataId", "==", id)))).expect(200));
        assertThat(search.<Integer>path("total")).isZero();
    }
}
