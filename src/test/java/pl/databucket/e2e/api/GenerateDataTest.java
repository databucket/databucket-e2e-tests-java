package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.model.DataRow;
import pl.databucket.e2e.model.Generated;
import pl.databucket.e2e.model.Tag;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Json.arr;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.AGENT;
import static pl.databucket.e2e.support.UserKey.ROBOT;

/** Generating random rows on the server: {@code POST /api/bucket/{bucket}/generate} (Databucket 3.20.0+). */
@ApiSuite
@DisplayName("Generate data")
@SuppressWarnings({"rawtypes", "unchecked"})
class GenerateDataTest {

    private static final String BUCKET = "users";
    private static final Map<String, Object> EMAILS = obj("$.email", "user-{n}@example.com");

    private BucketApi<Map> users;

    @BeforeEach
    void bucket(TestProject project) {
        project.createBucket(BUCKET);
        users = project.as(ROBOT).bucket(BUCKET, Map.class);
    }

    @Test
    @DisplayName("inserts the rows, numbering {n} after the last row of the bucket")
    void numbers_after_the_last_row(TestProject project) {
        users.insertAll(obj("email", "first@example.com"), obj("email", "second@example.com"));
        long lastId = users.find(Search.all().sort("desc(id)").limit(1)).data().get(0).id();

        Generated generated = users.generate(obj("fields", EMAILS, "count", 5));

        assertThat(generated.inserted()).isEqualTo(5);
        assertThat(generated.firstN()).isEqualTo(lastId + 1);
        assertThat(generated.lastN()).isEqualTo(lastId + 5);
        assertThat(generated.rows()).hasSize(3);
        List<Map> all = users.properties(Search.all().sort("id"));
        assertThat(all).hasSize(7);
        assertThat(all.subList(2, 7)).extracting(row -> row.get("email")).containsExactlyElementsOf(
                LongStream.rangeClosed(lastId + 1, lastId + 5).mapToObj(n -> "user-" + n + "@example.com").toList());
    }

    @Test
    @DisplayName("a second call continues {n}, so the generated values stay unique")
    void continues_between_calls() {
        Generated first = users.generate(obj("fields", EMAILS, "count", 3));
        Generated second = users.generate(obj("fields", EMAILS, "count", 3));

        assertThat(second.firstN()).isEqualTo(first.lastN() + 1);
        assertThat(users.properties(Search.all())).extracting(row -> row.get("email")).doesNotHaveDuplicates().hasSize(6);
    }

    @Test
    @DisplayName("startN sets the number of the first row")
    void start_n() {
        Generated generated = users.generate(obj("fields", obj("$.login", "tester-{n}"), "count", 2, "startN", 100));

        assertThat(generated.firstN()).isEqualTo(100);
        assertThat(users.properties(Search.all().sort("id"))).extracting(row -> row.get("login"))
                .containsExactly("tester-100", "tester-101");
    }

