package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Set;

/** A group of buckets. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Group extends Audited {

    public Long id;
    public String name;
    public String shortName;
    public String description;
    public Set<Long> bucketsIds;
    public Set<Long> usersIds;
    public Set<Short> teamsIds;
    public Short roleId;

    public Group id(Long id) {
        this.id = id;
        return this;
    }

    public Group name(String name) {
        this.name = name;
        return this;
    }

    public Group shortName(String shortName) {
        this.shortName = shortName;
        return this;
    }

    public Group description(String description) {
        this.description = description;
        return this;
    }

    public Group bucketsIds(Set<Long> bucketsIds) {
        this.bucketsIds = bucketsIds;
        return this;
    }

    public Group usersIds(Set<Long> usersIds) {
        this.usersIds = usersIds;
        return this;
    }

    public Group teamsIds(Set<Short> teamsIds) {
        this.teamsIds = teamsIds;
        return this;
    }

    public Group roleId(Short roleId) {
        this.roleId = roleId;
        return this;
    }
}
