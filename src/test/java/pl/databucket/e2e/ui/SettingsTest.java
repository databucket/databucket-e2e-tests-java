package pl.databucket.e2e.ui;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.Settings;
import pl.databucket.e2e.model.Bucket;
import pl.databucket.e2e.model.DataClass;
import pl.databucket.e2e.model.DataEnum;
import pl.databucket.e2e.model.Group;
import pl.databucket.e2e.model.ProjectUser;
import pl.databucket.e2e.model.Svg;
import pl.databucket.e2e.model.Tag;
import pl.databucket.e2e.model.Team;
import pl.databucket.e2e.pages.ProjectPage;
import pl.databucket.e2e.pages.SettingsPage;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.DatabucketIcons;
import pl.databucket.e2e.support.TestProject;
import pl.databucket.e2e.support.UiSuite;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

/** The project settings in the GUI (as ADMIN), each change read back through the API. */
@UiSuite
@Area(Area.SETTINGS)
@DisplayName("Settings")
class SettingsTest {

    private static final String BUCKET = "orders";

    private long bucketId;

    @BeforeEach
    void bucket(TestProject project) {
        bucketId = project.createBucket(BUCKET);
        project.createView(bucketId, "all orders", obj("number", "Number", "status", "Status"));
    }

    private static Settings settings(TestProject project) {
        return project.as(ADMIN).settings();
    }

    private static SettingsPage open(TestProject project, Page page, String tab) {
        new ProjectPage(page).openAs(project, ADMIN, "/settings/" + tab.toLowerCase());
        SettingsPage settings = new SettingsPage(page);
        settings.waitUntilLoaded(tab);
        return settings;
    }

    @Test
    void adds_changes_and_deletes_a_tag_of_a_bucket(TestProject project, Page page) {
        SettingsPage ui = open(project, page, "Tags");

        ui.add(obj("Name", "urgent", "Description", "to do first"));
        ui.select("Select buckets", BUCKET);
        ui.save();

        check("the row of urgent", () -> assertThat(ui.row("urgent")).containsText("to do first"));
        Tag tag = settings(project).tags().list().get(0);
        verifyAll(v -> v
                .that("name", tag.name, "urgent")
                .that("description", tag.description, "to do first")
                .that("bucketsIds", tag.bucketsIds, Set.of(bucketId)));

        ui.edit("urgent", obj("Description", "today"));
        ui.save();
        verify("description", settings(project).tags().list().get(0).description, "today");

        ui.delete("urgent");
        verify("tags", settings(project).tags().list().size(), 0);
    }

    @Test
    void adds_a_bucket_with_history_and_the_filled_icon_of_Databucket(TestProject project, Page page) {
        SettingsPage ui = open(project, page, "Buckets");

        ui.add(obj("Name", "customers", "Description", "our customers"));
        ui.chooseIcon(DatabucketIcons.FILLED_NAME);
        ui.check("Collect data history");
        ui.save();

        check("the row of customers", () -> assertThat(ui.row("customers")).containsText("our customers"));
        Bucket customers = settings(project).buckets().list().stream()
                .filter(bucket -> bucket.name.equals("customers")).findFirst().orElseThrow();
        verifyAll(v -> v
                .that("description", customers.description, "our customers")
                .that("history", customers.history, true)
                .that("icon.name", customers.icon.name, DatabucketIcons.FILLED_NAME)
                .that("icon.svg", customers.icon.svg, DatabucketIcons.FILLED));
    }

    @Test
    void deletes_a_bucket_after_typing_its_name(TestProject project, Page page) {
        SettingsPage ui = open(project, page, "Buckets");

        ui.deleteBucket(BUCKET);

        check("the row is gone", () -> assertThat(ui.row(BUCKET)).isHidden());
        verify("buckets", settings(project).buckets().list().size(), 0);
    }

    @Test
    void adds_an_enum_with_its_items(TestProject project, Page page) {
        SettingsPage ui = open(project, page, "Enums");

        ui.add(obj("Name", "status", "Description", "order status"));
        ui.openEditorDialog("Define items");
        ui.add(obj("Value", "NEW", "Text", "New order"));
        ui.save();
        ui.add(obj("Value", "PAID", "Text", "Paid"));
        ui.save();
        check("2 items", () -> assertThat(ui.dialog()).containsText("2 items"));
        ui.closeDialog();
        ui.save();

        DataEnum saved = settings(project).enums().list().get(0);
        verifyAll(v -> v
                .that("name", saved.name, "status")
                .that("items", saved.items, List.of(Map.of("value", "NEW", "text", "New order"),
                        Map.of("value", "PAID", "text", "Paid"))));
    }

