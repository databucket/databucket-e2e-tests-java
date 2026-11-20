package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.arr;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;

/** The rules of a search: [left, operator, right], combined with 'and'; {"or": [...]}, {"and": [...]}, {"!": rule}. */
@ApiSuite
@DisplayName("Search rules")
class RulesTest {

    private static final String BUCKET = "people";
    private static final String DATA = "/api/bucket/" + BUCKET;

    private static List<Map<String, Object>> people() {
        Map<String, Object> carol = obj("name", "Carol", "city", "Paris", "age", 51, "vip", false);
        carol.put("email", null);
        return List.of(
                obj("name", "Anna", "city", "Paris", "age", 34, "vip", true, "address", obj("zip", "75001")),
                obj("name", "bob", "city", "Berlin", "age", 17, "vip", false, "address", obj("zip", "10115")),
                carol,
                obj("name", "Dave", "city", "Warsaw", "age", 28, "vip", true, "price", "$10"),
                obj("name", "O'Brien", "city", "Dublin", "age", 40));
    }

    private static final List<String> ALL = List.of("Anna", "bob", "Carol", "Dave", "O'Brien");

    private Api robot;

    @BeforeEach
    void people(TestProject project) {
        project.createBucket(BUCKET);
        robot = project.as(ROBOT);
        List<Object> rows = new ArrayList<>();
        for (Map<String, Object> person : people()) rows.add(obj("properties", person));
        project.quietly(ROBOT).post(DATA + "/multi", call().body(rows).expect(201));
    }

    /** The names of the people matching the search (rules and/or logic), in the order of the ids. */
    private static List<String> names(Api api, Map<String, Object> search) {
        ApiResponse response = api.post(DATA + "/get?limit=100", call().body(search).expect(200));
        List<String> names = response.path("data.properties.name");
        return names == null ? List.of() : names;
    }

    private static Map<String, Object> rules(Object... rules) {
        return obj("rules", arr(rules));
    }

    private static List<Object> rule(Object left, String operator, Object right) {
        return new ArrayList<>(Arrays.asList(left, operator, right));
    }

    @Test
    @DisplayName("== and != compare texts")
    void equality(TestProject project) {
        assertThat(names(robot, rules(rule("$.city", "==", "Paris")))).containsExactly("Anna", "Carol");
        assertThat(names(robot, rules(rule("$.city", "!=", "Paris")))).containsExactly("bob", "Dave", "O'Brien");
    }

    @Test
    @DisplayName("<, <=, >, >= compare numbers")
    void comparisons(TestProject project) {
        assertThat(names(robot, rules(rule("$.age", ">", 30), rule("$.age", "<=", 51)))).containsExactly("Anna", "Carol", "O'Brien");
        assertThat(names(robot, rules(rule("$.age", "<", 18)))).containsExactly("bob");
        assertThat(names(robot, rules(rule("$.age", ">=", 40)))).containsExactly("Carol", "O'Brien");
    }

    @Test
    void in_and_not_in_take_a_list(TestProject project) {
        assertThat(names(robot, rules(rule("$.city", "in", arr("Berlin", "Warsaw"))))).containsExactly("bob", "Dave");
        assertThat(names(robot, rules(rule("$.city", "!in", arr("Berlin", "Warsaw"))))).containsExactly("Anna", "Carol", "O'Brien");
    }

    @Test
    @DisplayName("like and !like match with % and _")
    void like(TestProject project) {
        assertThat(names(robot, rules(rule("$.name", "like", "C%")))).containsExactly("Carol");
        assertThat(names(robot, rules(rule("$.name", "like", "_ob")))).containsExactly("bob");
        assertThat(names(robot, rules(rule("$.name", "!like", "%a%")))).containsExactly("bob", "O'Brien");
    }

    @Test
    @DisplayName("regular expressions: ~ is case-sensitive, ~* is not")
    void regular_expressions(TestProject project) {
        assertThat(names(robot, rules(rule("$.name", "~", "^[A-C]")))).containsExactly("Anna", "Carol");
        assertThat(names(robot, rules(rule("$.name", "~*", "^b")))).containsExactly("bob");
        assertThat(names(robot, rules(rule("$.name", "!~", "^[A-Z]")))).containsExactly("bob");
    }

    @Test
    void booleans_and_nested_properties(TestProject project) {
        assertThat(names(robot, rules(rule("$.vip", "==", true)))).containsExactly("Anna", "Dave");
        assertThat(names(robot, rules(rule("$.address.zip", "==", "75001")))).containsExactly("Anna");
    }

