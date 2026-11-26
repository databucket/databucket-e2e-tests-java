package pl.databucket.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The short title of an API call in the report, e.g. "Find rows in orders" for {@code POST /api/bucket/orders/get};
 * the method, the path and the status are in the step under it. {@code call().name("...")} gives a title of its own.
 */
final class CallTitle {

    // the settings of a project: path -> the name of one item
    private static final Map<String, String> SETTINGS = Map.ofEntries(
            Map.entry("buckets", "bucket"), Map.entry("classes", "class"), Map.entry("columns", "columns"),
            Map.entry("enums", "enum"), Map.entry("filters", "filter"), Map.entry("groups", "group"),
            Map.entry("tags", "tag"), Map.entry("tasks", "task"), Map.entry("teams", "team"),
            Map.entry("views", "view"), Map.entry("svg", "icon"), Map.entry("users", "user of the project"),
            Map.entry("task-schedules", "job"));

    private static final Pattern BUCKET = Pattern.compile("/api/bucket/([^/]+)(/.*)?");
    private static final Pattern SETTING = Pattern.compile("/api/([a-z-]+)(?:/(\\d+))?");
    private static final Pattern MANAGE = Pattern.compile("/api/manage/(projects|users)(?:/(\\d+))?(/.*)?");

    private CallTitle() {
    }

    static String of(String method, String pathAndQuery, String body) {
        String path = pathAndQuery.split("\\?", 2)[0];
        String query = pathAndQuery.contains("?") ? pathAndQuery.split("\\?", 2)[1] : "";
        String title = title(method, path, body);
        return query.contains("preview=true") ? title + " (preview)" : title;
    }

    private static String title(String method, String path, String body) {
        switch (path) {
            case "/api/public/signin":
                return "Sign in";
            case "/mcp":
                return mcp(body);
            case "/api/users/password/change":
                return "Change the password";
            case "/api/session/access-tree":
                return "Get the access tree";
            case "/api/task-schedules/run":
                return "Run the jobs of a keyword";
            case "/api/activity":
                return "Get the activity of the project";
            case "/api/manage/audit":
                return "Get the audit log";
            case "/api/manage/users/password/reset":
                return "Reset the password of a user";
            case "/api/users/change-project":
                return "Change the project";
            case "/api/roles":
                return "Get the roles";
            default:
                break;
        }
        Matcher session = Pattern.compile("/api/session/([a-z-]+)/(\\d+)").matcher(path);
        if (session.matches()) return "Get " + session.group(1) + " " + session.group(2);
        Matcher bucket = BUCKET.matcher(path);
        if (bucket.matches()) return bucket(method, bucket.group(1), bucket.group(2) == null ? "" : bucket.group(2));
        Matcher manage = MANAGE.matcher(path);
        if (manage.matches()) return manage(method, manage.group(1), manage.group(2), manage.group(3));
        Matcher jobRuns = Pattern.compile("/api/task-schedules/(\\d+)/(runs|run)").matcher(path);
        if (jobRuns.matches()) return jobRuns.group(2).equals("runs") ? "Get the runs of job " + jobRuns.group(1)
                : "Run job " + jobRuns.group(1);
        Matcher setting = SETTING.matcher(path);
        if (setting.matches() && SETTINGS.containsKey(setting.group(1))) {
            String item = SETTINGS.get(setting.group(1));
            String id = setting.group(2);
            return switch (method) {
                case "GET" -> id == null ? "Get the " + plural(setting.group(1)) : "Get " + item + " " + id;
                case "POST" -> "Create " + article(item) + item;
                case "PUT" -> "Change " + article(item) + item;
                case "DELETE" -> "Delete " + item + (id == null ? "" : " " + id);
                default -> method + " " + path;
            };
        }
        return method + " " + path;
    }

    private static String bucket(String method, String bucket, String rest) {
        if (rest.isEmpty() || rest.equals("/"))
            return switch (method) {
                case "POST" -> "Insert a row into " + bucket;
                case "PATCH" -> "Change rows of " + bucket + " by rules";
                case "PUT" -> "Change rows of " + bucket + " by rules (PUT)";
                case "DELETE" -> "Delete rows of " + bucket + " by rules";
                default -> method + " rows of " + bucket;
            };
        switch (rest) {
            case "/multi":
                return "Insert rows into " + bucket;
            case "/get":
                return "Find rows in " + bucket;
            case "/reserve":
                return "Reserve rows in " + bucket;
            case "/generate":
                return "Generate rows in " + bucket;
            case "/history/get":
                return "Search the history of " + bucket;
            case "/history/clear":
                return "Clear the history of " + bucket + " by rules";
            default:
                break;
        }
        Matcher tasks = Pattern.compile("/tasks/(\\d+)/run").matcher(rest);
        if (tasks.matches()) return "Run task " + tasks.group(1) + " on " + bucket;
        Matcher history = Pattern.compile("/([\\d,]+)/history(/.*)?").matcher(rest);
        if (history.matches()) {
            String more = history.group(2) == null ? "" : history.group(2);
            if (more.equals("/clear")) return "Clear the history of row " + history.group(1) + " of " + bucket;
            if (more.isEmpty()) return "Get the history of row " + history.group(1) + " of " + bucket;
            return "Get the history of row " + history.group(1) + " of " + bucket + " (" + more.substring(1) + ")";
        }
        Matcher ids = Pattern.compile("/([\\d,]+)").matcher(rest);
        if (ids.matches()) {
            String rows = (ids.group(1).contains(",") ? "rows " : "row ") + ids.group(1) + " of " + bucket;
            return switch (method) {
                case "GET" -> "Get " + rows;
                case "PATCH" -> "Change " + rows;
                case "PUT" -> "Change " + rows + " (PUT)";
                case "DELETE" -> "Delete " + rows;
                default -> method + " " + rows;
            };
        }
        return method + " /api/bucket/" + bucket + rest;
    }

    private static String manage(String method, String what, String id, String rest) {
        String item = what.equals("projects") ? "project" : "user";
        if (id == null && "/datasets".equals(rest)) return "Get the datasets";
        if (rest != null && id != null)
            return switch (rest) {
                case "/clear" -> "Clear project " + id;
                case "/dataset" -> method.equals("POST") ? "Import a dataset into project " + id
                        : "Export the dataset of project " + id;
                case "/content" -> "Get the content of project " + id;
                default -> method + " /api/manage/" + what + "/" + id + rest;
            };
        return switch (method) {
            case "GET" -> id == null ? "Get the " + what + " (Management)" : "Get " + item + " " + id;
            case "POST" -> "Create a " + item + " (Management)";
            case "PUT" -> "Change a " + item + " (Management)";
            case "DELETE" -> "Delete " + item + (id == null ? "" : " " + id);
            default -> method + " /api/manage/" + what;
        };
    }

    // a JSON-RPC call: the tool it calls
    private static String mcp(String body) {
        JsonNode json = body == null ? null : Json.parseOrNull(body);
        if (json == null) return "MCP";
        String method = json.path("method").asText("");
        if (method.equals("tools/call")) return "MCP: " + json.path("params").path("name").asText("a tool");
        if (method.equals("tools/list")) return "MCP: list the tools";
        return method.isEmpty() ? "MCP" : "MCP: " + method;
    }

    private static String plural(String path) {
        return switch (path) {
            case "svg" -> "icons";
            case "task-schedules" -> "jobs";
            case "users" -> "users of the project";
            default -> path;
        };
    }

    private static String article(String item) {
        if (item.equals("columns")) return "";
        return "aeio".indexOf(item.charAt(0)) >= 0 ? "an " : "a ";
    }
}