    @Test
    @DisplayName("every kind of generator gives values as specified, nested paths included")
    void generators() {
        users.generate(obj("count", 50, "seed", 3, "fields", obj(
                "$.firstName", "#{Name.firstName}",
                "$.phone", "#{numerify '###-###'}",
                "$.age", obj("number", arr(18, 90)),
                "$.balance", obj("number", arr(0, 100), "decimals", 2),
                "$.status", obj("oneOf", arr("active", "blocked")),
                "$.birthDate", obj("date", arr("1990-01-01", "1990-12-31")),
                "$.lastLogin", obj("datetime", arr("2025-01-01", "2025-01-31T23:59:59Z")),
                "$.address.city", "#{Address.city}",
                "$.type", "customer",
                "$.active", true)));

        List<Map> rows = users.properties(Search.all());
        assertThat(rows).hasSize(50).allSatisfy(row -> {
            assertThat((String) row.get("firstName")).isNotBlank();
            assertThat((String) row.get("phone")).matches("\\d{3}-\\d{3}");
            assertThat(((Number) row.get("age")).intValue()).isBetween(18, 90);
            BigDecimal balance = new BigDecimal(row.get("balance").toString());
            assertThat(balance).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
            assertThat(balance.scale()).isLessThanOrEqualTo(2);
            assertThat(row.get("status")).isIn("active", "blocked");
            assertThat(LocalDate.parse((String) row.get("birthDate")))
                    .isBetween(LocalDate.of(1990, 1, 1), LocalDate.of(1990, 12, 31));
            assertThat(Instant.parse((String) row.get("lastLogin")))
                    .isBetween(Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-31T23:59:59Z"));
            assertThat((String) ((Map) row.get("address")).get("city")).isNotBlank();
            assertThat(row.get("type")).isEqualTo("customer");
            assertThat(row.get("active")).isEqualTo(true);
        });
        assertThat(rows).extracting(row -> row.get("status")).contains("active", "blocked");
    }

    @Test
    @DisplayName("sets the tag of the rows and reserves them for the caller")
    void tag_and_reservation(TestProject project) {
        Tag tag = project.quietly(ADMIN).settings().tags().create(new Tag().name("generated"));

        users.generate(obj("fields", EMAILS, "count", 4, "tagId", tag.id, "reserved", true));

        List<DataRow<Map>> rows = users.find(Search.all().limit(100)).data();
        assertThat(rows).hasSize(4).allSatisfy(row -> {
            assertThat(row.tagId()).isEqualTo(tag.id);
            assertThat(row.reserved()).isTrue();
            assertThat(row.owner()).isEqualTo(project.user(ROBOT).username());
            assertThat(row.createdBy()).isEqualTo(project.user(ROBOT).username());
        });
    }

    @Test
    @DisplayName("a preview inserts nothing and returns at most 10 rows")
    void preview_inserts_nothing() {
        Generated preview = users.previewGenerated(obj("fields", EMAILS, "count", 5000));

        assertThat(preview.inserted()).isZero();
        assertThat(preview.rows()).hasSize(10);
        assertThat(preview.lastN()).isEqualTo(preview.firstN() + 9);
        assertThat(users.count(Search.all())).isZero();
    }

    @Test
    @DisplayName("with the same seed the preview shows the rows that are then inserted")
    void preview_with_a_seed() {
        Map<String, Object> request = obj("count", 20, "seed", 42, "locale", "pl", "fields", obj(
                "$.name", "#{Name.fullName}", "$.age", obj("number", arr(18, 90)), "$.email", "user-{n}@example.com"));

        Generated preview = users.previewGenerated(request);
        users.generate(request);

        assertThat(users.properties(Search.all().sort("id")).subList(0, 10)).isEqualTo(preview.rows());
    }

    @Test
    @DisplayName("an AI agent (AGENT) may generate data, as it may insert it")
    void agent_may_generate(TestProject project) {
        Generated generated = project.as(AGENT).bucket(BUCKET, Map.class).generate(obj("fields", EMAILS, "count", 2));

        assertThat(generated.inserted()).isEqualTo(2);
        assertThat(users.count(Search.all())).isEqualTo(2);
    }

    @ParameterizedTest(name = "count {0}")
    @ValueSource(ints = {0, -1, 10_001})
    @DisplayName("refuses a count outside 1..10000")
    void refuses_the_count(int count) {
        ApiResponse response = users.tryGenerate(obj("fields", EMAILS, "count", count));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.<String>path("message")).contains("1..10000");
        assertThat(users.count(Search.all())).isZero();
    }

    @Test
    @DisplayName("refuses an invalid generator with a message naming the field, and inserts nothing")
    void refuses_an_invalid_generator() {
        ApiResponse response = users.tryGenerate(obj("count", 5, "fields", obj(
                "$.email", "user-{n}@example.com", "$.age", obj("number", arr(18)))));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.<String>path("message")).contains("$.age");
        assertThat(users.count(Search.all())).isZero();
    }

    @Test
    @DisplayName("refuses a request without fields")
    void refuses_no_fields() {
        ApiResponse response = users.tryGenerate(obj("count", 5));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.<String>path("message")).contains("at least one field");
    }

    @Test
    @DisplayName("refuses a tag that doesn't exist, and inserts nothing")
    void refuses_an_unknown_tag() {
        ApiResponse response = users.tryGenerate(obj("fields", EMAILS, "count", 3, "tagId", 999_999_999));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.<String>path("message")).contains("tag").contains("doesn't exist");
        assertThat(users.count(Search.all())).isZero();
    }

    @Test
    void a_bucket_that_does_not_exist_gives_404(TestProject project) {
        ApiResponse response = project.as(ROBOT).bucket("no-such-bucket", Map.class)
                .tryGenerate(obj("fields", EMAILS, "count", 1));

        assertThat(response.status()).isEqualTo(404);
    }
}
