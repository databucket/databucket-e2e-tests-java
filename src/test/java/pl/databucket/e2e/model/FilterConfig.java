package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** A filter: its properties, the JsonLogic and the tree of the query builder. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilterConfig {

    public List<ColumnProperty> properties;
    public Map<String, Object> logic;
    public Map<String, Object> tree;

    public FilterConfig properties(List<ColumnProperty> properties) {
        this.properties = properties;
        return this;
    }

    public FilterConfig logic(Map<String, Object> logic) {
        this.logic = logic;
        return this;
    }

    public FilterConfig tree(Map<String, Object> tree) {
        this.tree = tree;
        return this;
    }
}
