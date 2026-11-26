package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.model.ColumnProperty;
import pl.databucket.e2e.model.Filter;
import pl.databucket.e2e.model.FilterConfig;
import pl.databucket.e2e.model.Task;
import pl.databucket.e2e.model.TaskConfig;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.Role;
import pl.databucket.e2e.support.TestProject;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.api.McpServerTest.ToolResult;
import static pl.databucket.e2e.api.McpServerTest.callTool;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.arr;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.AGENT;
import static pl.databucket.e2e.support.UserKey.ROBOT;

/**
 * Jobs of 3.23.0 (Settings → Jobs, formerly Schedules): a job with a keyword and no cron, the jobs of a keyword run in
 * one call (REST POST /api/task-schedules/run, MCP run_jobs) as the caller, and Run as the caller.
 */
@ApiSuite
@DisplayName("Jobs: running by keyword")
class JobsTest {

    private static final String ORDERS = "orders";
    private static final String SECRET = "secret-orders";
    private static final String KEYWORD = "DEV cleaning";
    private static final ColumnProperty STATUS = new ColumnProperty().path("$.status").title("Status").type("string").uuid("u-status");

    private long ordersId;

    @BeforeEach
    void buckets(TestProject project) {
        ordersId = project.createBucket(ORDERS, true, false);
        insert(project, ORDERS);
    }

    // two used orders and an active one
    private static void insert(TestProject project, String bucket) {
        project.quietly(ADMIN).post("/api/bucket/" + bucket + "/multi", call().body(arr(
                obj("properties", obj("number", 1, "status", "used")),
                obj("properties", obj("number", 2, "status", "used")),
                obj("properties", obj("number", 3, "status", "active")))).expect(201));
    }

    // a task removing the rows with the given status, in the given buckets
    private static Task removeTask(TestProject project, String status, long... bucketIds) {
        Long filterId = project.quietly(ADMIN).settings().filters().create(new Filter().name("status " + status)
                .configuration(new FilterConfig().properties(List.of(STATUS))
                        .logic(Map.of("==", List.of(Map.of("var", "$.status"), status))))).id;
        Set<Long> buckets = new java.util.HashSet<>();
        for (long id : bucketIds)
            buckets.add(id);
        return project.quietly(ADMIN).settings().tasks().create(new Task().name("remove " + status).bucketsIds(buckets)
                .filterId(filterId).configuration(new TaskConfig().properties(List.of(STATUS)).actions(Map.of("type", "remove"))));
    }

    private static long job(TestProject project, String name, Task task, long bucketId, String cron, String keyword) {
        return project.as(ADMIN).post("/api/task-schedules", call().body(obj(
                "name", name, "taskId", task.id, "bucketId", bucketId, "cron", cron, "keyword", keyword,
                "timeZone", "UTC", "enabled", true)).expect(201)).<Number>path("id").longValue();
    }

    private static int total(TestProject project, String bucket) {
        return project.quietly(ADMIN).post("/api/bucket/" + bucket + "/get?limit=0", call().body(obj()).expect(200))
                .<Integer>path("total");
    }

    private static ApiResponse runs(TestProject project, long jobId) {
        return project.as(ADMIN).get("/api/task-schedules/" + jobId + "/runs", call().expect(200));
    }

    @Test
    @DisplayName("a job without a cron, with a keyword, is saved and never planned")
    void job_without_cron(TestProject project) {
        Task task = removeTask(project, "used", ordersId);

        long id = job(project, "clean orders", task, ordersId, "", "  DEV   cleaning ");

        ApiResponse jobs = project.as(ADMIN).get("/api/task-schedules", call().expect(200));
        assertThat(jobs.<List<Number>>path("id")).extracting(Number::longValue).containsExactly(id);
        assertThat(jobs.<List<String>>path("keyword")).containsExactly(KEYWORD);
        assertThat(jobs.<List<Object>>path("cron")).containsExactly((Object) null);
        assertThat(jobs.<List<Object>>path("nextRunAt")).containsExactly((Object) null);
    }

