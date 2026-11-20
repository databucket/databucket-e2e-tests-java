package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** An icon: the name of a built-in one, or an SVG. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Icon {

    public String name;
    public String color;
    public String svg;

    public Icon name(String name) {
        this.name = name;
        return this;
    }

    public Icon color(String color) {
        this.color = color;
        return this;
    }

    public Icon svg(String svg) {
        this.svg = svg;
        return this;
    }
}
