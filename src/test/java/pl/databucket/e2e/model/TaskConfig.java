package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** A task: its properties and actions. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskConfig {

    public List<ColumnProperty> properties;
    public Map<String, Object> actions;

    public TaskConfig properties(List<ColumnProperty> properties) {
        this.properties = properties;
        return this;
    }

    public TaskConfig actions(Map<String, Object> actions) {
        this.actions = actions;
        return this;
    }
}
