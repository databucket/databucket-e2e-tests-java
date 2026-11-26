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
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.client.Rules.logic;
import static pl.databucket.e2e.client.Rules.var;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;

@ApiSuite
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
        assertThat(inserted.properties()).isEqualTo(new Order("Alice", "NEW", 120.5));
        assertThat(inserted.reserved()).isFalse();

        DataRow<Order> read = orders.get(inserted.id()).orElseThrow();
        assertThat(read.id()).isEqualTo(inserted.id());
        assertThat(read.properties().customer()).isEqualTo("Alice");
        assertThat(read.createdBy()).isEqualTo(project.user(ROBOT).username());
    }

    @Test
    @DisplayName("in a bucket with protected data a member inserts a free row and gets it back, but sees only own rows later")
    void protected_data(TestProject project) {
        project.createBucket("secrets", true, true);
        BucketApi<Login> secrets = project.as(MEMBER).bucket("secrets", Login.class);

        DataRow<Login> inserted = secrets.insert(new Login("erin"));
        assertThat(inserted.reserved()).isFalse();
        assertThat(inserted.properties().login()).isEqualTo("erin");

        // not reserved by the member, so hidden from them afterwards (as an id that doesn't exist: no rows)
        assertThat(secrets.get(inserted.id())).isEmpty();
    }

    @Test
    @DisplayName("finds rows by JsonLogic rules with paging, sorting and counting")
    void finds_rows(TestProject project) {
        BucketApi<Order> orders = project.as(ROBOT).bucket(BUCKET, Order.class);
        orders.insertAll(new Order("Alice", "NEW", 10.0), new Order("Bob", "PAID", 20.0),
                new Order("Carol", "NEW", 30.0), new Order("Dave", "NEW", 40.0));

        assertThat(orders.count(Search.all().logic(logic("==", var("$.status"), "NEW")))).isEqualTo(3);

        DataPage<Order> page = orders.find(Search.all().logic(logic("==", var("$.status"), "NEW"))
                .limit(2).page(1).sort("desc($.total)"));
        assertThat(page.properties()).extracting(Order::customer).containsExactly("Dave", "Carol");
    }

    @Test
    void reserves_free_rows_only_once(TestProject project) {
        BucketApi<Order> orders = project.as(ROBOT).bucket(BUCKET, Order.class);
        orders.insertAll(new Order("Alice", null), new Order("Bob", null));

        Reservation<Order> first = orders.reserve(Search.all().limit(1));
        Reservation<Order> second = orders.reserve(Search.all().limit(5));
        Reservation<Order> third = orders.reserve(Search.all().limit(1));

        assertThat(first.rows()).hasSize(1);
        assertThat(second.rows()).hasSize(1);
        assertThat(second.ids()).doesNotContainAnyElementsOf(first.ids());
        assertThat(first.rows()).extracting(DataRow::reserved).containsOnly(true);
        assertThat(second.rows()).extracting(DataRow::owner).containsOnly(project.user(ROBOT).username());
        assertThat(third.rows()).isEmpty();
    }

    @Test
    @DisplayName("updates one property, keeping the others, and deletes rows")
    void updates_and_deletes(TestProject project) {
        BucketApi<Order> orders = project.as(MEMBER).bucket(BUCKET, Order.class);
        long id = orders.insert(new Order("Alice", "NEW")).id();

        orders.update(Change.set("$.status", "PAID"), id);
        assertThat(orders.get(id).orElseThrow().properties()).isEqualTo(new Order("Alice", "PAID"));

        orders.delete(id);
        // a row that doesn't exist (any more) comes back as an empty list
        assertThat(orders.get(id)).isEmpty();
    }
}
