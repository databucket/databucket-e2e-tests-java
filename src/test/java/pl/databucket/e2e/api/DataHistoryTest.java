package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.arr;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.ROBOT;

@ApiSuite
@DisplayName("Data history")
class DataHistoryTest {

    private static final String BUCKET = "orders";
    private static final String DATA = "/api/bucket/" + BUCKET;

    @BeforeEach
    void bucket(TestProject project) {
        project.createBucket(BUCKET, true, false);
    }

    @Test
    void records_the_changes_of_a_row(TestProject project) {
        Api robot = project.as(ROBOT);
        ApiResponse row = robot.post(DATA, call().v2().body(obj("properties", obj("status", "NEW"))).expect(201));
        String rowPath = DATA + "/" + row.<Number>path("id");
        robot.put(rowPath, call().v2().body(obj("propertiesToSet", obj("$.status", "PAID"))).expect(200));

        ApiResponse history = robot.get(rowPath + "/history", call().v2().expect(200));

        assertThat(history.<List<?>>path("$")).hasSize(2);
        assertThat(history.<List<String>>path("modified_by")).containsOnly(project.user(ROBOT).username());
    }

    @Test
    void searches_the_history_of_the_rows_matching_the_rules(TestProject project) {
        Api robot = project.as(ROBOT);
        ApiResponse paid = robot.post(DATA, call().v2().body(obj("properties", obj("status", "NEW", "customer", "Alice"))).expect(201));
        robot.post(DATA, call().v2().body(obj("properties", obj("status", "NEW", "customer", "Bob"))).expect(201));
        long paidId = paid.<Number>path("id").longValue();
        robot.put(DATA + "/" + paidId, call().v2().body(obj("propertiesToSet", obj("$.status", "PAID"))).expect(200));

        ApiResponse search = robot.post(DATA + "/history/get?limit=10&properties=true", call().v2()
                .body(obj("logic", obj("==", arr(obj("var", "$.customer"), "Alice"))))
                .expect(200));

        assertThat(search.<Integer>path("total")).isEqualTo(2);
        assertThat(search.<List<Number>>path("history.dataId")).allMatch(dataId -> dataId.longValue() == paidId);
    }
}