    @Test
    @DisplayName("a robot previews and runs the jobs of a keyword, by id, as itself; a failed job doesn't stop the next one")
    void robot_runs_the_jobs_of_a_keyword(TestProject project) {
        // a bucket only for admins: the robot has no access to it, so its job fails when the robot runs it
        long secretId = project.quietly(ADMIN).post("/api/buckets", call().body(obj("name", SECRET,
                "icon", obj("name", "Storage"), "history", false, "protectedData", false, "roleId", Role.ADMIN.id))
                .expect(201)).<Number>path("id").longValue();
        insert(project, SECRET);
        Task task = removeTask(project, "used", ordersId, secretId);
        long secretJob = job(project, "clean secret", task, secretId, null, KEYWORD);
        long ordersJob = job(project, "clean orders", task, ordersId, null, "dev cleaning");
        job(project, "other", task, ordersId, null, "nightly");

        ApiResponse preview = project.as(ROBOT).post("/api/task-schedules/run?preview=true",
                call().body(obj("keyword", "DEV CLEANING")).expect(200));
        assertThat(preview.<Boolean>path("preview")).isTrue();
        assertThat(preview.<List<Number>>path("jobs.id")).extracting(Number::longValue).containsExactly(secretJob, ordersJob);
        assertThat(preview.<List<String>>path("jobs.status")).containsExactly("ERROR", "OK");
        assertThat(preview.<Integer>path("jobs[1].count")).isEqualTo(2);
        assertThat(total(project, ORDERS)).isEqualTo(3);

        ApiResponse run = project.as(ROBOT).post("/api/task-schedules/run", call().body(obj("keyword", KEYWORD)).expect(200));
        assertThat(run.<Boolean>path("preview")).isFalse();
        assertThat(run.<Integer>path("ok")).isEqualTo(1);
        assertThat(run.<Integer>path("errors")).isEqualTo(1);
        assertThat(run.<List<String>>path("jobs.name")).containsExactly("clean secret", "clean orders");
        assertThat(run.<List<String>>path("jobs.bucket")).containsExactly(SECRET, ORDERS);
        assertThat(run.<List<String>>path("jobs.status")).containsExactly("ERROR", "OK");
        assertThat(run.<Integer>path("jobs[1].count")).isEqualTo(2);
        assertThat(total(project, ORDERS)).isEqualTo(1);
        assertThat(total(project, SECRET)).isEqualTo(3);

        // the history: the keyword run, as the robot
        ApiResponse history = runs(project, ordersJob);
        assertThat(history.<List<String>>path("trigger")).containsExactly("KEYWORD");
        assertThat(history.<List<String>>path("runAs")).containsExactly(project.user(ROBOT).username());
        assertThat(runs(project, secretJob).<List<String>>path("status")).containsExactly("ERROR");

        // a failed keyword run doesn't count towards disabling the job
        ApiResponse jobs = project.as(ADMIN).get("/api/task-schedules", call().expect(200));
        assertThat(jobs.<List<Integer>>path("failures")).containsOnly(0);
        assertThat(jobs.<List<Boolean>>path("enabled")).containsOnly(true);
    }

    @Test
    @DisplayName("an unknown keyword lists the keywords; no keyword is refused; an agent can not run jobs")
    void refused_calls(TestProject project) {
        Task task = removeTask(project, "used", ordersId);
        job(project, "clean orders", task, ordersId, null, KEYWORD);
        job(project, "other", task, ordersId, null, "nightly");

        ApiResponse unknown = project.as(ROBOT).post("/api/task-schedules/run", call().body(obj("keyword", "dev")).expect(404));
        assertThat(unknown.text()).contains("'DEV cleaning', 'nightly'");

        project.as(ROBOT).post("/api/task-schedules/run", call().body(obj("keyword", " ")).expect(400));
        project.as(AGENT).post("/api/task-schedules/run", call().body(obj("keyword", KEYWORD)).expect(403));
        assertThat(total(project, ORDERS)).isEqualTo(3);
    }

    @Test
    @DisplayName("Run runs the job as the caller, also a disabled one")
    void run_as_the_caller(TestProject project) {
        Task task = removeTask(project, "used", ordersId);
        long id = job(project, "clean orders", task, ordersId, "0 2 * * *", null);
        project.as(ADMIN).put("/api/task-schedules", call().body(obj("id", id, "name", "clean orders", "taskId", task.id,
                "bucketId", ordersId, "cron", "0 2 * * *", "timeZone", "UTC", "enabled", false)).expect(200));

        ApiResponse ran = project.as(ADMIN).post("/api/task-schedules/" + id + "/run", call().body(obj()).expect(200));

        assertThat(ran.<String>path("lastStatus")).isEqualTo("OK");
        assertThat(ran.<Integer>path("lastCount")).isEqualTo(2);
        ApiResponse history = runs(project, id);
        assertThat(history.<List<String>>path("trigger")).containsExactly("MANUAL");
        assertThat(history.<List<String>>path("runAs")).containsExactly(project.user(ADMIN).username());
    }

    @Test
    @DisplayName("an agent's MCP client with a robot token runs the jobs of a keyword with run_jobs")
    void mcp_run_jobs(TestProject project) {
        Task task = removeTask(project, "used", ordersId);
        job(project, "clean orders", task, ordersId, null, KEYWORD);

        ToolResult byAgent = callTool(project.as(AGENT), "run_jobs", obj("keyword", KEYWORD));
        assertThat(byAgent.isError()).isTrue();
        assertThat(byAgent.text()).contains("ROBOT role");

        ToolResult unknown = callTool(project.as(ROBOT), "run_jobs", obj("keyword", "nope"));
        assertThat(unknown.isError()).isTrue();
        assertThat(unknown.text()).contains("'DEV cleaning'");

        ToolResult preview = callTool(project.as(ROBOT), "run_jobs", obj("keyword", KEYWORD, "preview", true));
        assertThat(preview.<Integer>path("jobs[0].count")).isEqualTo(2);
        assertThat(total(project, ORDERS)).isEqualTo(3);

        ToolResult run = callTool(project.as(ROBOT), "run_jobs", obj("keyword", KEYWORD));
        assertThat(run.<Integer>path("ok")).isEqualTo(1);
        assertThat(total(project, ORDERS)).isEqualTo(1);
    }
}
