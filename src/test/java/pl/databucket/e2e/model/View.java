package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Set;

/** A view of the data table in the GUI. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class View extends Audited {

    public Long id;
    public String name;
    public String description;
    public Set<Long> classesIds;
    public Set<Long> bucketsIds;
    public Set<Long> usersIds;
    public Set<Short> teamsIds;
    public Long columnsId;
    public String sort;
    public Long filterId;
    public Short roleId;
    public List<Short> featuresIds;

    public View id(Long id) {
        this.id = id;
        return this;
    }

    public View name(String name) {
        this.name = name;
        return this;
    }

    public View description(String description) {
        this.description = description;
        return this;
    }

    public View classesIds(Set<Long> classesIds) {
        this.classesIds = classesIds;
        return this;
    }

    public View bucketsIds(Set<Long> bucketsIds) {
        this.bucketsIds = bucketsIds;
        return this;
    }

    public View usersIds(Set<Long> usersIds) {
        this.usersIds = usersIds;
        return this;
    }

    public View teamsIds(Set<Short> teamsIds) {
        this.teamsIds = teamsIds;
        return this;
    }

    public View columnsId(Long columnsId) {
        this.columnsId = columnsId;
        return this;
    }

    public View sort(String sort) {
        this.sort = sort;
        return this;
    }

    public View filterId(Long filterId) {
        this.filterId = filterId;
        return this;
    }

    public View roleId(Short roleId) {
        this.roleId = roleId;
        return this;
    }

    public View featuresIds(List<Short> featuresIds) {
        this.featuresIds = featuresIds;
        return this;
    }
}
