package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.model.ColumnProperty;
import pl.databucket.e2e.model.Filter;
import pl.databucket.e2e.model.FilterConfig;
import pl.databucket.e2e.model.Task;
import pl.databucket.e2e.model.TaskConfig;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.ApiSuite;
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
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

/** The MCP tools of 3.21.0 and 3.22.0: running a saved task (run_task) and the activity of the project (get_activity). */
@ApiSuite
@Area(Area.MCP)
@DisplayName("MCP server: tasks and activity")
class McpTasksTest {

    private static final String BUCKET = "orders";
    private static final ColumnProperty STATUS = new ColumnProperty().path("$.status").title("Status").type("string").uuid("u-status");

    private long bucketId;

    @BeforeEach
    void bucket(TestProject project) {
        bucketId = project.createBucket(BUCKET, true, false);
        // two used orders and an active one
        project.as(ROBOT).post("/api/bucket/" + BUCKET + "/multi", call().body(arr(
                obj("properties", obj("number", 1, "status", "used")),
                obj("properties", obj("number", 2, "status", "used")),
                obj("properties", obj("number", 3, "status", "active")))).expect(201));
    }

    // a task of the bucket setting $.status to the given value, with a filter on the status (or none)
    private Task statusTask(TestProject project, String name, String from, String to) {
        Long filterId = null;
        if (from != null)
            filterId = project.as(ADMIN).settings().filters().create(new Filter().name("status " + from)
                    .configuration(new FilterConfig().properties(List.of(STATUS))
                            .logic(Map.of("==", List.of(Map.of("var", "$.status"), from))))).id;
        return project.as(ADMIN).settings().tasks().create(new Task().name(name).bucketsIds(Set.of(bucketId))
                .filterId(filterId)
                .configuration(new TaskConfig().properties(List.of(STATUS)).actions(Map.of("type", "modify",
                        "properties", List.of(Map.of("uuid", "u-status", "action", "setValue", "value", to))))));
    }

    private Task removeTask(TestProject project, String status) {
        Long filterId = project.as(ADMIN).settings().filters().create(new Filter().name("status " + status)
                .configuration(new FilterConfig().properties(List.of(STATUS))
                        .logic(Map.of("==", List.of(Map.of("var", "$.status"), status))))).id;
        return project.as(ADMIN).settings().tasks().create(new Task().name("remove " + status).bucketsIds(Set.of(bucketId))
                .filterId(filterId).configuration(new TaskConfig().properties(List.of(STATUS)).actions(Map.of("type", "remove"))));
    }

    private static int count(Api api, String status) {
        return callTool(api, "find_data", obj("bucket", BUCKET, "rules", arr(arr("$.status", "==", status)), "limit", 0))
                .<Integer>path("total");
    }

    @Test
    @DisplayName("an agent sees the tasks of a bucket and runs one, after a preview")
    void agent_runs_a_saved_task(TestProject project) {
        Task task = statusTask(project, "archive used", "used", "archived");
        Api agent = project.as(AGENT);

        ToolResult described = callTool(agent, "describe_bucket", obj("bucket", BUCKET));
        verifyAll(v -> v
                .that("tasks.name", described.<List<String>>path("tasks.name"), List.of("archive used"))
                .that("tasks.action", described.<List<String>>path("tasks.action"), List.of("modify"))
                .that("tasks.filter", described.<List<String>>path("tasks.filter"), List.of("status used")));

        ToolResult preview = callTool(agent, "run_task", obj("bucket", BUCKET, "task", "archive used", "preview", true));
        verifyAll(v -> v
                .that("count", preview.<Integer>path("count"), 2)
                .that("preview", preview.<Boolean>path("preview"), true));
        int archivedBefore = count(agent, "archived");
        verify("archived rows", archivedBefore, 0);

        // by id this time
        ToolResult run = callTool(agent, "run_task", obj("bucket", BUCKET, "task", String.valueOf(task.id)));
        verifyAll(v -> v
                .that("count", run.<Integer>path("count"), 2)
                .that("message", run.<String>path("message"), "Modified 2 data row(s)"));
        int archived = count(agent, "archived");
        int active = count(agent, "active");
        verifyAll(v -> v
                .that("archived rows", archived, 2)
                .that("active rows", active, 1));
    }

