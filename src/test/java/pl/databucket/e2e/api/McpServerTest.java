package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.Area;
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
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

/** The MCP server for AI agents (/mcp, stateless streamable HTTP): JSON-RPC calls with the user's token. */
@ApiSuite
@Area(Area.MCP)
@DisplayName("Server")
class McpServerTest {

    private static final String BUCKET = "accounts";
    private static final String MCP_ACCEPT = "application/json, text/event-stream";

    /** The answer of a tool: JSON (read it with {@link #path}), or the message of an error. */
    record ToolResult(boolean isError, String text) {
        <T> T path(String path) {
            return Json.path(text, path);
        }
    }

    static ApiResponse rpc(Api api, String method, Map<String, Object> params, String name) {
        return api.post("/mcp", call()
                .header("Accept", MCP_ACCEPT)
                .body(obj("jsonrpc", "2.0", "id", 1, "method", method, "params", params))
                .expect(200)
                .name(name));
    }

    static ApiResponse rpc(Api api, String method, Map<String, Object> params) {
        return rpc(api, method, params, null);
    }

    static ToolResult callTool(Api api, String tool, Map<String, Object> arguments) {
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
        verifyAll(v -> v
                .that("serverInfo.name", init.<String>path("result.serverInfo.name"), "databucket")
                .check("instructions mention list_buckets", () -> assertThat(init.<String>path("result.instructions")).contains("list_buckets")));

        ApiResponse tools = rpc(agent, "tools/list", obj());
        check("tools", () -> assertThat(tools.<List<String>>path("result.tools.name")).containsExactlyInAnyOrder(
                "create_bucket", "delete_data", "describe_bucket", "find_data", "generate_data", "get_activity", "get_data", "insert_data", "list_buckets",
                "release_data", "reserve_data", "run_jobs", "run_task", "search_history", "update_data"));
    }

    @Test
    void needs_a_token() {
        Api anonymous = new Api("anonymous");

        ApiResponse response = anonymous.post("/mcp", call()
                .header("Accept", MCP_ACCEPT)
                .body(obj("jsonrpc", "2.0", "id", 1, "method", "tools/list")));

        verify("status", response.status(), 401);
    }

    @Test
    @DisplayName("an agent finds, reserves, changes and releases data")
    void agent_works_with_data(TestProject project) {
        Api agent = project.as(AGENT);

        ToolResult buckets = callTool(agent, "list_buckets", obj());
        check("buckets contain " + BUCKET, () -> assertThat(buckets.<List<String>>path("name")).contains(BUCKET));

        ToolResult inserted = callTool(agent, "insert_data", obj("bucket", BUCKET, "rows", arr(
                obj("properties", obj("login", "alice", "status", "active")),
                obj("properties", obj("login", "bob", "status", "active")),
                obj("properties", obj("login", "carol", "status", "blocked")))));
        verifyAll(v -> v
                .that("isError", inserted.isError(), false)
                .that("rows: size", inserted.<List<?>>path("$").size(), 3));

        ToolResult active = callTool(agent, "find_data", obj("bucket", BUCKET,
                "rules", arr(arr("$.status", "==", "active")), "sort", "asc($.login)"));
        verifyAll(v -> v
                .that("total", active.<Integer>path("total"), 2)
                .that("logins", active.<List<String>>path("data.properties.login"), List.of("alice", "bob")));

        ToolResult reserved = callTool(agent, "reserve_data", obj("bucket", BUCKET, "rules", arr(arr("$.status", "==", "active"))));
        Number rowId = reserved.path("data[0].id");
        verifyAll(v -> v
                .that("reserved", reserved.<Integer>path("reserved"), 1)
                .that("available", reserved.<Integer>path("available"), 1)
                .that("data[0].owner", reserved.<String>path("data[0].owner"), project.user(AGENT).username()));

        ToolResult updated = callTool(agent, "update_data", obj("bucket", BUCKET, "ids", arr(rowId), "propertiesToSet", obj("$.status", "used")));
        verify("count", updated.<Integer>path("count"), 1);

        ToolResult released = callTool(agent, "release_data", obj("bucket", BUCKET, "ids", arr(rowId)));
        verify("count", released.<Integer>path("count"), 1);

        ToolResult history = callTool(agent, "search_history", obj("bucket", BUCKET, "rules", arr(arr("id", "==", rowId)), "properties", true));
        // inserted, reserved, changed, released
        verifyAll(v -> v
                .that("total", history.<Integer>path("total"), 4)
                .check("modifiedBy: only the agent", () -> assertThat(history.<List<String>>path("history.modifiedBy"))
                        .containsOnly(project.user(AGENT).username())));
    }

