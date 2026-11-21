package pl.databucket.e2e.client;

import com.fasterxml.jackson.databind.JavaType;
import pl.databucket.e2e.model.DataPage;
import pl.databucket.e2e.model.DataRow;
import pl.databucket.e2e.model.Generated;
import pl.databucket.e2e.model.HistoryPage;
import pl.databucket.e2e.model.HistoryVersion;
import pl.databucket.e2e.model.Message;
import pl.databucket.e2e.model.NewRow;
import pl.databucket.e2e.model.Reservation;
import pl.databucket.e2e.model.RowHistory;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.Json;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static pl.databucket.e2e.support.Call.call;

/**
 * The data of one bucket as a user sees it, with the properties of the rows read as {@code T}:
 * {@code new BucketApi<>(robot, "people", Person.class).insert(new Person("Anna", 34))}. The methods expect success;
 * the {@code try...} ones return the response as it is, for the tests of refusals.
 */
public final class BucketApi<T> {

    private final Api api;
    private final String path;
    private final Class<T> type;

    public BucketApi(Api api, String bucket, Class<T> type) {
        this.api = api;
        this.path = "/api/bucket/" + bucket;
        this.type = type;
    }

    // ---------------------------------------------------------------- inserting

    public DataRow<T> insert(T properties) {
        return insert(NewRow.of(properties));
    }

    public DataRow<T> insert(NewRow<T> row) {
        return api.post(path, call().body(row).expect(201)).as(rowType());
    }

    public ApiResponse tryInsert(NewRow<?> row) {
        return api.post(path, call().body(row));
    }

    /** Many rows in one call (all or none). */
    @SafeVarargs
    public final void insertAll(T... properties) {
        insertRows(Arrays.stream(properties).map(NewRow::of).toList());
    }

    public void insertAll(List<T> properties) {
        insertRows(properties.stream().map(NewRow::of).toList());
    }

    public void insertRows(List<NewRow<T>> rows) {
        api.post(path + "/multi", call().body(rows).expect(201));
    }

    public ApiResponse tryInsertRows(List<? extends NewRow<?>> rows) {
        return api.post(path + "/multi", call().body(rows));
    }

    // ---------------------------------------------------------------- generating

    /**
     * Rows generated on the server: {@code request} = {@code fields} (property path -> generator), {@code count} and
     * optionally {@code tagId}, {@code reserved}, {@code locale}, {@code seed}, {@code startN}.
     */
    public Generated generate(Map<String, ?> request) {
        return api.post(path + "/generate", call().body(request).expect(201)).as(Generated.class);
    }

    /** The first rows (at most 10) that {@link #generate} would insert; nothing is inserted. */
    public Generated previewGenerated(Map<String, ?> request) {
        return api.post(path + "/generate?preview=true", call().body(request).expect(200)).as(Generated.class);
    }

    public ApiResponse tryGenerate(Map<String, ?> request) {
        return api.post(path + "/generate", call().body(request));
    }

    // ---------------------------------------------------------------- reading

    /** The row, or empty when it doesn't exist or the user may not see it. */
    public Optional<DataRow<T>> get(long id) {
        List<DataRow<T>> rows = getAll(id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** The rows with these ids (those that don't exist are skipped). */
    public List<DataRow<T>> getAll(long... ids) {
        ApiResponse response = api.get(path + "/" + ids(ids), call().expect(200));
        // one row that exists comes as an object, otherwise a list
        if (response.text().trim().startsWith("["))
            return response.as(Json.listOf(rowType()));
        DataRow<T> row = response.as(rowType());
        return List.of(row);
    }

    public DataPage<T> find(Search search) {
        return api.post(path + "/get" + search.query(), call().body(search).expect(200)).as(Json.type(DataPage.class, type));
    }

    public ApiResponse tryFind(Search search) {
        return api.post(path + "/get" + search.query(), call().body(search));
    }

    /** The properties of all the rows matching the search, in the order of the ids (up to 1000). */
    public List<T> properties(Search search) {
        return find(search.limit(1000)).properties();
    }

    /** The number of rows matching the search. */
    public long count(Search search) {
        return find(search.limit(0)).total();
    }

    // ---------------------------------------------------------------- changing

    public Message update(Change change, long... ids) {
        return api.put(path + "/" + ids(ids), call().body(change).expect(200)).as(Message.class);
    }

    /** Changes the rows matching the rules of the change. */
    public Message updateWhere(Change change) {
        return api.put(path, call().body(change).expect(200)).as(Message.class);
    }

    public ApiResponse tryUpdateWhere(Change change) {
        return api.put(path, call().body(change));
    }

    public Message release(long... ids) {
        return update(Change.reserve(false), ids);
    }

    public Message delete(long... ids) {
        return api.delete(path + "/" + ids(ids), call().expect(200)).as(Message.class);
    }

    public ApiResponse tryDelete(long... ids) {
        return api.delete(path + "/" + ids(ids));
    }

    public Message deleteWhere(Search search) {
        return api.delete(path, call().body(search).expect(200)).as(Message.class);
    }

    public ApiResponse tryDeleteWhere(Search search) {
        return api.delete(path, call().body(search));
    }

    // ---------------------------------------------------------------- reserving

    /** Reserves free rows matching the search (limit 1 by default; limit 0 only counts). */
    public Reservation<T> reserve(Search search) {
        return api.post(path + "/reserve" + search.query(), call().body(search).expect(200))
                .as(Json.type(Reservation.class, type));
    }

    public ApiResponse tryReserve(Search search) {
        return api.post(path + "/reserve" + search.query(), call().body(search));
    }

    // ---------------------------------------------------------------- history

    public List<RowHistory> history(long id) {
        return api.get(path + "/" + id + "/history", call().expect(200)).asListOf(RowHistory.class);
    }

    public List<HistoryVersion<T>> versions(long id, List<Long> entries) {
        String ids = entries.stream().map(String::valueOf).collect(Collectors.joining(","));
        return api.get(path + "/" + id + "/history/" + ids, call().expect(200))
                .as(Json.listOf(Json.type(HistoryVersion.class, type)));
    }

    public HistoryPage<T> searchHistory(Search search) {
        return api.post(path + "/history/get" + search.query(), call().body(search).expect(200))
                .as(Json.type(HistoryPage.class, type));
    }

    public void clearHistory(long id) {
        api.delete(path + "/" + id + "/history/clear", call().expect(200));
    }

    public ApiResponse tryClearHistory(long id) {
        return api.delete(path + "/" + id + "/history/clear");
    }

    public void clearHistoryWhere(Search search) {
        api.delete(path + "/history/clear", call().body(search).expect(200));
    }

    public ApiResponse tryClearHistoryWhere(Search search) {
        return api.delete(path + "/history/clear", call().body(search));
    }

    // ---------------------------------------------------------------- helpers

    private JavaType rowType() {
        return Json.type(DataRow.class, type);
    }

    private static String ids(long... ids) {
        return LongStream.of(ids).mapToObj(String::valueOf).collect(Collectors.joining(","));
    }
}
