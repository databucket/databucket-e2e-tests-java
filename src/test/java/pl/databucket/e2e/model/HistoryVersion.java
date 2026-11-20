package pl.databucket.e2e.model;

/** The properties of a data row as they were in a history entry. */
public record HistoryVersion<T>(long id, T properties) {
}