    @Test
    @DisplayName("rules narrow the task; a task without a filter needs them; @now works in them")
    void rules_narrow_the_task(TestProject project) {
        statusTask(project, "close all", null, "closed");
        Api agent = project.as(AGENT);

        ToolResult withoutRules = callTool(agent, "run_task", obj("bucket", BUCKET, "task", "close all"));
        verifyAll(v -> v
                .that("isError", withoutRules.isError(), true)
                .check("message: give rules", () -> assertThat(withoutRules.text()).contains("give rules")));

        // the rows were created just now: none an hour old, all newer than an hour ago
        ToolResult old = callTool(agent, "run_task", obj("bucket", BUCKET, "task", "close all", "preview", true,
                "rules", arr(arr("createdAt", "<", "@now-1h"))));
        verify("count", old.<Integer>path("count"), 0);

        ToolResult run = callTool(agent, "run_task", obj("bucket", BUCKET, "task", "close all",
                "rules", arr(arr("$.status", "==", "active"), arr("createdAt", ">", "@now-1h"))));
        verify("count", run.<Integer>path("count"), 1);
        int closed = count(agent, "closed");
        int used = count(agent, "used");
        verifyAll(v -> v
                .that("closed rows", closed, 1)
                .that("used rows", used, 2));
    }

    @Test
    @DisplayName("an agent can not run a removing task, a robot can")
    void only_a_robot_runs_a_removing_task(TestProject project) {
        removeTask(project, "used");

        ToolResult byAgent = callTool(project.as(AGENT), "run_task", obj("bucket", BUCKET, "task", "remove used"));
        verifyAll(v -> v
                .that("isError", byAgent.isError(), true)
                .check("message: ROBOT role", () -> assertThat(byAgent.text()).contains("ROBOT role")));

        ToolResult byRobot = callTool(project.as(ROBOT), "run_task", obj("bucket", BUCKET, "task", "remove used"));
        verify("count", byRobot.<Integer>path("count"), 2);
        int used = count(project.as(ROBOT), "used");
        verify("used rows", used, 0);
    }

    @Test
    void explains_an_unknown_task(TestProject project) {
        ToolResult unknown = callTool(project.as(AGENT), "run_task", obj("bucket", BUCKET, "task", "no such task"));

        verifyAll(v -> v
                .that("isError", unknown.isError(), true)
                .check("message: describe_bucket", () -> assertThat(unknown.text()).contains("describe_bucket")));
    }

    @Test
    @DisplayName("an admin sees who changed the settings, an agent can not")
    void admin_reads_the_activity(TestProject project) {
        statusTask(project, "archive used", "used", "archived");

        ToolResult byAgent = callTool(project.as(AGENT), "get_activity", obj());
        verifyAll(v -> v
                .that("isError", byAgent.isError(), true)
                .check("message: ADMIN role", () -> assertThat(byAgent.text()).contains("ADMIN role")));

        ToolResult tasks = callTool(project.as(ADMIN), "get_activity", obj("type", "Task", "from", "@now-1h"));
        verifyAll(v -> v
                .that("total", tasks.<Integer>path("total"), 1)
                .that("events[0].action", tasks.<String>path("events[0].action"), "CREATE")
                .that("events[0].entityName", tasks.<String>path("events[0].entityName"), "archive used")
                .that("events[0].actor", tasks.<String>path("events[0].actor"), project.user(ADMIN).username())
                .that("events[0].source", tasks.<String>path("events[0].source"), "API"));

        // the bulk insert of the robot, as one operation with its count
        ToolResult data = callTool(project.as(ADMIN), "get_activity", obj("action", "data.create"));
        verifyAll(v -> v
                .that("events[0].changes.count", data.<Integer>path("events[0].changes.count"), 3)
                .that("events[0].actor", data.<String>path("events[0].actor"), project.user(ROBOT).username()));

        ToolResult badDate = callTool(project.as(ADMIN), "get_activity", obj("from", "yesterday"));
        verifyAll(v -> v
                .that("isError", badDate.isError(), true)
                .check("message: @now-1d", () -> assertThat(badDate.text()).contains("@now-1d")));
    }
}
