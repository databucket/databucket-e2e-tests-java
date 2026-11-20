package pl.databucket.e2e.client;

import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;

import java.util.List;

import static pl.databucket.e2e.support.Call.call;

/**
 * The objects of one kind in the settings or the management (tags, classes, projects...): create, list, update,
 * delete. The methods expect success; the {@code try...} ones return the response as it is.
 */
public final class Resource<T> {

    private final Api api;
    private final String path;
    private final Class<T> type;

    public Resource(Api api, String path, Class<T> type) {
        this.api = api;
        this.path = path;
        this.type = type;
    }

    public T create(T item) {
        return api.post(path, call().body(item).expect(201)).as(type);
    }

    public ApiResponse tryCreate(Object item) {
        return api.post(path, call().body(item));
    }

    public List<T> list() {
        return api.get(path, call().expect(200)).asListOf(type);
    }

    public ApiResponse tryList() {
        return api.get(path);
    }

    public T update(T item) {
        return api.put(path, call().body(item).expect(200)).as(type);
    }

    public ApiResponse tryUpdate(Object item) {
        return api.put(path, call().body(item));
    }

    public void delete(Object id) {
        api.delete(path + "/" + id, call().expect(204));
    }

    public ApiResponse tryDelete(Object id) {
        return api.delete(path + "/" + id);
    }
}
