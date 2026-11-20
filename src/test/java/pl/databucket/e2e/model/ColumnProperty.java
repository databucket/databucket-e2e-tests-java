package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A property shown in columns, filters and tasks. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ColumnProperty {

    public String path;
    public String type;
    public String uuid;
    public String title;
    public Integer enumId;

    public ColumnProperty path(String path) {
        this.path = path;
        return this;
    }

    public ColumnProperty type(String type) {
        this.type = type;
        return this;
    }

    public ColumnProperty uuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    public ColumnProperty title(String title) {
        this.title = title;
        return this;
    }

    public ColumnProperty enumId(Integer enumId) {
        this.enumId = enumId;
        return this;
    }
}
