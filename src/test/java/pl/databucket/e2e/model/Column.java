package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A column of the data table: a field (uuid_data_id, uuid_reserved and so on) or a property (its uuid). Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Column {

    public String uuid;
    public Boolean enabled;
    public Boolean sorting;
    public Boolean filtering;
    public String editable;

    public Column uuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    public Column enabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public Column sorting(Boolean sorting) {
        this.sorting = sorting;
        return this;
    }

    public Column filtering(Boolean filtering) {
        this.filtering = filtering;
        return this;
    }

    public Column editable(String editable) {
        this.editable = editable;
        return this;
    }
}
