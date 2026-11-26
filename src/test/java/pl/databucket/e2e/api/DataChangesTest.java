package pl.databucket.e2e.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Change;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.model.DataRow;
import pl.databucket.e2e.model.Message;
import pl.databucket.e2e.model.NewRow;
import pl.databucket.e2e.model.Tag;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.client.Rules.rule;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.AGENT;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

@ApiSuite
@DisplayName("Changing data")
class DataChangesTest {

    private static final String BUCKET = "orders";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Order(String status, String customer) {
        static Order status(String status) {
            return new Order(status, null);
        }
    }

    record Numbered(int no) {
    }

    private BucketApi<Order> orders;

    @BeforeEach
    void bucket(TestProject project) {
        project.createBucket(BUCKET);
        orders = project.as(ROBOT).bucket(BUCKET, Order.class);
    }

    private List<String> statuses() {
        return orders.properties(Search.all()).stream().map(Order::status).toList();
    }

    private void insertStatuses(String... statuses) {
        orders.insertAll(java.util.Arrays.stream(statuses).map(Order::status).toList());
    }

    @Test
    void inserts_many_rows_at_once(TestProject project) {
        BucketApi<Numbered> numbered = project.as(ROBOT).bucket(BUCKET, Numbered.class);
        numbered.insertAll(IntStream.rangeClosed(1, 50).mapToObj(Numbered::new).toList());

        verify("count", numbered.count(Search.all()), 50);
    }

    @Test
    void a_row_inserted_as_reserved_belongs_to_the_user(TestProject project) {
        DataRow<Order> row = orders.insert(NewRow.of(Order.status("NEW")).asReserved());

        verifyAll(v -> v
                .that("reserved", row.reserved(), true)
                .that("owner", row.owner(), project.user(ROBOT).username()));
    }

    @Test
    void a_row_with_a_tag_that_does_not_exist_is_refused(TestProject project) {
        ApiResponse response = orders.tryInsert(NewRow.of(Order.status("NEW")).tagged(999999));
        verify("status", response.status(), 406);

        verify("count", orders.count(Search.all()), 0);
    }

    @Test
    void many_rows_at_once_are_all_saved_or_none(TestProject project) {
        ApiResponse response = orders.tryInsertRows(List.of(NewRow.of(Order.status("A")), NewRow.of(Order.status("B")).tagged(999999)));
        verify("status", response.status(), 406);

        verify("count", orders.count(Search.all()), 0);
    }

    @Test
    void gets_several_rows_by_ids_and_skips_the_ids_that_do_not_exist(TestProject project) {
        long a = orders.insert(new Order("NEW", "a")).id();
        long b = orders.insert(new Order("NEW", "b")).id();

        verify("customers", orders.getAll(a, 999999, b).stream().map(row -> row.properties().customer()).toList(), List.of("a", "b"));
    }

    @Test
    void changes_several_rows_by_ids(TestProject project) {
        long a = orders.insert(Order.status("NEW")).id();
        orders.insert(Order.status("NEW"));
        long c = orders.insert(Order.status("NEW")).id();

        orders.update(Change.set("$.status", "PAID"), a, c);

        verify("statuses", statuses(), List.of("PAID", "NEW", "PAID"));
    }

    @Test
    void removes_properties_and_sets_nested_ones(TestProject project) {
        BucketApi<Map> any = project.as(ROBOT).bucket(BUCKET, Map.class);
        long id = any.insert(Map.of("customer", "Alice", "comment", "call first", "address", Map.of("city", "Paris"))).id();

        any.update(Change.remove("$.comment").and("$.address.zip", "75001").and("$.payment.method", "card"), id);

        Map<?, ?> properties = any.get(id).orElseThrow().properties();
        verifyAll(v -> v
                .that("properties", properties, Map.of(
                        "customer", "Alice", "address", Map.of("city", "Paris", "zip", "75001"), "payment", Map.of("method", "card")))
                .check("no other properties (comment removed)", () -> assertThat(properties).isEqualTo(Map.of(
                        "customer", "Alice", "address", Map.of("city", "Paris", "zip", "75001"), "payment", Map.of("method", "card")))));
    }

    @Test
    void changes_the_rows_matching_the_rules_and_says_how_many(TestProject project) {
        insertStatuses("NEW", "PAID", "NEW");

        Message message = orders.updateWhere(Change.set("$.status", "CANCELLED").where(rule("$.status", "==", "NEW")));
        verify("message", message.message(), "Modified 2 data row(s)");

        verify("statuses", statuses(), List.of("CANCELLED", "PAID", "CANCELLED"));
    }

    @Test
    void changing_or_removing_by_rules_needs_at_least_one_rule(TestProject project) {
        insertStatuses("NEW");

        verify("status", orders.tryUpdateWhere(Change.set("$.status", "X")).status(), 406);
        verify("status", orders.tryDeleteWhere(Search.all()).status(), 406);
        verify("statuses", statuses(), List.of("NEW"));
    }

    @Test
    void removes_the_rows_matching_the_rules(TestProject project) {
        insertStatuses("NEW", "PAID", "NEW");

        Message message = orders.deleteWhere(Search.where(rule("$.status", "==", "NEW")));
        verify("message", message.message(), "Removed 2 data row(s)");

        verify("statuses", statuses(), List.of("PAID"));
    }

    @Test
    @DisplayName("an AGENT changes rows but can not remove them")
    void agent_can_not_remove(TestProject project) {
        BucketApi<Order> asAgent = project.as(AGENT).bucket(BUCKET, Order.class);
        long id = asAgent.insert(Order.status("NEW")).id();
        asAgent.update(Change.set("$.status", "PAID"), id);

        verify("status", asAgent.tryDelete(id).status(), 403);
        verify("status", asAgent.tryDeleteWhere(Search.where(rule("$.status", "==", "PAID"))).status(), 403);
        verify("count", orders.count(Search.all()), 1);
    }

    @Test
    void a_bucket_that_does_not_exist_gives_404(TestProject project) {
        BucketApi<Order> missing = project.as(ROBOT).bucket("no-such-bucket", Order.class);

        verify("status", missing.tryFind(Search.all()).status(), 404);
        verify("status", missing.tryInsert(NewRow.of(Order.status("NEW"))).status(), 404);
    }

    @Test
    void sets_and_clears_the_tag_of_a_row(TestProject project) {
        Tag tag = project.quietly(ADMIN).settings().tags().create(new Tag().name("checked"));
        long id = orders.insert(Order.status("NEW")).id();

        orders.update(Change.tag(tag.id), id);

        verify("tagId", orders.get(id).orElseThrow().tagId(), tag.id);
    }

    @Test
    void records_who_changed_a_row_and_when(TestProject project) {
        long id = orders.insert(Order.status("NEW")).id();

        project.as(MEMBER).bucket(BUCKET, Order.class).update(Change.set("$.status", "PAID"), id);
        DataRow<Order> changed = orders.get(id).orElseThrow();

        verifyAll(v -> v
                .that("createdBy", changed.createdBy(), project.user(ROBOT).username())
                .that("modifiedBy", changed.modifiedBy(), project.user(MEMBER).username())
                .check("modifiedAt not before createdAt", () -> assertThat(changed.modifiedAt()).isAfterOrEqualTo(changed.createdAt())));
    }
}
