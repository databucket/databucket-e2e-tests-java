package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Columns of the data table. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Columns extends Audited {

    public Long id;
    public String name;
    public String description;
    public Long classId;
    public ColumnsConfig configuration;

    public Columns id(Long id) {
        this.id = id;
        return this;
    }

    public Columns name(String name) {
        this.name = name;
        return this;
    }

    public Columns description(String description) {
        this.description = description;
        return this;
    }

    public Columns classId(Long classId) {
        this.classId = classId;
        return this;
    }

    public Columns configuration(ColumnsConfig configuration) {
        this.configuration = configuration;
        return this;
    }
}
