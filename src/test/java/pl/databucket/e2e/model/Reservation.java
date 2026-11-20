package pl.databucket.e2e.model;

import java.util.List;

/** The answer to a reservation: the rows reserved, and the free rows still matching (unless count=false). */
public record Reservation<T>(Integer limit, String sort, int reserved, Long available, String message, List<DataRow<T>> data) {

    public List<DataRow<T>> rows() {
        return data == null ? List.of() : data;
    }

    public List<Long> ids() {
        return rows().stream().map(DataRow::id).toList();
    }
}
