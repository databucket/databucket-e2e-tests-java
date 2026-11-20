package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.Json;
import pl.databucket.e2e.support.TestProject;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.arr;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.AGENT;
import static pl.databucket.e2e.support.UserKey.ROBOT;

/** The MCP server for AI agents (/mcp, stateless streamable HTTP): JSON-RPC calls with the user's token. */
@ApiSuite
@DisplayName("MCP server")
class McpServerTest {

    private static final String BUCKET = "accounts";
    private static final String MCP_ACCEPT = "application/json, text/event-stream";

    /** The answer of a tool: JSON (read it with {@link #path}), or the message of an error. */
    record ToolResult(boolean isError, String text) {
        <T> T path(String path) {
            return Json.path(text, path);
        }
    }

    private static ApiResponse rpc(Api api, String method, Map<String, Object> params, String name) {
        return api.post("/mcp", call()
                .header("Accept", MCP_ACCEPT)
                .body(obj("jsonrpc", "2.0", "id", 1, "method", method, "params", params))
                .expect(200)
                .name(name));
    }

    private static ApiResponse rpc(Api api, String method, Map<String, Object> params) {
        return rpc(api, method, params, null);
    }

    private static ToolResult callTool(Api api, String tool, Map<String, Object> arguments) {
        ApiResponse body = rpc(api, "tools/call", obj("name", tool, "arguments", arguments), "MCP " + tool);
        return new ToolResult(Boolean.TRUE.equals(body.path("result.isError")), body.path("result.content[0].text"));
    }

    @BeforeEach
    void bucket(TestProject project) {
        project.createBucket(BUCKET, true, false);
    }

    @Test
    void introduces_itself_and_lists_its_tools(TestProject project) {
        Api agent = project.as(AGENT);

        ApiResponse init = rpc(agent, "initialize", obj(
                "protocolVersion", "2025-06-18",
                "capabilities", obj(),
                "clientInfo", obj("name", "e2e", "version", "1")));
        assertThat(init.<String>path("result.serverInfo.name")).isEqualTo("databucket");
        assertThat(init.<String>path("result.instructions")).contains("list_buckets");

        ApiResponse tools = rpc(agent, "tools/list", obj());
        assertThat(tools.<List<String>>path("result.tools.name")).containsExactlyInAnyOrder(
                "create_bucket", "delete_data", "describe_bucket", "find_data", "generate_data", "get_data", "insert_data", "list_buckets",
                "release_data", "reserve_data", "search_history", "update_data");
    }

