package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Set;

/** A project in the management (SUPER). Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManagedProject extends Audited {

    public Integer id;
    public String name;
    public String description;
    public Boolean enabled;
    public Boolean publicVisible;
    public Instant expirationDate;
    public Set<Long> usersIds;
    public Set<Integer> templatesIds;
    public Boolean empty;

    public ManagedProject id(Integer id) {
        this.id = id;
        return this;
    }

    public ManagedProject name(String name) {
        this.name = name;
        return this;
    }

    public ManagedProject description(String description) {
        this.description = description;
        return this;
    }

    public ManagedProject enabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public ManagedProject publicVisible(Boolean publicVisible) {
        this.publicVisible = publicVisible;
        return this;
    }

    public ManagedProject expirationDate(Instant expirationDate) {
        this.expirationDate = expirationDate;
        return this;
    }

    public ManagedProject usersIds(Set<Long> usersIds) {
        this.usersIds = usersIds;
        return this;
    }

    public ManagedProject templatesIds(Set<Integer> templatesIds) {
        this.templatesIds = templatesIds;
        return this;
    }

    public ManagedProject empty(Boolean empty) {
        this.empty = empty;
        return this;
    }
}