    @Test
    @DisplayName("or, and and ! combine rules")
    void combined(TestProject project) {
        assertThat(names(robot, rules(obj("or", arr(
                rule("$.city", "==", "Berlin"),
                obj("and", arr(rule("$.city", "==", "Paris"), rule("$.age", ">", 40)))))))).containsExactly("bob", "Carol");
        assertThat(names(robot, rules(obj("!", rule("$.city", "==", "Paris"))))).containsExactly("bob", "Dave", "O'Brien");
    }

    @Test
    @DisplayName("is null and is not null: a missing property is null too")
    void nulls(TestProject project) {
        assertThat(names(robot, rules(rule("$.price", "is not", null)))).containsExactly("Dave");
        assertThat(names(robot, rules(rule("$.email", "is", null)))).containsExactlyElementsOf(ALL);
    }

    @Test
    @DisplayName("a text starting with $ is a value inside in, not a property")
    void dollar_text(TestProject project) {
        assertThat(names(robot, rules(rule("$.price", "in", arr("$10"))))).containsExactly("Dave");
    }

    @Test
    @DisplayName("rules on the fields of a row: id, createdBy, reserved")
    void fields(TestProject project) {
        ApiResponse all = robot.post(DATA + "/get?limit=100", call().body(obj()).expect(200));
        List<String> names = all.path("data.properties.name");
        List<Number> ids = all.path("data.id");
        Number carol = ids.get(names.indexOf("Carol"));

        assertThat(names(robot, rules(rule("id", "==", carol)))).containsExactly("Carol");
        assertThat(names(robot, rules(rule("createdBy", "==", project.user(ROBOT).username()), rule("$.age", "<", 18))))
                .containsExactly("bob");
        assertThat(names(robot, rules(rule("reserved", "==", true)))).isEmpty();
    }

    @Test
    @DisplayName("@currentUser is the signed-in user")
    void current_user(TestProject project) {
        Api member = project.as(MEMBER);
        member.post(DATA + "/reserve?limit=1", call().body(rules(rule("$.name", "==", "Dave"))).expect(200));

        assertThat(names(member, rules(rule("owner", "==", "@currentUser")))).containsExactly("Dave");
        assertThat(names(robot, rules(rule("owner", "==", "@currentUser")))).isEmpty();
    }

    @Test
    void rules_and_logic_given_together_must_both_match(TestProject project) {
        Map<String, Object> search = rules(rule("$.city", "==", "Paris"));
        search.put("logic", obj(">", arr(obj("var", "$.age"), 40)));

        assertThat(names(robot, search)).containsExactly("Carol");
    }

    @Test
    void a_value_with_quotes_is_only_a_value_never_SQL(TestProject project) {
        assertThat(names(robot, rules(rule("$.name", "==", "O'Brien")))).containsExactly("O'Brien");
        assertThat(names(robot, rules(rule("$.name", "==", "x' OR '1'='1")))).isEmpty();
        assertThat(names(robot, rules(rule("$.name", "like", "%'; DROP TABLE tags; --")))).isEmpty();
        // the table is still there
        assertThat(names(robot, rules(rule("$.city", "==", "Warsaw")))).containsExactly("Dave");
    }

    @Test
    void a_rule_written_as_SQL_text_is_refused(TestProject project) {
        ApiResponse response = robot.post(DATA + "/get", call().body(obj("rules", arr("properties->>'city' = 'Paris'"))));

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void an_unknown_field_or_operator_is_refused(TestProject project) {
        ApiResponse field = robot.post(DATA + "/get", call().body(rules(rule("password", "==", "x"))));
        ApiResponse operator = robot.post(DATA + "/get", call().body(rules(rule("$.age", "===", 1))));

        assertThat(field.status()).isEqualTo(400);
        assertThat(operator.status()).isEqualTo(400);
    }

    @Test
    void rules_on_the_tag_of_the_rows(TestProject project) {
        ApiResponse tag = project.quietly(ADMIN).post("/api/tags", call().body(obj("name", "vip-checked")).expect(201));
        Number tagId = tag.path("id");
        Map<String, Object> change = rules(rule("$.vip", "==", true));
        change.put("tagId", tagId);
        robot.put(DATA, call().body(change).expect(200));

        assertThat(names(robot, rules(rule("tagId", "==", tagId)))).containsExactly("Anna", "Dave");
        assertThat(names(robot, rules(rule("tagId", "is", null)))).containsExactly("bob", "Carol", "O'Brien");
    }
}
