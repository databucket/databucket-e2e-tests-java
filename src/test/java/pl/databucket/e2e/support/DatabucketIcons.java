package pl.databucket.e2e.support;

import pl.databucket.e2e.model.Icon;

import java.util.Map;

import static pl.databucket.e2e.support.Json.obj;

/**
 * The two icons of Databucket (the bucket with a B of the application's favicon) that every test project has
 * (Settings &gt; Icons): outlined, the icon of the buckets made by {@link TestProject#createBucket}, and filled.
 * Without colors of their own ({@code currentColor}): the color of the theme, or of the icon when it has one.
 */
public final class DatabucketIcons {

    private static final String B = "M32.56 47L20.38 47L20.38 17L31.16 17Q36.95 17 39.96 19.12Q42.98 21.24 42.98 25.28"
            + "Q42.98 27.61 41.91 29.26Q40.84 30.91 38.76 31.69Q41.11 32.31 42.36 34Q43.62 35.69 43.62 38.12"
            + "Q43.62 42.53 40.83 44.74Q38.04 46.96 32.56 47ZM32.68 34.25L27.61 34.25L27.61 41.44L32.35 41.44"
            + "Q34.31 41.44 35.36 40.54Q36.41 39.64 36.41 38.02Q36.41 34.27 32.68 34.25ZM27.61 22.58L27.61 29.51"
            + "L31.38 29.51Q33.75 29.49 34.76 28.64Q35.77 27.8 35.77 26.15Q35.77 24.25 34.68 23.42Q33.59 22.58 31.16 22.58Z";

    public static final String OUTLINED_NAME = "databucket-outlined";
    public static final String FILLED_NAME = "databucket-filled";

    /** The outline of the bucket with a solid B. */
    public static final String OUTLINED = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"-2 -2 68 68\">"
            + "<path d=\"M8.5 6.5H55.5L49.6 54.6Q49.2 57.5 46.3 57.5H17.7Q14.8 57.5 14.4 54.6Z\" fill=\"none\""
            + " stroke=\"currentColor\" stroke-width=\"5\" stroke-linejoin=\"round\"/>"
            + "<path fill=\"currentColor\" fill-rule=\"evenodd\" d=\"" + B + "\"/></svg>";

    /** A solid bucket with the B cut out. */
    public static final String FILLED = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"-2 -2 68 68\">"
            + "<path fill=\"currentColor\" fill-rule=\"evenodd\" d=\"M6 4L58 4L51.96 55Q51.56 60 46.56 60L17.44 60"
            + "Q12.44 60 12.04 55Z" + B + "\"/></svg>";

    private DatabucketIcons() {
    }

    /** The icon of a bucket (enum item...) as the GUI stores a chosen SVG: its name and markup. */
    public static Icon outlined() {
        return new Icon().name(OUTLINED_NAME).svg(OUTLINED);
    }

    public static Icon filled() {
        return new Icon().name(FILLED_NAME).svg(FILLED);
    }

    /** {@link #outlined()} as a JSON object of a request. */
    public static Map<String, Object> outlinedJson() {
        return obj("name", OUTLINED_NAME, "svg", OUTLINED);
    }
}
