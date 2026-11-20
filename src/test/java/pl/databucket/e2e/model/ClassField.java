package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A field of a data class. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClassField {

    public String title;
    public String path;
    public String type;
    public Integer enumId;
    public String uuid;

    public ClassField title(String title) {
        this.title = title;
        return this;
    }

    public ClassField path(String path) {
        this.path = path;
        return this;
    }

    public ClassField type(String type) {
        this.type = type;
        return this;
    }

    public ClassField enumId(Integer enumId) {
        this.enumId = enumId;
        return this;
    }

    public ClassField uuid(String uuid) {
        this.uuid = uuid;
        return this;
    }
}
