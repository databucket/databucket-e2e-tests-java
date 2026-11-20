package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A property with a database index. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexedProperty {

    public String path;
    public String type;

    public IndexedProperty path(String path) {
        this.path = path;
        return this;
    }

    public IndexedProperty type(String type) {
        this.type = type;
        return this;
    }
}