    @Test
    @DisplayName("an agent can not remove data, a robot can")
    void only_a_robot_removes_data(TestProject project) {
        Api agent = project.as(AGENT);
        Api robot = project.as(ROBOT);
        ApiResponse row = robot.post("/api/bucket/" + BUCKET, call().body(obj("properties", obj("login", "dave"))).expect(201));
        Number rowId = row.path("id");

        ToolResult byAgent = callTool(agent, "delete_data", obj("bucket", BUCKET, "ids", arr(rowId)));
        verifyAll(v -> v
                .that("isError", byAgent.isError(), true)
                .check("message names the ROBOT role", () -> assertThat(byAgent.text()).contains("ROBOT role")));
        // the REST API refuses it too
        agent.delete("/api/bucket/" + BUCKET + "/" + rowId, call().expect(403));

        ToolResult byRobot = callTool(robot, "delete_data", obj("bucket", BUCKET, "ids", arr(rowId)));
        verify("count", byRobot.<Integer>path("count"), 1);
    }

    @Test
    void an_agent_inserts_free_rows_into_a_bucket_with_protected_data(TestProject project) {
        project.createBucket("secrets", true, true);
        Api agent = project.as(AGENT);

        ToolResult inserted = callTool(agent, "insert_data", obj("bucket", "secrets", "rows", arr(
                obj("properties", obj("login", "free")),
                obj("properties", obj("login", "mine"), "reserved", true))));
        verifyAll(v -> v
                .that("isError", inserted.isError(), false)
                .that("logins", inserted.<List<String>>path("properties.login"), List.of("free", "mine"))
                .that("reserved", inserted.<List<Boolean>>path("reserved"), List.of(false, true)));

        // afterwards the agent sees only its own row
        ToolResult found = callTool(agent, "find_data", obj("bucket", "secrets"));
        verify("logins", found.<List<String>>path("data.properties.login"), List.of("mine"));
    }

    @Test
    void explains_mistakes_to_the_agent(TestProject project) {
        Api agent = project.as(AGENT);

        ToolResult unknownBucket = callTool(agent, "find_data", obj("bucket", "no-such-bucket"));
        verifyAll(v -> v
                .that("unknown bucket: isError", unknownBucket.isError(), true)
                .check("unknown bucket: points to list_buckets", () -> assertThat(unknownBucket.text()).contains("list_buckets")));

        ToolResult sqlRule = callTool(agent, "find_data", obj("bucket", BUCKET, "rules", arr("properties->>'a' = '1'")));
        verifyAll(v -> v
                .that("SQL rule: isError", sqlRule.isError(), true)
                .check("SQL rule: explains the rule format", () -> assertThat(sqlRule.text()).contains("[left, operator, right]")));

        ToolResult unknownTag = callTool(agent, "insert_data", obj("bucket", BUCKET, "rows", arr(obj("properties", obj("a", 1), "tagId", 999999))));
        verifyAll(v -> v
                .that("unknown tag: isError", unknownTag.isError(), true)
                .check("unknown tag: points to describe_bucket", () -> assertThat(unknownTag.text()).contains("describe_bucket")));

        ToolResult nothing = callTool(agent, "find_data", obj("bucket", BUCKET, "limit", 0));
        verify("total", nothing.<Integer>path("total"), 0);
    }
}
