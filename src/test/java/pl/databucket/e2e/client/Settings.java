package pl.databucket.e2e.client;

import pl.databucket.e2e.model.AccessTree;
import pl.databucket.e2e.model.Bucket;
import pl.databucket.e2e.model.Columns;
import pl.databucket.e2e.model.DataClass;
import pl.databucket.e2e.model.DataEnum;
import pl.databucket.e2e.model.Filter;
import pl.databucket.e2e.model.Group;
import pl.databucket.e2e.model.ProjectUser;
import pl.databucket.e2e.model.RoleInfo;
import pl.databucket.e2e.model.Svg;
import pl.databucket.e2e.model.Tag;
import pl.databucket.e2e.model.Task;
import pl.databucket.e2e.model.Team;
import pl.databucket.e2e.model.View;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static pl.databucket.e2e.support.Call.call;

/** The settings of the project of the user (ADMIN), and what the web application reads for a MEMBER. */
public final class Settings {

    private final Api api;

    public Settings(Api api) {
        this.api = api;
    }

    public Resource<Bucket> buckets() {
        return new Resource<>(api, "/api/buckets", Bucket.class);
    }

    public Resource<DataClass> classes() {
        return new Resource<>(api, "/api/classes", DataClass.class);
    }

    public Resource<DataEnum> enums() {
        return new Resource<>(api, "/api/enums", DataEnum.class);
    }

    public Resource<Columns> columns() {
        return new Resource<>(api, "/api/columns", Columns.class);
    }

    public Resource<Filter> filters() {
        return new Resource<>(api, "/api/filters", Filter.class);
    }

    public Resource<Group> groups() {
        return new Resource<>(api, "/api/groups", Group.class);
    }

    public Resource<Tag> tags() {
        return new Resource<>(api, "/api/tags", Tag.class);
    }

    public Resource<Task> tasks() {
        return new Resource<>(api, "/api/tasks", Task.class);
    }

    public Resource<Team> teams() {
        return new Resource<>(api, "/api/teams", Team.class);
    }

    public Resource<View> views() {
        return new Resource<>(api, "/api/views", View.class);
    }

    public Resource<Svg> icons() {
        return new Resource<>(api, "/api/svg", Svg.class);
    }

    /** The users of the project (ADMIN): their teams can be changed. */
    public Resource<ProjectUser> users() {
        return new Resource<>(api, "/api/users", ProjectUser.class);
    }

    public List<RoleInfo> roles() {
        return api.get("/api/roles", call().expect(200)).asListOf(RoleInfo.class);
    }

    /** The projects, groups, buckets and views of the web application (MEMBER and up). */
    public AccessTree accessTree() {
        return api.get("/api/session/access-tree", call().expect(200)).as(AccessTree.class);
    }

    public List<Columns> sessionColumns(long... ids) {
        return api.get("/api/session/columns/" + join(ids), call().expect(200)).asListOf(Columns.class);
    }

    public List<Filter> sessionFilters(long... ids) {
        return api.get("/api/session/filters/" + join(ids), call().expect(200)).asListOf(Filter.class);
    }

    public List<Task> sessionTasks(long... ids) {
        return api.get("/api/session/tasks/" + join(ids), call().expect(200)).asListOf(Task.class);
    }

    public ApiResponse changePassword(String username, String password, String newPassword) {
        return api.post("/api/users/password/change", call().body(Map.of(
                "username", username, "password", password, "newPassword", newPassword)));
    }

    public ApiResponse changeProject(long projectId) {
        return api.post("/api/users/change-project", call().body(Map.of("projectId", projectId)));
    }

    private static String join(long... ids) {
        return LongStream.of(ids).mapToObj(String::valueOf).collect(Collectors.joining(","));
    }
}
