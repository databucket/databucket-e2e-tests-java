package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A saved filter (rich filter of the GUI). Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Filter extends Audited {

    public Long id;
    public String name;
    public String description;
    public Long classId;
    public FilterConfig configuration;

    public Filter id(Long id) {
        this.id = id;
        return this;
    }

    public Filter name(String name) {
        this.name = name;
        return this;
    }

    public Filter description(String description) {
        this.description = description;
        return this;
    }

    public Filter classId(Long classId) {
        this.classId = classId;
        return this;
    }

    public Filter configuration(FilterConfig configuration) {
        this.configuration = configuration;
        return this;
    }
}
