package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Set;

/** A user in the management (SUPER); the password only in requests. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManagedUser extends Audited {

    public Long id;
    public String username;
    public String description;
    public String email;
    public String password;
    public Boolean enabled;
    public Instant expirationDate;
    public Set<Short> rolesIds;
    public Set<Integer> projectsIds;

    public ManagedUser id(Long id) {
        this.id = id;
        return this;
    }

    public ManagedUser username(String username) {
        this.username = username;
        return this;
    }

    public ManagedUser description(String description) {
        this.description = description;
        return this;
    }

    public ManagedUser email(String email) {
        this.email = email;
        return this;
    }

    public ManagedUser password(String password) {
        this.password = password;
        return this;
    }

    public ManagedUser enabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public ManagedUser expirationDate(Instant expirationDate) {
        this.expirationDate = expirationDate;
        return this;
    }

    public ManagedUser rolesIds(Set<Short> rolesIds) {
        this.rolesIds = rolesIds;
        return this;
    }

    public ManagedUser projectsIds(Set<Integer> projectsIds) {
        this.projectsIds = projectsIds;
        return this;
    }
}
