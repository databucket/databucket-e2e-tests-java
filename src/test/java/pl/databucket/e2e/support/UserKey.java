package pl.databucket.e2e.support;

/** The users of a {@link TestProject}, one per role. */
public enum UserKey {
    ADMIN(Role.ADMIN), MEMBER(Role.MEMBER), ROBOT(Role.ROBOT), AGENT(Role.AGENT);

    public final Role role;

    UserKey(Role role) {
        this.role = role;
    }

    /** "admin", "member"...: the end of the username and the caller's name in the report. */
    public String key() {
        return name().toLowerCase();
    }
}
