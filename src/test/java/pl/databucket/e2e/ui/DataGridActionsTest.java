package pl.databucket.e2e.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Change;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.model.DataRow;
import pl.databucket.e2e.pages.DataHistoryDialog;
import pl.databucket.e2e.pages.ProjectPage;
import pl.databucket.e2e.pages.RichFilterDialog;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.TestProject;
import pl.databucket.e2e.support.UiSuite;

import java.util.List;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.client.Rules.rule;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

/** The actions of the data grid on its rows: edit, delete, reserve, history, details, sort, rich filter. */
@UiSuite
@Area(Area.DATA)
@DisplayName("Data grid actions")
@SuppressWarnings({"rawtypes", "unchecked"})
class DataGridActionsTest {

    private static final String BUCKET = "orders";
    // the cells of a grid row: checkbox, actions, Id, Reserved, Owner, Customer, Status, Modified at
    private static final int CUSTOMER = 5;

    // inserted in an order other than by name, so that sorting by Customer differs from sorting by id
    @BeforeEach
    void data(TestProject project) {
        long bucketId = project.createBucket(BUCKET);
        project.createView(bucketId, "all orders", obj("customer", "Customer", "status", "Status"));
        orders(project).insertAll(
                Map.of("customer", "Bob", "status", "PAID"),
                Map.of("customer", "Carol", "status", "NEW"),
                Map.of("customer", "Alice", "status", "NEW"));
    }

    private static BucketApi<Map> orders(TestProject project) {
        return project.as(ROBOT).bucket(BUCKET, Map.class);
    }

