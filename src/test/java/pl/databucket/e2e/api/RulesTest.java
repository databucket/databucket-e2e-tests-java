package pl.databucket.e2e.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Change;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.model.DataRow;
import pl.databucket.e2e.model.Tag;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.client.Rules.and;
import static pl.databucket.e2e.client.Rules.logic;
import static pl.databucket.e2e.client.Rules.not;
import static pl.databucket.e2e.client.Rules.or;
import static pl.databucket.e2e.client.Rules.rule;
import static pl.databucket.e2e.client.Rules.var;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;

/** The rules of a search: [left, operator, right], combined with 'and'; {"or": [...]}, {"and": [...]}, {"!": rule}. */
@ApiSuite
@Area(Area.DATA)
@DisplayName("Search rules")
class RulesTest {

    private static final String BUCKET = "people";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Person(String name, String city, Integer age, Boolean vip, Address address, String price) {
    }

    record Address(String zip) {
    }

    private static final List<Person> PEOPLE = List.of(
            new Person("Anna", "Paris", 34, true, new Address("75001"), null),
            new Person("bob", "Berlin", 17, false, new Address("10115"), null),
            new Person("Carol", "Paris", 51, false, null, null),
            new Person("Dave", "Warsaw", 28, true, null, "$10"),
            new Person("O'Brien", "Dublin", 40, null, null, null));

    private BucketApi<Person> people;

    @BeforeEach
    void people(TestProject project) {
        project.createBucket(BUCKET);
        project.quietly(ROBOT).bucket(BUCKET, Person.class).insertAll(PEOPLE);
        people = project.as(ROBOT).bucket(BUCKET, Person.class);
    }

    /** The names of the people matching the search, in the order of the ids. */
    private static List<String> names(BucketApi<Person> bucket, Search search) {
        return bucket.properties(search).stream().map(Person::name).toList();
    }

    private List<String> names(Object... rules) {
        return names(people, Search.where(rules));
    }

    @Test
    @DisplayName("== and != compare texts")
    void equality(TestProject project) {
        verify("names", names(rule("$.city", "==", "Paris")), List.of("Anna", "Carol"));
        verify("names", names(rule("$.city", "!=", "Paris")), List.of("bob", "Dave", "O'Brien"));
    }

    @Test
    @DisplayName("<, <=, >, >= compare numbers")
    void comparisons(TestProject project) {
        verify("names", names(rule("$.age", ">", 30), rule("$.age", "<=", 51)), List.of("Anna", "Carol", "O'Brien"));
        verify("names", names(rule("$.age", "<", 18)), List.of("bob"));
        verify("names", names(rule("$.age", ">=", 40)), List.of("Carol", "O'Brien"));
    }

    @Test
    void in_and_not_in_take_a_list(TestProject project) {
        verify("names", names(rule("$.city", "in", List.of("Berlin", "Warsaw"))), List.of("bob", "Dave"));
        verify("names", names(rule("$.city", "!in", List.of("Berlin", "Warsaw"))), List.of("Anna", "Carol", "O'Brien"));
    }

    @Test
    @DisplayName("like and !like match with % and _")
    void like(TestProject project) {
        verify("names", names(rule("$.name", "like", "C%")), List.of("Carol"));
        verify("names", names(rule("$.name", "like", "_ob")), List.of("bob"));
        verify("names", names(rule("$.name", "!like", "%a%")), List.of("bob", "O'Brien"));
    }

    @Test
    @DisplayName("regular expressions: ~ is case-sensitive, ~* is not")
    void regular_expressions(TestProject project) {
        verify("names", names(rule("$.name", "~", "^[A-C]")), List.of("Anna", "Carol"));
        verify("names", names(rule("$.name", "~*", "^b")), List.of("bob"));
        verify("names", names(rule("$.name", "!~", "^[A-Z]")), List.of("bob"));
    }

    @Test
    void booleans_and_nested_properties(TestProject project) {
        verify("names", names(rule("$.vip", "==", true)), List.of("Anna", "Dave"));
        verify("names", names(rule("$.address.zip", "==", "75001")), List.of("Anna"));
    }

