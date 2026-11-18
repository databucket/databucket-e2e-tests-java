package pl.databucket.e2e.support;

/**
 * Where the tests run and how they get in. Gradle properties (-PbaseUrl=...) or environment variables (BASE_URL=...)
 * override the defaults: the docker compose of databucket-server.
 */
public final class Env {

    /** Databucket under test. */
    public static final String BASE_URL = value("baseUrl", "BASE_URL", "http://databucket.localhost").replaceAll("/+$", "");

    /** A SUPER admin: creates a separate project with its own users for every test. */
    public static final String SUPER_USER = value("superUser", "SUPER_USER", "super");
    public static final String SUPER_PASSWORD = value("superPassword", "SUPER_PASSWORD", "super");

    /** The browser window visible (-Pheaded=true or HEADED=true); headless by default. */
    public static final boolean HEADED = Boolean.parseBoolean(value("headed", "HEADED", "false"));

    /** The password of every user the tests create. */
    public static final String TEST_PASSWORD = "E2e-Passw0rd!";

    private Env() {
    }

    private static String value(String property, String variable, String defaultValue) {
        String value = System.getProperty("databucket." + property);
        if (value == null || value.isBlank()) value = System.getenv(variable);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
