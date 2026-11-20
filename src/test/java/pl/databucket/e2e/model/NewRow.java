package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A data row to insert: the properties, and optionally a tag and a reservation for the user. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewRow<T>(T properties, Long tagId, Boolean reserved) {

    public static <T> NewRow<T> of(T properties) {
        return new NewRow<>(properties, null, null);
    }

    public NewRow<T> tagged(long tagId) {
        return new NewRow<>(properties, tagId, reserved);
    }

    public NewRow<T> asReserved() {
        return new NewRow<>(properties, tagId, true);
    }
}
