package pl.databucket.e2e.ui;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.model.DataRow;
import pl.databucket.e2e.pages.GenerateDataDialog;
import pl.databucket.e2e.pages.ProjectPage;
import pl.databucket.e2e.support.TestProject;
import pl.databucket.e2e.support.UiSuite;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.Screenshots.screenshot;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;

/** The dialog "Generate data" of the data grid (Databucket 3.20.0+). */
@UiSuite
@DisplayName("Generate data dialog")
@SuppressWarnings({"rawtypes", "unchecked"})
class GenerateDataDialogTest {

    private static final String BUCKET = "people";

    @BeforeEach
    void data(TestProject project) {
        long bucketId = project.createBucket(BUCKET);
        project.createView(bucketId, "all people", obj("email", "E-mail", "firstName", "First name", "city", "City"));
    }

    private static BucketApi<Map> people(TestProject project) {
        return project.as(ROBOT).bucket(BUCKET, Map.class);
    }

    @Test
    @DisplayName("the menu offers Generate data only in a view with the feature Generate")
    void only_with_the_feature(TestProject project, Page page) {
        long other = project.createBucket("orders");
        List<Integer> withoutGenerate = IntStream.rangeClosed(1, TestProject.ALL_FEATURES)
                .filter(id -> id != TestProject.FEATURE_GENERATE).boxed().toList();
        project.createView(other, "all orders", obj("customer", "Customer"), withoutGenerate);
        ProjectPage ui = new ProjectPage(page);
        ui.openAs(project, MEMBER);

        ui.openBucket(BUCKET);
        assertThat(ui.importExportMenu()).contains("Import CSV…", "Generate data…");
        assertThat(ui.importExportButton()).hasAccessibleName("Import / Export / Generate");

        ui.openBucket("orders");
        assertThat(ui.importExportMenu()).contains("Import CSV…").doesNotContain("Generate data…");
        assertThat(ui.importExportButton()).hasAccessibleName("Import / Export");
    }

    @Test
    @DisplayName("proposes the generators from the columns, previews the rows and generates them")
    void proposes_previews_and_generates(TestProject project, Page page) {
        ProjectPage ui = new ProjectPage(page);
        ui.openAs(project, MEMBER);
        ui.openBucket(BUCKET);

        GenerateDataDialog dialog = ui.openGenerateData();

        assertThat(dialog.generators()).hasCount(3);
        assertThat(dialog.settings("$.email")).hasValue("user-{n}@example.com");
        assertThat(dialog.settings("$.firstName")).hasValue("#{Name.firstName}");
        assertThat(dialog.settings("$.city")).hasValue("#{Address.city}");

        dialog.previewRows();
        assertThat(dialog.preview().locator("thead")).containsText("E-mail");
        assertThat(dialog.preview().locator("tbody tr")).hasCount(10);
        assertThat(dialog.preview().locator("tbody tr").first()).containsText("user-1@example.com");
        screenshot(page, "preview");
        assertThat(people(project).count(Search.all())).isZero();

        dialog.setRows(25);
        dialog.generate(25);

        assertThat(ui.pagination()).containsText("of 25");
        List<DataRow<Map>> rows = people(project).find(Search.all().limit(100)).data();
        assertThat(rows).hasSize(25).allSatisfy(row -> {
            assertThat(row.createdBy()).isEqualTo(project.user(MEMBER).username());
            assertThat(row.reserved()).isFalse();
            assertThat((String) row.properties().get("firstName")).isNotBlank();
            assertThat((String) row.properties().get("city")).isNotBlank();
        });
        assertThat(rows).extracting(row -> row.properties().get("email")).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("generates the properties as edited: one removed, one added")
    void edited_properties(TestProject project, Page page) {
        ProjectPage ui = new ProjectPage(page);
        ui.openAs(project, MEMBER);
        ui.openBucket(BUCKET);
        GenerateDataDialog dialog = ui.openGenerateData();

        dialog.remove("$.city");
        dialog.addProperty("$.code", "ID-{n}");
        dialog.setText("$.firstName", "Anna");
        dialog.setRows(3);
        dialog.generate(3);

        assertThat(people(project).properties(Search.all().sort("id"))).containsExactly(
                Map.of("email", "user-1@example.com", "firstName", "Anna", "code", "ID-1"),
                Map.of("email", "user-2@example.com", "firstName", "Anna", "code", "ID-2"),
                Map.of("email", "user-3@example.com", "firstName", "Anna", "code", "ID-3"));
    }

    @Test
    @DisplayName("shows why the server refused the generators and inserts nothing")
    void shows_the_refusal(TestProject project, Page page) {
        ProjectPage ui = new ProjectPage(page);
        ui.openAs(project, MEMBER);
        ui.openBucket(BUCKET);
        GenerateDataDialog dialog = ui.openGenerateData();

        dialog.setText("$.city", "#{NoSuchProvider.city}");
        dialog.tryGenerate();

        assertThat(dialog.error()).containsText("Nothing has been generated");
        assertThat(dialog.error()).containsText("$.city");
        assertThat(dialog.dialog()).isVisible();
        screenshot(page, "refused");
        assertThat(people(project).count(Search.all())).isZero();
    }
}
