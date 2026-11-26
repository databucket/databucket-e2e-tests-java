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
import pl.databucket.e2e.support.Area;
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
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

/** Generating random rows on the server: {@code POST /api/bucket/{bucket}/generate} (Databucket 3.20.0+). */
@ApiSuite
@Area(Area.DATA)
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

        verifyAll(v -> v
                .that("inserted", generated.inserted(), 5)
                .that("firstN", generated.firstN(), lastId + 1)
                .that("lastN", generated.lastN(), lastId + 5)
                .that("rows: size", generated.rows().size(), 3));
        List<Map> all = users.properties(Search.all().sort("id"));
        verifyAll(v -> v
                .that("rows: size", all.size(), 7)
                .that("emails of the generated rows", all.subList(2, 7).stream().map(row -> row.get("email")).toList(),
                        LongStream.rangeClosed(lastId + 1, lastId + 5).mapToObj(n -> "user-" + n + "@example.com").toList()));
    }

    @Test
    @DisplayName("a second call continues {n}, so the generated values stay unique")
    void continues_between_calls() {
        Generated first = users.generate(obj("fields", EMAILS, "count", 3));
        Generated second = users.generate(obj("fields", EMAILS, "count", 3));

        verify("second: firstN", second.firstN(), first.lastN() + 1);
        List<Map> all = users.properties(Search.all());
        verifyAll(v -> v
                .that("rows: size", all.size(), 6)
                .check("emails without duplicates", () -> assertThat(all).extracting(row -> row.get("email")).doesNotHaveDuplicates()));
    }

    @Test
    @DisplayName("startN sets the number of the first row")
    void start_n() {
        Generated generated = users.generate(obj("fields", obj("$.login", "tester-{n}"), "count", 2, "startN", 100));

        verify("firstN", generated.firstN(), 100);
        List<Map> all = users.properties(Search.all().sort("id"));
        verify("logins", all.stream().map(row -> row.get("login")).toList(), List.of("tester-100", "tester-101"));
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
        verifyAll(v -> v
                .that("rows: size", rows.size(), 50)
                .check("firstName: not blank", () -> assertThat(rows).allSatisfy(row ->
                        assertThat((String) row.get("firstName")).isNotBlank()))
                .check("phone: ###-###", () -> assertThat(rows).allSatisfy(row ->
                        assertThat((String) row.get("phone")).matches("\\d{3}-\\d{3}")))
                .check("age: 18..90", () -> assertThat(rows).allSatisfy(row ->
                        assertThat(((Number) row.get("age")).intValue()).isBetween(18, 90)))
                .check("balance: 0..100", () -> assertThat(rows).allSatisfy(row ->
                        assertThat(new BigDecimal(row.get("balance").toString())).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100))))
                .check("balance: at most 2 decimals", () -> assertThat(rows).allSatisfy(row ->
                        assertThat(new BigDecimal(row.get("balance").toString()).scale()).isLessThanOrEqualTo(2)))
                .check("status: active or blocked", () -> assertThat(rows).allSatisfy(row ->
                        assertThat(row.get("status")).isIn("active", "blocked")))
                .check("birthDate: in 1990", () -> assertThat(rows).allSatisfy(row ->
                        assertThat(LocalDate.parse((String) row.get("birthDate")))
                                .isBetween(LocalDate.of(1990, 1, 1), LocalDate.of(1990, 12, 31))))
                .check("lastLogin: in January 2025", () -> assertThat(rows).allSatisfy(row ->
                        assertThat(Instant.parse((String) row.get("lastLogin")))
                                .isBetween(Instant.parse("2025-01-01T00:00:00Z"), Instant.parse("2025-01-31T23:59:59Z"))))
                .check("address.city: not blank", () -> assertThat(rows).allSatisfy(row ->
                        assertThat((String) ((Map) row.get("address")).get("city")).isNotBlank()))
                .check("type: customer", () -> assertThat(rows).allSatisfy(row ->
                        assertThat(row.get("type")).isEqualTo("customer")))
                .check("active: true", () -> assertThat(rows).allSatisfy(row ->
                        assertThat(row.get("active")).isEqualTo(true)))
                .check("both statuses used", () -> assertThat(rows).extracting(row -> row.get("status"))
                        .contains("active", "blocked")));
    }

    @Test
    @DisplayName("sets the tag of the rows and reserves them for the caller")
    void tag_and_reservation(TestProject project) {
        Tag tag = project.quietly(ADMIN).settings().tags().create(new Tag().name("generated"));

        users.generate(obj("fields", EMAILS, "count", 4, "tagId", tag.id, "reserved", true));

        List<DataRow<Map>> rows = users.find(Search.all().limit(100)).data();
        String robot = project.user(ROBOT).username();
        verifyAll(v -> v
                .that("rows: size", rows.size(), 4)
                .check("tagId: the tag", () -> assertThat(rows).allSatisfy(row -> assertThat(row.tagId()).isEqualTo(tag.id)))
                .check("reserved", () -> assertThat(rows).allSatisfy(row -> assertThat(row.reserved()).isTrue()))
                .check("owner: the robot", () -> assertThat(rows).allSatisfy(row -> assertThat(row.owner()).isEqualTo(robot)))
                .check("createdBy: the robot", () -> assertThat(rows).allSatisfy(row -> assertThat(row.createdBy()).isEqualTo(robot))));
    }

    @Test
    @DisplayName("a preview inserts nothing and returns at most 10 rows")
    void preview_inserts_nothing() {
        Generated preview = users.previewGenerated(obj("fields", EMAILS, "count", 5000));

        verifyAll(v -> v
                .that("inserted", preview.inserted(), 0)
                .that("rows: size", preview.rows().size(), 10)
                .that("lastN", preview.lastN(), preview.firstN() + 9));
        long count = users.count(Search.all());
        verify("rows in the bucket", count, 0);
    }

    @Test
    @DisplayName("with the same seed the preview shows the rows that are then inserted")
    void preview_with_a_seed() {
        Map<String, Object> request = obj("count", 20, "seed", 42, "locale", "pl", "fields", obj(
                "$.name", "#{Name.fullName}", "$.age", obj("number", arr(18, 90)), "$.email", "user-{n}@example.com"));

        Generated preview = users.previewGenerated(request);
        users.generate(request);

        List<Map> inserted = users.properties(Search.all().sort("id"));
        verifyAll(v -> v
                .that("first 10 rows: as in the preview", inserted.subList(0, 10), preview.rows()));
    }

    @Test
    @DisplayName("an AI agent (AGENT) may generate data, as it may insert it")
    void agent_may_generate(TestProject project) {
        Generated generated = project.as(AGENT).bucket(BUCKET, Map.class).generate(obj("fields", EMAILS, "count", 2));

        verify("inserted", generated.inserted(), 2);
        long count = users.count(Search.all());
        verify("rows in the bucket", count, 2);
    }

    @ParameterizedTest(name = "count {0}")
    @ValueSource(ints = {0, -1, 10_001})
    @DisplayName("refuses a count outside 1..10000")
    void refuses_the_count(int count) {
        ApiResponse response = users.tryGenerate(obj("fields", EMAILS, "count", count));

        verifyAll(v -> v
                .that("status", response.status(), 400)
                .check("message names 1..10000", () -> assertThat(response.<String>path("message")).contains("1..10000")));
        long rows = users.count(Search.all());
        verify("rows in the bucket", rows, 0);
    }

    @Test
    @DisplayName("refuses an invalid generator with a message naming the field, and inserts nothing")
    void refuses_an_invalid_generator() {
        ApiResponse response = users.tryGenerate(obj("count", 5, "fields", obj(
                "$.email", "user-{n}@example.com", "$.age", obj("number", arr(18)))));

        verifyAll(v -> v
                .that("status", response.status(), 400)
                .check("message names $.age", () -> assertThat(response.<String>path("message")).contains("$.age")));
        long rows = users.count(Search.all());
        verify("rows in the bucket", rows, 0);
    }

    @Test
    @DisplayName("refuses a request without fields")
    void refuses_no_fields() {
        ApiResponse response = users.tryGenerate(obj("count", 5));

        verifyAll(v -> v
                .that("status", response.status(), 400)
                .check("message: at least one field", () -> assertThat(response.<String>path("message")).contains("at least one field")));
    }

    @Test
    @DisplayName("refuses a tag that doesn't exist, and inserts nothing")
    void refuses_an_unknown_tag() {
        ApiResponse response = users.tryGenerate(obj("fields", EMAILS, "count", 3, "tagId", 999_999_999));

        verifyAll(v -> v
                .that("status", response.status(), 400)
                .check("message: the tag doesn't exist", () -> assertThat(response.<String>path("message"))
                        .contains("tag").contains("doesn't exist")));
        long rows = users.count(Search.all());
        verify("rows in the bucket", rows, 0);
    }

    @Test
    void a_bucket_that_does_not_exist_gives_404(TestProject project) {
        ApiResponse response = project.as(ROBOT).bucket("no-such-bucket", Map.class)
                .tryGenerate(obj("fields", EMAILS, "count", 1));

        verify("status", response.status(), 404);
    }
}
