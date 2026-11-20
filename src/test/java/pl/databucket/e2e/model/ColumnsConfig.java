package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** The columns and the properties they show. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ColumnsConfig {

    public List<Column> columns;
    public List<ColumnProperty> properties;

    public ColumnsConfig columns(List<Column> columns) {
        this.columns = columns;
        return this;
    }

    public ColumnsConfig properties(List<ColumnProperty> properties) {
        this.properties = properties;
        return this;
    }
}
