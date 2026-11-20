package pl.databucket.e2e.model;

import java.time.Instant;

/** Who created and changed an object, and when: set by the server, ignored in requests. */
public abstract class Audited {
    public String createdBy;
    public Instant createdAt;
    public String modifiedBy;
    public Instant modifiedAt;
}
