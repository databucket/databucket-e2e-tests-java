package pl.databucket.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import pl.databucket.e2e.support.Screenshots;
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
        Screenshots.step("Open the project as " + key.key() + " (" + user.username() + ")", () -> {
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
        Screenshots.step("Open bucket " + name, () -> {
            bucket(name).click();
            assertThat(page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(name).setSelected(true))).isVisible();
        });
    }

    /** A data row of the grid containing the text. */
    public Locator row(String text) {
        return main().getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(text));
    }

    public void search(String text) {
        Screenshots.step("Search \"" + text + "\"", () -> searchBox().fill(text));
    }

    /** Adds a row with the grid's inline editor; fields by their column titles, in the given order. */
    public void addRow(Map<String, ?> values) {
        Screenshots.step("Add a row " + Json.write(values), () -> {
            addButton().click();
            Locator editor = main().getByRole(AriaRole.ROW).filter(new Locator.FilterOptions()
                    .setHas(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save"))));
            values.forEach((column, value) ->
                    editor.getByRole(AriaRole.TEXTBOX, new Locator.GetByRoleOptions().setName(column)).fill(String.valueOf(value)));
            editor.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save")).click();
            assertThat(editor).isHidden();
        });
    }

    /** The row being edited or deleted in the grid (the one with Save and Cancel). */
    public Locator editedRow() {
        return main().getByRole(AriaRole.ROW).filter(new Locator.FilterOptions()
                .setHas(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save"))));
    }

    // an item of the menu of a row (the button "Actions" of the row)
    private void rowMenu(String row, String item) {
        row(row).getByRole(AriaRole.BUTTON).first().click();
        page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(item).setExact(true)).click();
    }

    /** Edits the row containing the text: the fields by their column titles. */
    public void editRow(String row, Map<String, ?> values) {
        Screenshots.step("Edit the row " + row + ": " + Json.write(values), () -> {
            rowMenu(row, "Edit");
            Locator editor = editedRow();
            values.forEach((column, value) -> editor.getByRole(AriaRole.TEXTBOX,
                    new Locator.GetByRoleOptions().setName(column)).fill(String.valueOf(value)));
            Screenshots.screenshot("edited");
            editor.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save")).click();
            assertThat(editor).isHidden();
        });
    }

    /** Deletes the row containing the text from its menu, confirming the question in the row. */
    public void deleteRow(String row) {
        Screenshots.step("Delete the row " + row, () -> {
            rowMenu(row, "Delete");
            Locator question = editedRow();
            assertThat(question).containsText("Are you sure you want to delete this row?");
            Screenshots.screenshot("question");
            question.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save")).click();
            assertThat(question).isHidden();
        });
    }

    /** Opens the history of the row containing the text. */
    public DataHistoryDialog openHistory(String row) {
        DataHistoryDialog dialog = new DataHistoryDialog(page);
        Screenshots.step("Open the history of the row " + row, () -> {
            rowMenu(row, "Data history");
            assertThat(dialog.dialog()).isVisible();
        });
        return dialog;
    }

    /** Opens the details of the row containing the text. */
    public Locator openDetails(String row) {
        Locator dialog = page.getByRole(AriaRole.DIALOG).filter(new Locator.FilterOptions().setHasText("Data details"));
        Screenshots.step("Open the details of the row " + row, () -> {
            rowMenu(row, "Data details");
            assertThat(dialog).isVisible();
        });
        return dialog;
    }

    /** Selects the rows containing the texts with their checkboxes. */
    public void select(String... rows) {
        Screenshots.step("Select the rows " + String.join(", ", rows), () -> {
            for (String row : rows) row(row).getByRole(AriaRole.CHECKBOX).check();
            assertThat(main().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions()
                    .setName(rows.length + " row(s) selected"))).isVisible();
        });
    }

    /** A button of the toolbar shown for the selected rows (Reserve, Release, Delete, Set tag...). */
    public Locator selectionButton(String name) {
        return main().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name).setExact(true));
    }

    /** The message shown after an action (e.g. "Reserved 2 data row(s)"). */
    public Locator message() {
        return main().getByRole(AriaRole.ALERT);
    }

    /** Reserves (or releases) the selected rows and waits for the message. */
    public void reserveSelected(boolean reserve) {
        String action = reserve ? "Reserve" : "Release";
        Screenshots.step(action + " the selected rows", () -> {
            selectionButton(action).click();
            assertThat(message()).isVisible();
        });
    }

    /** Deletes the selected rows, confirming the dialog "Delete N rows?". */
    public void deleteSelected() {
        Screenshots.step("Delete the selected rows", () -> {
            selectionButton("Delete").click();
            Locator dialog = page.getByRole(AriaRole.DIALOG).filter(new Locator.FilterOptions()
                    .setHasText(Pattern.compile("^Delete \\d+ rows?\\?")));
            assertThat(dialog).isVisible();
            Screenshots.screenshot("question");
            dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete")).click();
            assertThat(dialog).isHidden();
            assertThat(message()).isVisible();
        });
    }

    /** Sorts by the column (a click on its title: ascending, the next one descending). */
    public void sortBy(String column) {
        // the sort label itself: the column title around it is the drag handle of the column (a click in its
        // middle may miss the label)
        Screenshots.step("Sort by " + column, () -> main().getByTestId("mtableheader-sortlabel")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^" + column + "$"))).click());
    }

    /** The texts of a column of the shown rows, from the top (the column by its position among the cells). */
    public List<String> column(int index) {
        // the data rows: with a checkbox in a cell (the header has it in a column header, the empty rows have none)
        return main().getByRole(AriaRole.ROW).filter(new Locator.FilterOptions()
                        .setHas(page.getByRole(AriaRole.CELL).getByRole(AriaRole.CHECKBOX)))
                .all().stream().map(row -> row.getByRole(AriaRole.CELL).nth(index).innerText().strip()).toList();
    }

    /** Opens the rich filter dialog. */
    public RichFilterDialog openRichFilter() {
        RichFilterDialog dialog = new RichFilterDialog(page);
        Screenshots.step("Open the rich filter", () -> {
            main().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Rich filter")).click();
            assertThat(dialog.dialog()).isVisible();
        });
        return dialog;
    }

    /** The Import/Export button of the toolbar; its name lists the enabled features, e.g. "Import / Export / Generate". */
    public Locator importExportButton() {
        return main().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^(Import|Export|Generate)( |$)")));
    }

    /** The items of the Import/Export menu (empty when the view has none of these features). */
    public List<String> importExportMenu() {
        return Screenshots.step("Read the Import/Export menu", () -> {
            if (importExportButton().count() == 0)
                return List.of();
            importExportButton().click();
            Locator items = page.getByRole(AriaRole.MENUITEM);
            assertThat(items.first()).isVisible();
            List<String> texts = items.allTextContents();
            Screenshots.screenshot("menu");
            page.keyboard().press("Escape");
            assertThat(items.first()).isHidden();
            return texts;
        });
    }

    /** Opens the dialog "Generate data" from the Import/Export menu. */
    public GenerateDataDialog openGenerateData() {
        GenerateDataDialog dialog = new GenerateDataDialog(page);
        Screenshots.step("Open Generate data", () -> {
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
