package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * An entry of the history of one data row: only the items that changed are set; properties is true when they
 * changed (their values: {@link HistoryVersion}).
 */
public record RowHistory(long id,
                         @JsonProperty("tag_id") Long tagId,
                         Boolean reserved,
                         Boolean properties,
                         @JsonProperty("modified_at") Instant modifiedAt,
                         @JsonProperty("modified_by") String modifiedBy) {
}