    private static DataRow<Map> rowOf(TestProject project, String customer) {
        List<DataRow<Map>> rows = orders(project).find(Search.where(rule("$.customer", "==", customer))).data();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static ProjectPage open(TestProject project, Page page) {
        ProjectPage ui = new ProjectPage(page);
        ui.openAs(project, MEMBER);
        ui.openBucket(BUCKET);
        return ui;
    }

    @Test
    void edits_a_row_in_the_grid(TestProject project, Page page) {
        ProjectPage ui = open(project, page);

        ui.editRow("Bob", obj("Status", "SHIPPED"));

        check("Bob: SHIPPED", () -> assertThat(ui.row("Bob")).containsText("SHIPPED"));
        DataRow<Map> bob = rowOf(project, "Bob");
        verifyAll(v -> v
                .that("properties", bob.properties(), Map.of("customer", "Bob", "status", "SHIPPED"))
                .that("modifiedBy", bob.modifiedBy(), project.user(MEMBER).username()));
    }

    @Test
    void deletes_a_row_after_the_question(TestProject project, Page page) {
        ProjectPage ui = open(project, page);

        ui.deleteRow("Bob");

        verifyAll(v -> v
                .check("Bob is gone", () -> assertThat(ui.row("Bob")).isHidden())
                .check("pagination: 1-2 of 2", () -> assertThat(ui.pagination()).hasText("1-2 of 2")));
        verifyAll(v -> v
                .that("count", orders(project).count(Search.all()), 2)
                .check("no row of Bob", () -> assertThat(rowOf(project, "Bob")).isNull()));
    }

    @Test
    void reserves_and_releases_the_selected_rows(TestProject project, Page page) {
        ProjectPage ui = open(project, page);
        String member = project.user(MEMBER).username();

        ui.select("Alice", "Carol");
        ui.reserveSelected(true);

        verifyAll(v -> v
                .check("the message says 2 rows reserved", () -> assertThat(ui.message()).containsText("Reserved 2 data row(s)"))
                .check("Alice: owner is the member", () -> assertThat(ui.row("Alice")).containsText(member))
                .check("Carol: owner is the member", () -> assertThat(ui.row("Carol")).containsText(member))
                .check("Bob: no owner", () -> assertThat(ui.row("Bob")).not().containsText(member)));
        DataRow<Map> alice = rowOf(project, "Alice");
        DataRow<Map> bob = rowOf(project, "Bob");
        verifyAll(v -> v
                .that("Alice reserved", alice.reserved(), true)
                .that("Alice owner", alice.owner(), member)
                .that("Bob reserved", bob.reserved(), false));

        ui.select("Alice");
        ui.reserveSelected(false);

        check("Alice: no owner", () -> assertThat(ui.row("Alice")).not().containsText(member));
        verifyAll(v -> v
                .that("Alice reserved", rowOf(project, "Alice").reserved(), false)
                .that("Carol reserved", rowOf(project, "Carol").reserved(), true));
    }

    @Test
    void deletes_the_selected_rows_after_the_question(TestProject project, Page page) {
        ProjectPage ui = open(project, page);

        ui.select("Alice", "Bob");
        ui.deleteSelected();

        verifyAll(v -> v
                .check("the message says deleted", () -> assertThat(ui.message()).containsText("Deleted"))
                .check("pagination: 1-1 of 1", () -> assertThat(ui.pagination()).hasText("1-1 of 1"))
                .check("Carol is shown", () -> assertThat(ui.row("Carol")).isVisible()));
        verify("rows left", orders(project).properties(Search.all()), List.of(Map.of("customer", "Carol", "status", "NEW")));
    }

    @Test
    void shows_the_history_of_a_row(TestProject project, Page page) {
        long bobId = rowOf(project, "Bob").id();
        orders(project).update(Change.set("$.status", "SHIPPED"), bobId);
        ProjectPage ui = open(project, page);

        DataHistoryDialog history = ui.openHistory("Bob");

        String robot = project.user(ROBOT).username();
        verifyAll(v -> v
                .check("the title has the id", () -> assertThat(history.dialog()).containsText("Data history [Id: " + bobId + "]"))
                .check("2 changes: insert and update", () -> assertThat(history.changes()).hasCount(2))
                .check("both by the robot", () -> assertThat(history.changes().filter(
                        new Locator.FilterOptions().setHasText(robot))).hasCount(2)));
    }

    @Test
    void shows_the_details_of_a_row(TestProject project, Page page) {
        long aliceId = rowOf(project, "Alice").id();
        ProjectPage ui = open(project, page);

        Locator details = ui.openDetails("Alice");

        verifyAll(v -> v
                .check("the id", () -> assertThat(details.getByRole(com.microsoft.playwright.options.AriaRole.CELL)
                        .first()).hasText(String.valueOf(aliceId)))
                .check("created by the robot", () -> assertThat(details).containsText(project.user(ROBOT).username()))
                .check("the properties as JSON: customer", () -> assertThat(details).containsText("\"customer\": \"Alice\""))
                .check("the properties as JSON: status", () -> assertThat(details).containsText("\"status\": \"NEW\"")));
    }

    @Test
    void sorts_by_a_column(TestProject project, Page page) {
        ProjectPage ui = open(project, page);
        verify("by id descending (the view's sort)", customers(ui, page, List.of("Alice", "Carol", "Bob")),
                List.of("Alice", "Carol", "Bob"));

        ui.sortBy("Customer");
        verify("by Customer ascending", customers(ui, page, List.of("Alice", "Bob", "Carol")), List.of("Alice", "Bob", "Carol"));

        ui.sortBy("Customer");
        verify("by Customer descending", customers(ui, page, List.of("Carol", "Bob", "Alice")), List.of("Carol", "Bob", "Alice"));
    }

    // the customers of the grid once they are the expected ones (the grid reloads), or after 10 s as they are
    private static List<String> customers(ProjectPage ui, Page page, List<String> expected) {
        long until = System.currentTimeMillis() + 10_000;
        List<String> shown = ui.column(CUSTOMER);
        while (!shown.equals(expected) && System.currentTimeMillis() < until) {
            page.waitForTimeout(200);
            shown = ui.column(CUSTOMER);
        }
        return shown;
    }

    @Test
    void filters_with_a_rule_of_the_rich_filter(TestProject project, Page page) {
        long bobId = rowOf(project, "Bob").id();
        ProjectPage ui = open(project, page);

        RichFilterDialog filter = ui.openRichFilter();
        filter.addNumberRule("Id", bobId);
        check("1 data row matches", () -> assertThat(filter.matches()).hasText("1 data row matches the rules"));
        filter.showApiRequest();
        check("the API request has the rule", () -> assertThat(filter.apiBody())
                .containsText("\"id\", \"==\", " + bobId));
        filter.apply();
        filter.close();

        verifyAll(v -> v
                .check("pagination: 1-1 of 1", () -> assertThat(ui.pagination()).hasText("1-1 of 1"))
                .check("Bob is shown", () -> assertThat(ui.row("Bob")).isVisible())
                .check("Alice is hidden", () -> assertThat(ui.row("Alice")).isHidden()));
    }
}
