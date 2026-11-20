package pl.databucket.e2e.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rules of a search as the API takes them: {@code rule("$.city", "==", "Paris")} is
 * {@code ["$.city", "==", "Paris"]}; {@code or(...)}, {@code and(...)}, {@code not(...)} combine them.
 */
public final class Rules {

    private Rules() {
    }

    public static List<Object> rule(Object left, String operator, Object right) {
        return new ArrayList<>(Arrays.asList(left, operator, right));
    }

    public static Map<String, Object> or(Object... rules) {
        return Map.of("or", List.of(rules));
    }

    public static Map<String, Object> and(Object... rules) {
        return Map.of("and", List.of(rules));
    }

    public static Map<String, Object> not(Object rule) {
        return Map.of("!", rule);
    }

    /** A JsonLogic expression, e.g. {@code logic(">", var("$.age"), 40)}. */
    public static Map<String, Object> logic(String operator, Object... arguments) {
        Map<String, Object> logic = new LinkedHashMap<>();
        logic.put(operator, List.of(arguments));
        return logic;
    }

    public static Map<String, Object> var(String name) {
        return Map.of("var", name);
    }
}