    @Test
    void adds_a_class_with_properties(TestProject project, Page page) {
        SettingsPage ui = open(project, page, "Classes");

        ui.add(obj("Name", "order", "Description", "an order"));
        ui.openEditorDialog("Configure properties");
        ui.add(obj("Title", "Status", "Path", "$.status"));
        ui.save();
        ui.add(obj("Title", "Number", "Path", "$.number"));
        ui.choose("Type", "Numeric");
        ui.save();
        ui.closeDialog();
        ui.save();

        DataClass saved = settings(project).classes().list().get(0);
        verifyAll(v -> v
                .that("name", saved.name, "order")
                .that("configuration", saved.configuration, List.of(
                        Map.of("title", "Status", "path", "$.status", "type", "string"),
                        Map.of("title", "Number", "path", "$.number", "type", "numeric"))));
    }

    @Test
    void adds_a_team_with_the_member(TestProject project, Page page) {
        SettingsPage ui = open(project, page, "Teams");
        String member = project.user(MEMBER).username();

        ui.add(obj("Name", "QA", "Description", "testers"));
        ui.select("Select users", member);
        ui.save();

        long memberId = settings(project).users().list().stream()
                .filter(user -> user.username.equals(member)).map(user -> user.id).findFirst().orElseThrow();
        Team team = settings(project).teams().list().get(0);
        verifyAll(v -> v
                .that("name", team.name, "QA")
                .that("usersIds", team.usersIds, Set.of(memberId)));
    }

    @Test
    void adds_a_group_with_the_bucket(TestProject project, Page page) {
        SettingsPage ui = open(project, page, "Groups");

        // the name: 5 to 30 characters
        ui.add(obj("Short name", "SHOP", "Name", "Online shop", "Description", "the shop buckets"));
        ui.select("Select buckets", BUCKET);
        ui.save();

        Group group = settings(project).groups().list().get(0);
        verifyAll(v -> v
                .that("shortName", group.shortName, "SHOP")
                .that("name", group.name, "Online shop")
                .that("bucketsIds", group.bucketsIds, Set.of(bucketId)));
    }

    @Test
    @DisplayName("a group refused by the validation keeps what was typed (fixed in 3.26.2)")
    void a_refused_group_keeps_what_was_typed(TestProject project, Page page) {
        SettingsPage ui = open(project, page, "Groups");

        ui.add(obj("Short name", "SHOP", "Name", "Shop", "Description", "the shop buckets"));
        ui.select("Select buckets", BUCKET);
        ui.trySave();

        verifyAll(v -> v
                .check("the message: Item is not valid", () -> assertThat(ui.message()).containsText("Item is not valid"))
                .check("the message names the rule", () -> assertThat(ui.message()).containsText("Name must be at least 5 characters long"))
                .check("Short name kept", () -> assertThat(ui.field("Short name")).hasValue("SHOP"))
                .check("Name kept", () -> assertThat(ui.field("Name")).hasValue("Shop"))
                .check("Description kept", () -> assertThat(ui.field("Description")).hasValue("the shop buckets"))
                .check("the bucket kept", () -> assertThat(ui.editorButton("Select buckets")).hasText("1"))
                .that("nothing saved", settings(project).groups().list().size(), 0));

        ui.change(obj("Name", "Online shop"));
        ui.save();

        Group group = settings(project).groups().list().get(0);
        verifyAll(v -> v
                .that("name", group.name, "Online shop")
                .that("bucketsIds", group.bucketsIds, Set.of(bucketId)));
    }

    @Test
    void shows_the_icons_of_the_project_and_deletes_one(TestProject project, Page page) {
        SettingsPage ui = open(project, page, "Icons");
        verifyAll(v -> v
                .check("databucket-outlined", () -> assertThat(ui.row(DatabucketIcons.OUTLINED_NAME)).isVisible())
                .check("databucket-filled", () -> assertThat(ui.row(DatabucketIcons.FILLED_NAME)).isVisible()));

        ui.delete(DatabucketIcons.FILLED_NAME);

        verify("icons", settings(project).icons().list().stream().map((Svg icon) -> icon.name).toList(),
                List.of(DatabucketIcons.OUTLINED_NAME));
    }

    @Test
    void lists_the_users_of_the_project_with_their_roles(TestProject project, Page page) {
        SettingsPage ui = open(project, page, "Users");

        verifyAll(v -> {
            for (var key : pl.databucket.e2e.support.UserKey.values())
                v.check(key.key() + ": " + project.user(key).role().name(), () -> assertThat(ui.row(project.user(key).username()))
                        .containsText(project.user(key).role().name()));
        });
        List<ProjectUser> users = settings(project).users().list();
        verify("users", users.size(), pl.databucket.e2e.support.UserKey.values().length);
    }
}