    @Test
    void needs_a_token() {
        Api anonymous = new Api("anonymous");

        ApiResponse response = anonymous.post("/mcp", call()
                .header("Accept", MCP_ACCEPT)
                .body(obj("jsonrpc", "2.0", "id", 1, "method", "tools/list")));

        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    @DisplayName("an agent finds, reserves, changes and releases data")
    void agent_works_with_data(TestProject project) {
        Api agent = project.as(AGENT);

        ToolResult buckets = callTool(agent, "list_buckets", obj());
        assertThat(buckets.<List<String>>path("name")).contains(BUCKET);

        ToolResult inserted = callTool(agent, "insert_data", obj("bucket", BUCKET, "rows", arr(
                obj("properties", obj("login", "alice", "status", "active")),
                obj("properties", obj("login", "bob", "status", "active")),
                obj("properties", obj("login", "carol", "status", "blocked")))));
        assertThat(inserted.isError()).isFalse();
        assertThat(inserted.<List<?>>path("$")).hasSize(3);

        ToolResult active = callTool(agent, "find_data", obj("bucket", BUCKET,
                "rules", arr(arr("$.status", "==", "active")), "sort", "asc($.login)"));
        assertThat(active.<Integer>path("total")).isEqualTo(2);
        assertThat(active.<List<String>>path("data.properties.login")).containsExactly("alice", "bob");

        ToolResult reserved = callTool(agent, "reserve_data", obj("bucket", BUCKET, "rules", arr(arr("$.status", "==", "active"))));
        assertThat(reserved.<Integer>path("reserved")).isEqualTo(1);
        assertThat(reserved.<Integer>path("available")).isEqualTo(1);
        Number rowId = reserved.path("data[0].id");
        assertThat(reserved.<String>path("data[0].owner")).isEqualTo(project.user(AGENT).username());

        ToolResult updated = callTool(agent, "update_data", obj("bucket", BUCKET, "ids", arr(rowId), "propertiesToSet", obj("$.status", "used")));
        assertThat(updated.<Integer>path("count")).isEqualTo(1);

        ToolResult released = callTool(agent, "release_data", obj("bucket", BUCKET, "ids", arr(rowId)));
        assertThat(released.<Integer>path("count")).isEqualTo(1);

        ToolResult history = callTool(agent, "search_history", obj("bucket", BUCKET, "rules", arr(arr("id", "==", rowId)), "properties", true));
        // inserted, reserved, changed, released
        assertThat(history.<Integer>path("total")).isEqualTo(4);
        assertThat(history.<List<String>>path("history.modifiedBy")).containsOnly(project.user(AGENT).username());
    }

    @Test
    @DisplayName("an agent can not remove data, a robot can")
    void only_a_robot_removes_data(TestProject project) {
        Api agent = project.as(AGENT);
        Api robot = project.as(ROBOT);
        ApiResponse row = robot.post("/api/bucket/" + BUCKET, call().v2().body(obj("properties", obj("login", "dave"))).expect(201));
        Number rowId = row.path("id");

        ToolResult byAgent = callTool(agent, "delete_data", obj("bucket", BUCKET, "ids", arr(rowId)));
        assertThat(byAgent.isError()).isTrue();
        assertThat(byAgent.text()).contains("ROBOT role");
        // the REST API refuses it too
        agent.delete("/api/bucket/" + BUCKET + "/" + rowId, call().v2().expect(403));

        ToolResult byRobot = callTool(robot, "delete_data", obj("bucket", BUCKET, "ids", arr(rowId)));
        assertThat(byRobot.<Integer>path("count")).isEqualTo(1);
    }

    @Test
    void an_agent_inserts_free_rows_into_a_bucket_with_protected_data(TestProject project) {
        project.createBucket("secrets", true, true);
        Api agent = project.as(AGENT);

        ToolResult inserted = callTool(agent, "insert_data", obj("bucket", "secrets", "rows", arr(
                obj("properties", obj("login", "free")),
                obj("properties", obj("login", "mine"), "reserved", true))));
        assertThat(inserted.isError()).isFalse();
        assertThat(inserted.<List<String>>path("properties.login")).containsExactly("free", "mine");
        assertThat(inserted.<List<Boolean>>path("reserved")).containsExactly(false, true);

        // afterwards the agent sees only its own row
        ToolResult found = callTool(agent, "find_data", obj("bucket", "secrets"));
        assertThat(found.<List<String>>path("data.properties.login")).containsExactly("mine");
    }

    @Test
    void explains_mistakes_to_the_agent(TestProject project) {
        Api agent = project.as(AGENT);

        ToolResult unknownBucket = callTool(agent, "find_data", obj("bucket", "no-such-bucket"));
        assertThat(unknownBucket.isError()).isTrue();
        assertThat(unknownBucket.text()).contains("list_buckets");

        ToolResult sqlRule = callTool(agent, "find_data", obj("bucket", BUCKET, "rules", arr("properties->>'a' = '1'")));
        assertThat(sqlRule.isError()).isTrue();
        assertThat(sqlRule.text()).contains("[left, operator, right]");

        ToolResult unknownTag = callTool(agent, "insert_data", obj("bucket", BUCKET, "rows", arr(obj("properties", obj("a", 1), "tagId", 999999))));
        assertThat(unknownTag.isError()).isTrue();
        assertThat(unknownTag.text()).contains("describe_bucket");

        ToolResult nothing = callTool(agent, "find_data", obj("bucket", BUCKET, "limit", 0));
        assertThat(nothing.<Integer>path("total")).isEqualTo(0);
    }
}
