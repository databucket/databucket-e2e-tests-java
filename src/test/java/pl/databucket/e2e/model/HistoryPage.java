package pl.databucket.e2e.model;

import java.time.Instant;
import java.util.List;

/** One page of the history entries found by a history search. */
public record HistoryPage<T>(Integer page, Integer limit, String sort, Long total, Integer totalPages, List<Entry<T>> history) {

    public List<Entry<T>> entries() {
        return history == null ? List.of() : history;
    }

    /** An entry: the data row (dataId), what changed, and with properties=true the properties of that version. */
    public record Entry<T>(long id, long dataId, Long tagId, Boolean reserved, Boolean propertiesChanged, T properties,
                           Instant modifiedAt, String modifiedBy) {
    }
}
