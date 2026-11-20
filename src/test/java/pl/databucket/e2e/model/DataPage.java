package pl.databucket.e2e.model;

import java.util.List;

/** One page of the data rows matching a search; total and totalPages are left out with count=false. */
public record DataPage<T>(Integer page, Integer limit, String sort, Long total, Integer totalPages, List<DataRow<T>> data) {

    /** The rows of the page (none when the page is empty). */
    public List<DataRow<T>> rows() {
        return data == null ? List.of() : data;
    }

    /** The properties of the rows of the page. */
    public List<T> properties() {
        return rows().stream().map(DataRow::properties).toList();
    }
}
