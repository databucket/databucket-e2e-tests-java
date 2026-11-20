package pl.databucket.e2e.model;

import java.time.Instant;

/**
 * A data row of a bucket, with its properties read as {@code T}: a record of the test ({@code DataRow<Person>}) or a
 * {@code Map} for properties of any shape.
 */
public record DataRow<T>(long id, Long tagId, boolean reserved, String owner, T properties,
                         String createdBy, Instant createdAt, String modifiedBy, Instant modifiedAt) {
}
