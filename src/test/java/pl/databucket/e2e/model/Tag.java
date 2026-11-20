package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Set;

/** A tag of data rows. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Tag extends Audited {

    public Long id;
    public String name;
    public String description;
    public Set<Long> bucketsIds;
    public Set<Long> classesIds;

    public Tag id(Long id) {
        this.id = id;
        return this;
    }

    public Tag name(String name) {
        this.name = name;
        return this;
    }

    public Tag description(String description) {
        this.description = description;
        return this;
    }

    public Tag bucketsIds(Set<Long> bucketsIds) {
        this.bucketsIds = bucketsIds;
        return this;
    }

    public Tag classesIds(Set<Long> classesIds) {
        this.classesIds = classesIds;
        return this;
    }
}
