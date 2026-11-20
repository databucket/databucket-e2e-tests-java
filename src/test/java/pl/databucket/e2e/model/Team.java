package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Set;

/** A team of users. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Team extends Audited {

    public Short id;
    public String name;
    public String description;
    public Set<Long> usersIds;

    public Team id(Short id) {
        this.id = id;
        return this;
    }

    public Team name(String name) {
        this.name = name;
        return this;
    }

    public Team description(String description) {
        this.description = description;
        return this;
    }

    public Team usersIds(Set<Long> usersIds) {
        this.usersIds = usersIds;
        return this;
    }
}