    @Test
    @DisplayName("or, and and ! combine rules")
    void combined(TestProject project) {
        verify("names", names(or(rule("$.city", "==", "Berlin"), and(rule("$.city", "==", "Paris"), rule("$.age", ">", 40)))), List.of("bob", "Carol"));
        verify("names", names(not(rule("$.city", "==", "Paris"))), List.of("bob", "Dave", "O'Brien"));
    }

    @Test
    @DisplayName("is null and is not null: a missing property is null too")
    void nulls(TestProject project) {
        verify("names", names(rule("$.price", "is not", null)), List.of("Dave"));
        verify("names", names(rule("$.email", "is", null)), PEOPLE.stream().map(Person::name).toList());
    }

    @Test
    @DisplayName("a text starting with $ is a value inside in, not a property")
    void dollar_text(TestProject project) {
        verify("names", names(rule("$.price", "in", List.of("$10"))), List.of("Dave"));
    }

    @Test
    @DisplayName("rules on the fields of a row: id, createdBy, reserved")
    void fields(TestProject project) {
        long carol = people.find(Search.where(rule("$.name", "==", "Carol")).limit(1)).rows().get(0).id();

        verify("names", names(rule("id", "==", carol)), List.of("Carol"));
        verify("names", names(rule("createdBy", "==", project.user(ROBOT).username()), rule("$.age", "<", 18)), List.of("bob"));
        verify("names", names(rule("reserved", "==", true)), List.of());
    }

    @Test
    @DisplayName("@currentUser is the signed-in user")
    void current_user(TestProject project) {
        BucketApi<Person> asMember = project.as(MEMBER).bucket(BUCKET, Person.class);
        asMember.reserve(Search.where(rule("$.name", "==", "Dave")).limit(1));

        verify("names", names(asMember, Search.where(rule("owner", "==", "@currentUser"))), List.of("Dave"));
        verify("names", names(rule("owner", "==", "@currentUser")), List.of());
    }

    @Test
    void rules_and_logic_given_together_must_both_match(TestProject project) {
        Search search = Search.where(rule("$.city", "==", "Paris")).logic(logic(">", var("$.age"), 40));

        verify("names", names(people, search), List.of("Carol"));
    }

    @Test
    void a_value_with_quotes_is_only_a_value_never_SQL(TestProject project) {
        verify("names", names(rule("$.name", "==", "O'Brien")), List.of("O'Brien"));
        verify("names", names(rule("$.name", "==", "x' OR '1'='1")), List.of());
        verify("names", names(rule("$.name", "like", "%'; DROP TABLE tags; --")), List.of());
        // the table is still there
        verify("names", names(rule("$.city", "==", "Warsaw")), List.of("Dave"));
    }

    @Test
    void a_rule_written_as_SQL_text_is_refused(TestProject project) {
        ApiResponse response = people.tryFind(Search.where("properties->>'city' = 'Paris'"));

        verify("status", response.status(), 400);
    }

    @Test
    void an_unknown_field_or_operator_is_refused(TestProject project) {
        verify("status", people.tryFind(Search.where(rule("password", "==", "x"))).status(), 400);
        verify("status", people.tryFind(Search.where(rule("$.age", "===", 1))).status(), 400);
    }

    @Test
    void rules_on_the_tag_of_the_rows(TestProject project) {
        Tag tag = project.quietly(ADMIN).settings().tags().create(new Tag().name("vip-checked"));
        people.updateWhere(Change.tag(tag.id).where(rule("$.vip", "==", true)));

        verify("names", names(rule("tagId", "==", tag.id)), List.of("Anna", "Dave"));
        verify("names", names(rule("tagId", "is", null)), List.of("bob", "Carol", "O'Brien"));
        List<DataRow<Person>> tagged = people.find(Search.where(rule("tagId", "==", tag.id)).limit(10)).rows();
        check("tagId: only the tag", () -> assertThat(tagged).extracting(DataRow::tagId).containsOnly(tag.id));
    }
}
