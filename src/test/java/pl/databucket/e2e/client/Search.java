package pl.databucket.e2e.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A search of data rows or history entries: the rules (body) and the page, sort and count (query).
 * {@code Search.where(rule("$.status", "==", "NEW")).limit(10).sort("desc(id)")}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class Search {

    public List<Object> rules = new ArrayList<>();
    public Map<String, Object> logic;
    public List<Object> historyRules = new ArrayList<>();
    public String targetOwnerUsername;

    @JsonIgnore
    private Integer limit;
    @JsonIgnore
    private Integer page;
    @JsonIgnore
    private String sort;
    @JsonIgnore
    private Boolean count;
    @JsonIgnore
    private Boolean properties;

    private Search() {
    }

    /** All rows. */
    public static Search all() {
        return new Search();
    }

    /** The rows matching all the rules. */
    public static Search where(Object... rules) {
        Search search = new Search();
        search.rules.addAll(Arrays.asList(rules));
        return search;
    }

    /** History entries matching the rules (fields id, dataId, tagId, reserved, modifiedBy, modifiedAt). */
    public static Search history(Object... historyRules) {
        Search search = new Search();
        search.historyRules.addAll(Arrays.asList(historyRules));
        return search;
    }

    public Search logic(Map<String, Object> logic) {
        this.logic = logic;
        return this;
    }

    public Search limit(int limit) {
        this.limit = limit;
        return this;
    }

    public Search page(int page) {
        this.page = page;
        return this;
    }

    /** e.g. desc(id), asc($.name), random. */
    public Search sort(String sort) {
        this.sort = sort;
        return this;
    }

    public Search count(boolean count) {
        this.count = count;
        return this;
    }

    /** History: with the properties of each version. */
    public Search withProperties() {
        this.properties = true;
        return this;
    }

    /** Reservation by an ADMIN for another user. */
    public Search forOwner(String username) {
        this.targetOwnerUsername = username;
        return this;
    }

    /** The query of the call: "" or "?limit=2&page=1&sort=...". */
    String query() {
        StringJoiner query = new StringJoiner("&", "?", "").setEmptyValue("");
        if (limit != null) query.add("limit=" + limit);
        if (page != null) query.add("page=" + page);
        if (sort != null) query.add("sort=" + URLEncoder.encode(sort, UTF_8));
        if (count != null) query.add("count=" + count);
        if (properties != null) query.add("properties=" + properties);
        return query.toString();
    }
}
