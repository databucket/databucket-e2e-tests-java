package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.ROBOT;

@ApiSuite
@DisplayName("Paging and sorting")
class PagingSortingTest {

    private static final String BUCKET = "items";
    private static final String DATA = "/api/bucket/" + BUCKET;
    private static final List<String> NAMES = List.of("pear", "Apple", "cherry", "banana", "date");
    private static final List<Integer> RANKS = List.of(9, 100, 10, 1, 55);

    private Api robot;

    @BeforeEach
    void items(TestProject project) {
        project.createBucket(BUCKET);
        robot = project.as(ROBOT);
        List<Object> rows = new ArrayList<>();
        for (int i = 0; i < NAMES.size(); i++) rows.add(obj("properties", obj("name", NAMES.get(i), "rank", RANKS.get(i))));
        project.quietly(ROBOT).post(DATA + "/multi", call().body(rows).expect(201));
    }

    private ApiResponse get(String query) {
        return robot.post(DATA + "/get" + (query.isEmpty() ? "" : "?" + query), call().body(obj()).expect(200));
    }

    private static List<String> names(ApiResponse response) {
        List<String> names = response.path("data.properties.name");
        return names == null ? List.of() : names;
    }

    private static String encoded(String sort) {
        return URLEncoder.encode(sort, UTF_8);
    }

    @Test
    @DisplayName("pages through the rows by id, with the total and the number of pages")
    void pages(TestProject project) {
        ApiResponse first = get("limit=2&page=1");
        ApiResponse second = get("limit=2&page=2");
        ApiResponse last = get("limit=2&page=3");

        assertThat(names(first)).containsExactly("pear", "Apple");
        assertThat(names(second)).containsExactly("cherry", "banana");
        assertThat(names(last)).containsExactly("date");
        assertThat(first.<Integer>path("total")).isEqualTo(5);
        assertThat(first.<Integer>path("totalPages")).isEqualTo(3);
        assertThat(first.<Integer>path("page")).isEqualTo(1);
        assertThat(first.<Integer>path("limit")).isEqualTo(2);
    }

    @Test
    @DisplayName("without a limit returns one row, and a page after the last is empty")
    void defaults(TestProject project) {
        ApiResponse one = get("");
        ApiResponse beyond = get("limit=2&page=9");

        assertThat(names(one)).hasSize(1);
        assertThat(one.<Integer>path("total")).isEqualTo(5);
        assertThat(names(beyond)).isEmpty();
    }

    @Test
    void sorts_by_id_descending(TestProject project) {
        assertThat(names(get("limit=10&sort=desc(id)"))).containsExactly("date", "banana", "cherry", "Apple", "pear");
    }

    @Test
    @DisplayName("sorts numbers as numbers, not as texts")
    void numbers(TestProject project) {
        ApiResponse response = get("limit=10&sort=" + encoded("desc($.rank)"));

        assertThat(response.<List<Integer>>path("data.properties.rank")).containsExactly(100, 55, 10, 9, 1);
    }

    @Test
    void sorts_texts_by_a_property(TestProject project) {
        List<String> sorted = names(get("limit=10&sort=" + encoded("asc($.name)")));

        // whatever the collation does with upper case, the lower-case names are in order
        assertThat(sorted.stream().filter(name -> !name.equals("Apple")).toList()).containsExactly("banana", "cherry", "date", "pear");
        assertThat(sorted).hasSize(5);
    }

    @Test
    void random_order_returns_every_row_once(TestProject project) {
        assertThat(names(get("limit=10&sort=random"))).containsExactlyInAnyOrderElementsOf(NAMES);
    }

    @Test
    @DisplayName("count=false leaves out the total")
    void without_count(TestProject project) {
        ApiResponse response = get("limit=2&count=false");

        assertThat(names(response)).containsExactly("pear", "Apple");
        assertThat(response.<Object>path("total")).isNull();
        assertThat(response.<Object>path("totalPages")).isNull();
    }

    @Test
    void an_unknown_sort_field_is_refused(TestProject project) {
        ApiResponse response = robot.post(DATA + "/get?sort=desc(password)", call().body(obj()));

        assertThat(response.status()).isEqualTo(400);
    }
}
