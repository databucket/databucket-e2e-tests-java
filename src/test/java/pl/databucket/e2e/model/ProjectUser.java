package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Set;

/** A user of the project as an ADMIN sees it in the settings. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectUser extends Audited {

    public Long id;
    public String username;
    public Boolean enabled;
    public Set<Short> rolesIds;
    public Set<Short> teamsIds;
    public Instant expirationDate;

    public ProjectUser id(Long id) {
        this.id = id;
        return this;
    }

    public ProjectUser username(String username) {
        this.username = username;
        return this;
    }

    public ProjectUser enabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public ProjectUser rolesIds(Set<Short> rolesIds) {
        this.rolesIds = rolesIds;
        return this;
    }

    public ProjectUser teamsIds(Set<Short> teamsIds) {
        this.teamsIds = teamsIds;
        return this;
    }

    public ProjectUser expirationDate(Instant expirationDate) {
        this.expirationDate = expirationDate;
        return this;
    }
}
