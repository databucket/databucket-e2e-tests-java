package pl.databucket.e2e.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Change;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.model.HistoryPage;
import pl.databucket.e2e.model.HistoryVersion;
import pl.databucket.e2e.model.RowHistory;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.client.Rules.rule;
import static pl.databucket.e2e.support.UserKey.AGENT;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

@ApiSuite
@DisplayName("History of changes")
class HistoryChangesTest {

    private static final String BUCKET = "orders";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Order(String status, String customer, String login) {
        static Order status(String status) {
            return new Order(status, null, null);
        }
    }

    private BucketApi<Order> orders;

    @BeforeEach
    void bucket(TestProject project) {
        project.createBucket(BUCKET, true, false);
        orders = project.as(ROBOT).bucket(BUCKET, Order.class);
    }

    private static void setStatus(BucketApi<Order> bucket, long id, String status) {
        bucket.update(Change.set("$.status", status), id);
    }

    @Test
    void gives_the_properties_of_a_row_as_they_were_in_each_version(TestProject project) {
        long id = orders.insert(Order.status("NEW")).id();
        setStatus(orders, id, "PAID");
        setStatus(orders, id, "SHIPPED");
        List<Long> entries = orders.history(id).stream().map(RowHistory::id).toList();

        List<HistoryVersion<Order>> versions = orders.versions(id, entries);

        verify("statuses", versions.stream().map(version -> version.properties().status()).toList(), List.of("NEW", "PAID", "SHIPPED"));
    }

    @Test
    void records_reservations_and_their_owner(TestProject project) {
        long id = orders.insert(new Order(null, null, "anna")).id();
        project.quietly(MEMBER).bucket(BUCKET, Order.class).reserve(Search.all().limit(1));

        List<RowHistory> entries = orders.history(id);

        verifyAll(v -> v
                .that("entries: size", entries.size(), 2)
                .check("second entry: reserved", () -> assertThat(entries.get(1).reserved()).isTrue())
                .check("second entry: modifiedBy", () -> assertThat(entries.get(1).modifiedBy()).isEqualTo(project.user(MEMBER).username())));
    }

    @Test
    @DisplayName("a ROBOT clears the history of a row; the row stays")
    void robot_clears(TestProject project) {
        long id = orders.insert(Order.status("NEW")).id();
        setStatus(orders, id, "PAID");

        orders.clearHistory(id);

        verify("history entries", orders.history(id).size(), 0);
        verify("status", orders.get(id).orElseThrow().properties().status(), "PAID");
    }

    @Test
    @DisplayName("an AGENT can not clear the history")
    void agent_can_not_clear(TestProject project) {
        long id = orders.insert(Order.status("NEW")).id();
        BucketApi<Order> asAgent = project.as(AGENT).bucket(BUCKET, Order.class);

        verify("status", asAgent.tryClearHistory(id).status(), 403);
        verify("status", asAgent.tryClearHistoryWhere(Search.where(rule("id", "==", id))).status(), 403);
        verify("history entries", orders.history(id).size(), 1);
    }

    @Test
    void clears_the_history_of_the_rows_matching_the_rules_only(TestProject project) {
        long paid = orders.insert(new Order("NEW", "Alice", null)).id();
        long other = orders.insert(new Order("NEW", "Bob", null)).id();
        setStatus(orders, paid, "PAID");
        setStatus(orders, other, "PAID");

        orders.clearHistoryWhere(Search.where(rule("$.customer", "==", "Alice")));

        verify("history entries of Alice", orders.history(paid).size(), 0);
        verify("history entries of Bob", orders.history(other).size(), 2);
    }

    @Test
    void a_bucket_without_history_keeps_no_entries(TestProject project) {
        project.createBucket("plain", false, false);
        BucketApi<Order> plain = project.as(ROBOT).bucket("plain", Order.class);
        long id = plain.insert(Order.status("NEW")).id();
        setStatus(plain, id, "PAID");

        verify("history entries", plain.history(id).size(), 0);
    }

    @Test
    void searches_the_entries_by_who_changed_the_rows(TestProject project) {
        long id = orders.insert(Order.status("NEW")).id();
        setStatus(project.quietly(MEMBER).bucket(BUCKET, Order.class), id, "PAID");
        setStatus(orders, id, "SHIPPED");
        String member = project.user(MEMBER).username();

        HistoryPage<Order> byMember = orders.searchHistory(Search.history(rule("modifiedBy", "==", member)).limit(10));

        verifyAll(v -> v
                .that("total", byMember.total(), 1)
                .check("first entry: dataId", () -> assertThat(byMember.entries().get(0).dataId()).isEqualTo(id))
                .check("first entry: modifiedBy", () -> assertThat(byMember.entries().get(0).modifiedBy()).isEqualTo(member)));
    }

    @Test
    void removing_a_row_removes_its_history(TestProject project) {
        long id = orders.insert(Order.status("NEW")).id();
        setStatus(orders, id, "PAID");

        orders.delete(id);

        verify("total", orders.searchHistory(Search.history(rule("dataId", "==", id)).limit(10)).total(), 0);
    }
}
