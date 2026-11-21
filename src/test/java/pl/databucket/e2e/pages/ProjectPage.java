package pl.databucket.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Allure;
import pl.databucket.e2e.support.Json;
import pl.databucket.e2e.support.TestProject;
import pl.databucket.e2e.support.TestUser;
import pl.databucket.e2e.support.UserKey;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static pl.databucket.e2e.support.Json.obj;

/** The project frontend: the bucket list in the drawer and the data grid of the open bucket. */
public final class ProjectPage {

    private final Page page;

    public ProjectPage(Page page) {
        this.page = page;
    }

    public Locator main() {
        return page.getByRole(AriaRole.MAIN);
    }

    public Locator searchBox() {
        return main().getByRole(AriaRole.TEXTBOX, new Locator.GetByRoleOptions().setName("Search"));
    }

    public Locator addButton() {
        return main().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Add"));
    }

    public Locator pagination() {
        return main().getByText(Pattern.compile("^\\d+-\\d+ of \\d+$"));
    }

    /**
     * Opens the project as the user without going through the login form: the token is put where the login page
     * puts it. Tests of the login page itself use {@link LoginPage}.
     */
    public void openAs(TestProject project, UserKey key) {
        TestUser user = project.user(key);
        Allure.step("Open the project as " + key.key() + " (" + user.username() + ")", () -> {
            Map<String, Object> session = obj(
                    "user-name", user.username(),
                    "token", project.token(key),
                    "project-id", String.valueOf(project.id()),
                    "roles", rolesOf(user.role().name()));
            // only once, so a sign-out in the test isn't undone by a reload
            page.addInitScript("""
                    (values => {
                      if (sessionStorage.getItem('e2e-session')) return;
                      sessionStorage.setItem('e2e-session', '1');
                      for (const [name, value] of Object.entries(values)) localStorage.setItem(name, value);
                    })(%s);
                    """.formatted(Json.write(session)));
            page.navigate("/project/" + project.id());
        });
    }

    public Locator bucket(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    public void openBucket(String name) {
        Allure.step("Open bucket " + name, () -> {
            bucket(name).click();
            assertThat(page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(name).setSelected(true))).isVisible();
        });
    }

    /** A data row of the grid containing the text. */
    public Locator row(String text) {
        return main().getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(text));
    }

    public void search(String text) {
        Allure.step("Search \"" + text + "\"", () -> searchBox().fill(text));
    }

    /** Adds a row with the grid's inline editor; fields by their column titles, in the given order. */
    public void addRow(Map<String, ?> values) {
        Allure.step("Add a row " + Json.write(values), () -> {
            addButton().click();
            Locator editor = main().getByRole(AriaRole.ROW).filter(new Locator.FilterOptions()
                    .setHas(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save"))));
            values.forEach((column, value) ->
                    editor.getByRole(AriaRole.TEXTBOX, new Locator.GetByRoleOptions().setName(column)).fill(String.valueOf(value)));
            editor.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save")).click();
            assertThat(editor).isHidden();
        });
    }

    /** The Import/Export button of the toolbar; its name lists the enabled features, e.g. "Import / Export / Generate". */
    public Locator importExportButton() {
        return main().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^(Import|Export|Generate)( |$)")));
    }

    /** The items of the Import/Export menu (empty when the view has none of these features). */
    public List<String> importExportMenu() {
        return Allure.step("Read the Import/Export menu", () -> {
            if (importExportButton().count() == 0)
                return List.of();
            importExportButton().click();
            Locator items = page.getByRole(AriaRole.MENUITEM);
            assertThat(items.first()).isVisible();
            List<String> texts = items.allTextContents();
            page.keyboard().press("Escape");
            assertThat(items.first()).isHidden();
            return texts;
        });
    }

    /** Opens the dialog "Generate data" from the Import/Export menu. */
    public GenerateDataDialog openGenerateData() {
        GenerateDataDialog dialog = new GenerateDataDialog(page);
        Allure.step("Open Generate data", () -> {
            importExportButton().click();
            page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName("Generate data…")).click();
            assertThat(dialog.dialog()).isVisible();
        });
        return dialog;
    }

    private static String rolesOf(String role) {
        List<String> order = List.of("ROBOT", "MEMBER", "ADMIN", "SUPER");
        List<String> roles = new java.util.ArrayList<>(order.subList(0, order.indexOf(role) + 1));
        java.util.Collections.reverse(roles);
        return String.join(",", roles);
    }
}
