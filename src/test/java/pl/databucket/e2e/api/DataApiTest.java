package pl.databucket.e2e.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Change;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.model.DataPage;
import pl.databucket.e2e.model.DataRow;
import pl.databucket.e2e.model.Reservation;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.client.Rules.logic;
import static pl.databucket.e2e.client.Rules.var;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

@ApiSuite
@Area(Area.DATA)
@DisplayName("Data API")
class DataApiTest {

    private static final String BUCKET = "orders";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Order(String customer, String status, Double total) {
        Order(String customer, String status) {
            this(customer, status, null);
        }
    }

    record Login(String login) {
    }

    @BeforeEach
    void bucket(TestProject project) {
        project.createBucket(BUCKET);
    }

    @Test
    void inserts_a_row_and_reads_it_by_id(TestProject project) {
        BucketApi<Order> orders = project.as(ROBOT).bucket(BUCKET, Order.class);

        DataRow<Order> inserted = orders.insert(new Order("Alice", "NEW", 120.5));
        verifyAll(v -> v
                .that("properties", inserted.properties(), new Order("Alice", "NEW", 120.5))
                .that("reserved", inserted.reserved(), false));

        DataRow<Order> read = orders.get(inserted.id()).orElseThrow();
        verifyAll(v -> v
                .that("id", read.id(), inserted.id())
                .that("properties.customer", read.properties().customer(), "Alice")
                .that("createdBy", read.createdBy(), project.user(ROBOT).username()));
    }

    @Test
    @DisplayName("in a bucket with protected data a member inserts a free row and gets it back, but sees only own rows later")
    void protected_data(TestProject project) {
        project.createBucket("secrets", true, true);
        BucketApi<Login> secrets = project.as(MEMBER).bucket("secrets", Login.class);

        DataRow<Login> inserted = secrets.insert(new Login("erin"));
        verifyAll(v -> v
                .that("reserved", inserted.reserved(), false)
                .that("properties.login", inserted.properties().login(), "erin"));

        // not reserved by the member, so hidden from them afterwards (as an id that doesn't exist: no rows)
        check("the row is not found", () -> assertThat(secrets.get(inserted.id())).isEmpty());
    }

    @Test
    @DisplayName("finds rows by JsonLogic rules with paging, sorting and counting")
    void finds_rows(TestProject project) {
        BucketApi<Order> orders = project.as(ROBOT).bucket(BUCKET, Order.class);
        orders.insertAll(new Order("Alice", "NEW", 10.0), new Order("Bob", "PAID", 20.0),
                new Order("Carol", "NEW", 30.0), new Order("Dave", "NEW", 40.0));

        long count = orders.count(Search.all().logic(logic("==", var("$.status"), "NEW")));
        verify("count", count, 3);

        DataPage<Order> page = orders.find(Search.all().logic(logic("==", var("$.status"), "NEW"))
                .limit(2).page(1).sort("desc($.total)"));
        verify("customers", page.properties().stream().map(Order::customer).toList(), java.util.List.of("Dave", "Carol"));
    }

    @Test
    void reserves_free_rows_only_once(TestProject project) {
        BucketApi<Order> orders = project.as(ROBOT).bucket(BUCKET, Order.class);
        orders.insertAll(new Order("Alice", null), new Order("Bob", null));

        Reservation<Order> first = orders.reserve(Search.all().limit(1));
        Reservation<Order> second = orders.reserve(Search.all().limit(5));
        Reservation<Order> third = orders.reserve(Search.all().limit(1));

        verifyAll(v -> v
                .that("first: rows", first.rows().size(), 1)
                .that("second: rows", second.rows().size(), 1)
                .check("second: other rows than the first", () -> assertThat(second.ids()).doesNotContainAnyElementsOf(first.ids()))
                .check("first: reserved", () -> assertThat(first.rows()).extracting(DataRow::reserved).containsOnly(true))
                .check("second: owned by the robot", () -> assertThat(second.rows()).extracting(DataRow::owner)
                        .containsOnly(project.user(ROBOT).username()))
                .that("third: rows", third.rows().size(), 0));
    }

    @Test
    @DisplayName("updates one property, keeping the others, and deletes rows")
    void updates_and_deletes(TestProject project) {
        BucketApi<Order> orders = project.as(MEMBER).bucket(BUCKET, Order.class);
        long id = orders.insert(new Order("Alice", "NEW")).id();

        orders.update(Change.set("$.status", "PAID"), id);
        orders.get(id, new Order("Alice", "PAID"));

        orders.delete(id);
        // a row that doesn't exist (any more) comes back as an empty list
        check("the row is not found", () -> assertThat(orders.get(id)).isEmpty());
    }

    @Test
    @DisplayName("still changes rows with PUT, the method before PATCH (by ids and by rules)")
    void changes_rows_with_the_old_put(TestProject project) {
        BucketApi<Order> orders = project.as(MEMBER).bucket(BUCKET, Order.class);
        long id = orders.insert(new Order("Alice", "NEW")).id();

        project.as(MEMBER).put("/api/bucket/" + BUCKET + "/" + id,
                call().body(obj("propertiesToSet", obj("$.status", "PAID"))).expect(200));
        orders.get(id, new Order("Alice", "PAID"));

        project.as(MEMBER).put("/api/bucket/" + BUCKET,
                call().body(obj("propertiesToSet", obj("$.status", "SHIPPED"), "rules", java.util.List.of(java.util.List.of("id", "==", id))))
                        .expect(200));
        orders.get(id, new Order("Alice", "SHIPPED"));
    }
}
