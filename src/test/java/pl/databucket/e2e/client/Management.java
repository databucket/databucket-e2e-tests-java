package pl.databucket.e2e.client;

import com.fasterxml.jackson.databind.JsonNode;
import pl.databucket.e2e.model.DatasetInfo;
import pl.databucket.e2e.model.ManagedProject;
import pl.databucket.e2e.model.ManagedUser;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.Json;

import java.util.List;
import java.util.Map;

import static pl.databucket.e2e.support.Call.call;

/** The management of all projects and users (SUPER). */
public final class Management {

    private final Api api;

    public Management(Api api) {
        this.api = api;
    }

    public Resource<ManagedProject> projects() {
        return new Resource<>(api, "/api/manage/projects", ManagedProject.class);
    }

    public Resource<ManagedUser> users() {
        return new Resource<>(api, "/api/manage/users", ManagedUser.class);
    }

    /** The number of objects of each kind in the project ("Buckets", "Data rows"...). */
    public Map<String, Integer> content(int projectId) {
        return api.get("/api/manage/projects/" + projectId + "/content", call().expect(200))
                .as(Json.MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Integer.class));
    }

    public ApiResponse tryContent(int projectId) {
        return api.get("/api/manage/projects/" + projectId + "/content");
    }

    /** Removes everything in the project except the project and its users. */
    public void clear(int projectId) {
        api.post("/api/manage/projects/" + projectId + "/clear", call().expect(204));
    }

    public List<DatasetInfo> datasets() {
        return api.get("/api/manage/projects/datasets", call().expect(200)).asListOf(DatasetInfo.class);
    }

    /** The project as a dataset (JSON), to import into another, empty project. */
    public JsonNode export(int projectId) {
        return Json.parseOrNull(api.get("/api/manage/projects/" + projectId + "/dataset", call().expect(200)).text());
    }

    /** Imports a dataset file into an empty project; the answer has the number of rows. */
    public ApiResponse importDataset(int projectId, Object dataset) {
        return api.post("/api/manage/projects/" + projectId + "/dataset", call().body(dataset));
    }

    public ApiResponse resetPassword(String username, String password) {
        return api.post("/api/manage/users/password/reset", call().body(Map.of("username", username, "password", password)));
    }
}
