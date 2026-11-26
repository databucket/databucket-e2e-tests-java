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

/** The MCP tools of 3.21.0 and 3.22.0: running a saved task (run_task) and the activity of the project (get_activity). */
@ApiSuite
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
        assertThat(described.<List<String>>path("tasks.name")).containsExactly("archive used");
        assertThat(described.<List<String>>path("tasks.action")).containsExactly("modify");
        assertThat(described.<List<String>>path("tasks.filter")).containsExactly("status used");

        ToolResult preview = callTool(agent, "run_task", obj("bucket", BUCKET, "task", "archive used", "preview", true));
        assertThat(preview.<Integer>path("count")).isEqualTo(2);
        assertThat(preview.<Boolean>path("preview")).isTrue();
        assertThat(count(agent, "archived")).isZero();

        // by id this time
        ToolResult run = callTool(agent, "run_task", obj("bucket", BUCKET, "task", String.valueOf(task.id)));
        assertThat(run.<Integer>path("count")).isEqualTo(2);
        assertThat(run.<String>path("message")).isEqualTo("Modified 2 data row(s)");
        assertThat(count(agent, "archived")).isEqualTo(2);
        assertThat(count(agent, "active")).isEqualTo(1);
    }

    @Test
    @DisplayName("rules narrow the task; a task without a filter needs them; @now works in them")
    void rules_narrow_the_task(TestProject project) {
        statusTask(project, "close all", null, "closed");
        Api agent = project.as(AGENT);

        ToolResult withoutRules = callTool(agent, "run_task", obj("bucket", BUCKET, "task", "close all"));
        assertThat(withoutRules.isError()).isTrue();
        assertThat(withoutRules.text()).contains("give rules");

        // the rows were created just now: none an hour old, all newer than an hour ago
        ToolResult old = callTool(agent, "run_task", obj("bucket", BUCKET, "task", "close all", "preview", true,
                "rules", arr(arr("createdAt", "<", "@now-1h"))));
        assertThat(old.<Integer>path("count")).isZero();

        ToolResult run = callTool(agent, "run_task", obj("bucket", BUCKET, "task", "close all",
                "rules", arr(arr("$.status", "==", "active"), arr("createdAt", ">", "@now-1h"))));
        assertThat(run.<Integer>path("count")).isEqualTo(1);
        assertThat(count(agent, "closed")).isEqualTo(1);
        assertThat(count(agent, "used")).isEqualTo(2);
    }

    @Test
    @DisplayName("an agent can not run a removing task, a robot can")
    void only_a_robot_runs_a_removing_task(TestProject project) {
        removeTask(project, "used");

        ToolResult byAgent = callTool(project.as(AGENT), "run_task", obj("bucket", BUCKET, "task", "remove used"));
        assertThat(byAgent.isError()).isTrue();
        assertThat(byAgent.text()).contains("ROBOT role");

        ToolResult byRobot = callTool(project.as(ROBOT), "run_task", obj("bucket", BUCKET, "task", "remove used"));
        assertThat(byRobot.<Integer>path("count")).isEqualTo(2);
        assertThat(count(project.as(ROBOT), "used")).isZero();
    }

    @Test
    void explains_an_unknown_task(TestProject project) {
        ToolResult unknown = callTool(project.as(AGENT), "run_task", obj("bucket", BUCKET, "task", "no such task"));

        assertThat(unknown.isError()).isTrue();
        assertThat(unknown.text()).contains("describe_bucket");
    }

    @Test
    @DisplayName("an admin sees who changed the settings, an agent can not")
    void admin_reads_the_activity(TestProject project) {
        statusTask(project, "archive used", "used", "archived");

        ToolResult byAgent = callTool(project.as(AGENT), "get_activity", obj());
        assertThat(byAgent.isError()).isTrue();
        assertThat(byAgent.text()).contains("ADMIN role");

        ToolResult tasks = callTool(project.as(ADMIN), "get_activity", obj("type", "Task", "from", "@now-1h"));
        assertThat(tasks.<Integer>path("total")).isEqualTo(1);
        assertThat(tasks.<String>path("events[0].action")).isEqualTo("CREATE");
        assertThat(tasks.<String>path("events[0].entityName")).isEqualTo("archive used");
        assertThat(tasks.<String>path("events[0].actor")).isEqualTo(project.user(ADMIN).username());
        assertThat(tasks.<String>path("events[0].source")).isEqualTo("API");

        // the bulk insert of the robot, as one operation with its count
        ToolResult data = callTool(project.as(ADMIN), "get_activity", obj("action", "data.create"));
        assertThat(data.<Integer>path("events[0].changes.count")).isEqualTo(3);
        assertThat(data.<String>path("events[0].actor")).isEqualTo(project.user(ROBOT).username());

        ToolResult badDate = callTool(project.as(ADMIN), "get_activity", obj("from", "yesterday"));
        assertThat(badDate.isError()).isTrue();
        assertThat(badDate.text()).contains("@now-1d");
    }
}
