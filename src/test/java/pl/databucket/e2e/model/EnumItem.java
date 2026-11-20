package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** An item of an enum. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnumItem {

    public String value;
    public String text;
    public Icon icon;

    public EnumItem value(String value) {
        this.value = value;
        return this;
    }

    public EnumItem text(String text) {
        this.text = text;
        return this;
    }

    public EnumItem icon(Icon icon) {
        this.icon = icon;
        return this;
    }
}
