package pl.databucket.e2e.support;

/** The roles of Databucket with their fixed ids. */
public enum Role {
    SUPER(1), ADMIN(2), MEMBER(3), ROBOT(4), AGENT(5);

    public final int id;

    Role(int id) {
        this.id = id;
    }
}
