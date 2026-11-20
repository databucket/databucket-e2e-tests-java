package pl.databucket.e2e.client;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A change of data rows: {@code Change.set("$.status", "PAID").andRemove("$.comment")}; by rules with
 * {@code .where(rule(...))}. Items left out stay unchanged.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class Change {

    public Map<String, Object> propertiesToSet = new LinkedHashMap<>();
    public List<String> propertiesToRemove = new ArrayList<>();
    public Long tagId;
    public Boolean reserved;
    public List<Object> rules = new ArrayList<>();

    private Change() {
    }

    public static Change set(String path, Object value) {
        return new Change().and(path, value);
    }

    public static Change remove(String... paths) {
        return new Change().andRemove(paths);
    }

    public static Change tag(long tagId) {
        Change change = new Change();
        change.tagId = tagId;
        return change;
    }

    public static Change reserve(boolean reserved) {
        Change change = new Change();
        change.reserved = reserved;
        return change;
    }

    public Change and(String path, Object value) {
        propertiesToSet.put(path, value);
        return this;
    }

    public Change andRemove(String... paths) {
        propertiesToRemove.addAll(Arrays.asList(paths));
        return this;
    }

    /** The rows to change: those matching all the rules. */
    public Change where(Object... rules) {
        this.rules.addAll(Arrays.asList(rules));
        return this;
    }
}
