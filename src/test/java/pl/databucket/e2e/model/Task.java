package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Set;

/** A task: changes to apply to the selected rows in the GUI. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Task extends Audited {

    public Long id;
    public String name;
    public String description;
    public Long classId;
    public Long filterId;
    public Set<Long> classesIds;
    public Set<Long> bucketsIds;
    public TaskConfig configuration;

    public Task id(Long id) {
        this.id = id;
        return this;
    }

    public Task name(String name) {
        this.name = name;
        return this;
    }

    public Task description(String description) {
        this.description = description;
        return this;
    }

    public Task classId(Long classId) {
        this.classId = classId;
        return this;
    }

    public Task filterId(Long filterId) {
        this.filterId = filterId;
        return this;
    }

    public Task classesIds(Set<Long> classesIds) {
        this.classesIds = classesIds;
        return this;
    }

    public Task bucketsIds(Set<Long> bucketsIds) {
        this.bucketsIds = bucketsIds;
        return this;
    }

    public Task configuration(TaskConfig configuration) {
        this.configuration = configuration;
        return this;
    }
}
