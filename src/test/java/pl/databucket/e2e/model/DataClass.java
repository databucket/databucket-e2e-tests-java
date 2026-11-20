package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** A data class: the fields of the data of buckets. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataClass extends Audited {

    public Long id;
    public String name;
    public String description;
    public List<ClassField> configuration;

    public DataClass id(Long id) {
        this.id = id;
        return this;
    }

    public DataClass name(String name) {
        this.name = name;
        return this;
    }

    public DataClass description(String description) {
        this.description = description;
        return this;
    }

    public DataClass configuration(List<ClassField> configuration) {
        this.configuration = configuration;
        return this;
    }
}
