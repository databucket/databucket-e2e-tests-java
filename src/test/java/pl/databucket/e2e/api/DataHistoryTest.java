package pl.databucket.e2e.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Change;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.model.HistoryPage;
import pl.databucket.e2e.model.RowHistory;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.client.Rules.logic;
import static pl.databucket.e2e.client.Rules.var;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Verify.verifyAll;

@ApiSuite
@DisplayName("Data history")
class DataHistoryTest {

    private static final String BUCKET = "orders";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Order(String status, String customer) {
    }

    @BeforeEach
    void bucket(TestProject project) {
        project.createBucket(BUCKET, true, false);
    }

    @Test
    void records_the_changes_of_a_row(TestProject project) {
        BucketApi<Order> orders = project.as(ROBOT).bucket(BUCKET, Order.class);
        long id = orders.insert(new Order("NEW", null)).id();
        orders.update(Change.set("$.status", "PAID"), id);

        List<RowHistory> history = orders.history(id);
        verifyAll(v -> v
                .that("history: size", history.size(), 2)
                .check("modified by the robot", () -> assertThat(history).extracting(RowHistory::modifiedBy)
                        .containsOnly(project.user(ROBOT).username())));
    }

    @Test
    void searches_the_history_of_the_rows_matching_the_rules(TestProject project) {
        BucketApi<Order> orders = project.as(ROBOT).bucket(BUCKET, Order.class);
        long paid = orders.insert(new Order("NEW", "Alice")).id();
        orders.insert(new Order("NEW", "Bob"));
        orders.update(Change.set("$.status", "PAID"), paid);

        HistoryPage<Order> search = orders.searchHistory(Search.all()
                .logic(logic("==", var("$.customer"), "Alice")).limit(10).withProperties());

        verifyAll(v -> v
                .that("total", search.total(), 2)
                .check("entries of Alice's row only", () -> assertThat(search.entries())
                        .extracting(HistoryPage.Entry::dataId).containsOnly(paid)));
    }
}
