package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** An enum: allowed values of a field. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataEnum extends Audited {

    public Integer id;
    public String name;
    public String description;
    public Boolean iconsEnabled;
    public List<EnumItem> items;

    public DataEnum id(Integer id) {
        this.id = id;
        return this;
    }

    public DataEnum name(String name) {
        this.name = name;
        return this;
    }

    public DataEnum description(String description) {
        this.description = description;
        return this;
    }

    public DataEnum iconsEnabled(Boolean iconsEnabled) {
        this.iconsEnabled = iconsEnabled;
        return this;
    }

    public DataEnum items(List<EnumItem> items) {
        this.items = items;
        return this;
    }
}
