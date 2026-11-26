package pl.databucket.e2e.ui;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.pages.ProjectPage;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.TestProject;
import pl.databucket.e2e.support.UiSuite;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.arr;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.Screenshots.screenshot;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;

@UiSuite
@DisplayName("Bucket data grid")
class BucketDataGridTest {

    private static final String BUCKET = "orders";

    @BeforeEach
    void data(TestProject project) {
        long bucketId = project.createBucket(BUCKET);
        project.createView(bucketId, "all orders", obj("customer", "Customer", "status", "Status"));
        project.quietly(ROBOT).post("/api/bucket/" + BUCKET + "/multi", call().body(arr(
                obj("properties", obj("customer", "Alice", "status", "NEW")),
                obj("properties", obj("customer", "Bob", "status", "PAID")),
                obj("properties", obj("customer", "Carol", "status", "NEW"))
        )).expect(201));
    }

    @Test
    void shows_the_rows_inserted_through_the_API(TestProject project, Page page) {
        ProjectPage ui = new ProjectPage(page);
        ui.openAs(project, MEMBER);

        ui.openBucket(BUCKET);

        assertThat(ui.row("Alice")).containsText("NEW");
        assertThat(ui.row("Bob")).containsText("PAID");
        assertThat(ui.pagination()).hasText("1-3 of 3");
    }

    @Test
    void searches_the_properties(TestProject project, Page page) {
        ProjectPage ui = new ProjectPage(page);
        ui.openAs(project, MEMBER);
        ui.openBucket(BUCKET);

        ui.search("Bob");

        assertThat(ui.pagination()).hasText("1-1 of 1");
        assertThat(ui.row("Bob")).isVisible();
        assertThat(ui.row("Alice")).isHidden();
    }

    @Test
    void adds_a_row_that_the_API_then_finds(TestProject project, Page page) {
        ProjectPage ui = new ProjectPage(page);
        ui.openAs(project, MEMBER);
        ui.openBucket(BUCKET);

        ui.addRow(obj("Customer", "Dave", "Status", "NEW"));

        assertThat(ui.row("Dave")).isVisible();
        screenshot(page, "row added");
        Api member = project.as(MEMBER);
        ApiResponse found = member.post("/api/bucket/" + BUCKET + "/get?limit=10", call()
                .body(obj("logic", obj("==", arr(obj("var", "$.customer"), "Dave"))))
                .expect(200));
        assertThat(found.<List<?>>path("data")).hasSize(1);
        assertThat(found.<String>path("data[0].properties.customer")).isEqualTo("Dave");
        assertThat(found.<String>path("data[0].properties.status")).isEqualTo("NEW");
        assertThat(found.<String>path("data[0].createdBy")).isEqualTo(project.user(MEMBER).username());
    }
}
