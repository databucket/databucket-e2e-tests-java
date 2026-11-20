package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Set;

/** A bucket (settings): a table of test data. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Bucket extends Audited {

    public Long id;
    public String name;
    public Icon icon;
    public String description;
    public Long classId;
    public Set<Long> groupsIds;
    public Set<Long> usersIds;
    public Set<Short> teamsIds;
    public Boolean history;
    public Boolean protectedData;
    public Boolean overviewVisible;
    public Integer historyLimit;
    public List<IndexedProperty> indexedProperties;
    public Short roleId;

    public Bucket id(Long id) {
        this.id = id;
        return this;
    }

    public Bucket name(String name) {
        this.name = name;
        return this;
    }

    public Bucket icon(Icon icon) {
        this.icon = icon;
        return this;
    }

    public Bucket description(String description) {
        this.description = description;
        return this;
    }

    public Bucket classId(Long classId) {
        this.classId = classId;
        return this;
    }

    public Bucket groupsIds(Set<Long> groupsIds) {
        this.groupsIds = groupsIds;
        return this;
    }

    public Bucket usersIds(Set<Long> usersIds) {
        this.usersIds = usersIds;
        return this;
    }

    public Bucket teamsIds(Set<Short> teamsIds) {
        this.teamsIds = teamsIds;
        return this;
    }

    public Bucket history(Boolean history) {
        this.history = history;
        return this;
    }

    public Bucket protectedData(Boolean protectedData) {
        this.protectedData = protectedData;
        return this;
    }

    public Bucket overviewVisible(Boolean overviewVisible) {
        this.overviewVisible = overviewVisible;
        return this;
    }

    public Bucket historyLimit(Integer historyLimit) {
        this.historyLimit = historyLimit;
        return this;
    }

    public Bucket indexedProperties(List<IndexedProperty> indexedProperties) {
        this.indexedProperties = indexedProperties;
        return this;
    }

    public Bucket roleId(Short roleId) {
        this.roleId = roleId;
        return this;
    }
}
