package pl.databucket.e2e.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** An icon (SVG) of the project. Fields as in the JSON of the API; null fields are left out of requests. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Svg extends Audited {

    public Long id;
    public String name;
    public String structure;

    public Svg id(Long id) {
        this.id = id;
        return this;
    }

    public Svg name(String name) {
        this.name = name;
        return this;
    }

    public Svg structure(String structure) {
        this.structure = structure;
        return this;
    }
}
