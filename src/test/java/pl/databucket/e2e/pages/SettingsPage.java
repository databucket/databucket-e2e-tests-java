package pl.databucket.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import pl.databucket.e2e.support.Json;
import pl.databucket.e2e.support.Screenshots;

import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * The project settings (Settings: Teams, Users, Classes, Icons, Enums, Groups, Buckets, Tags, Columns, ...): a tab per
 * object type, each a table with a row editor (Add, Edit) and Delete with the question in the row. Fields of the
 * editor are found by their names (the column titles); lists of objects (buckets, users) are chosen in a dialog
 * opened by a button of the editor ("Select buckets").
 */
public final class SettingsPage {

    private final Page page;

    public SettingsPage(Page page) {
        this.page = page;
    }

    /** Opens the tab and waits for its data. */
    public void openTab(String tab) {
        Screenshots.step("Settings > " + tab, () -> {
            page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(tab).setExact(true)).click();
            waitUntilLoaded(tab);
        });
    }

    /**
     * Waits until the tab is shown and its lists are loaded: a tab reads several lists (e.g. Groups: groups, buckets,
     * users, teams). Before 3.26.2 the table was mounted again when they came, which dropped a row being edited.
     */
    public void waitUntilLoaded(String tab) {
        assertThat(tab(tab)).hasAttribute("aria-selected", "true");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
    }

    public Locator tab(String tab) {
        return page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(tab).setExact(true));
    }

    /** The row of the table (of the page or of a dialog) containing the text, not being edited. */
    public Locator row(String text) {
        return page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(text))
                .filter(new Locator.FilterOptions().setHasNot(page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Save"))));
    }

    /** The row being edited (the one with Save and Cancel): of the page, or of the dialog when one is open. */
    public Locator editor() {
        return page.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions()
                .setHas(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save"))));
    }

    /** The top dialog. */
    public Locator dialog() {
        return page.getByRole(AriaRole.DIALOG).last();
    }

    // the Add button of the page, or of the top dialog when one is open
    private Locator addButton() {
        Locator scope = page.getByRole(AriaRole.DIALOG).count() > 0 ? dialog() : page.locator("body");
        return scope.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Add").setExact(true)).first();
    }

    /** Clicks Add (of the page or of the open dialog) and fills the text fields of the new row. */
    public void add(Map<String, ?> fields) {
        Screenshots.step("Add " + Json.write(fields), () -> {
            addButton().click();
            fill(fields);
        });
    }

    /** Starts editing the row containing the text and fills the text fields. */
    public void edit(String row, Map<String, ?> fields) {
        Screenshots.step("Edit " + row + ": " + Json.write(fields), () -> {
            row(row).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Edit")).click();
            fill(fields);
        });
    }

    /** Changes text fields of the row being edited. */
    public void change(Map<String, ?> fields) {
        Screenshots.step("Change " + Json.write(fields), () -> fill(fields));
    }

    // fills the text fields of the edited row once the lists the tab reads after a click are loaded (before 3.26.2 the
    // table was mounted again and dropped what was typed), then checks that they kept their values
    private void fill(Map<String, ?> fields) {
        Locator editor = editor();
        assertThat(editor).isVisible();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        fields.forEach((name, value) -> field(name).fill(String.valueOf(value)));
        fields.forEach((name, value) -> assertThat(field(name)).hasValue(String.valueOf(value)));
    }

    /** A text field of the edited row by its name. */
    public Locator field(String name) {
        return editor().getByRole(AriaRole.TEXTBOX, new Locator.GetByRoleOptions().setName(name).setExact(true));
    }

    /** Checks a checkbox of the edited row, e.g. "Collect data history". */
    public void check(String name) {
        Screenshots.step("Check " + name, () -> editor().getByRole(AriaRole.CHECKBOX,
                new Locator.GetByRoleOptions().setName(name)).check());
    }

    /** Opens the list dialog of the edited row ("Select buckets"), checks the rows containing the texts, closes it. */
    public void select(String button, String... texts) {
        Screenshots.step(button + ": " + String.join(", ", texts), () -> {
            editor().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(button)).click();
            Locator dialog = dialog();
            assertThat(dialog).isVisible();
            for (String text : texts)
                dialog.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(text))
                        .getByRole(AriaRole.CHECKBOX).check();
            Screenshots.screenshot("selected");
            closeDialog();
            assertThat(editor().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(button)))
                    .hasText(String.valueOf(texts.length));
        });
    }

    /** Opens the editing dialog of the edited row ("Define items", "Configure properties"). */
    public void openEditorDialog(String button) {
        Screenshots.step("Open " + button, () -> {
            editor().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(button)).click();
            assertThat(dialog()).isVisible();
        });
    }

    /** Closes the top dialog with its button "close". */
    public void closeDialog() {
        Locator dialog = dialog();
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("close").setExact(true)).click();
        assertThat(dialog).isHidden();
    }

    /** Chooses an option of a combobox of the edited row, e.g. "Type": "Numeric". */
    public void choose(String combobox, String option) {
        Screenshots.step(combobox + ": " + option, () -> {
            editor().getByRole(AriaRole.COMBOBOX, new Locator.GetByRoleOptions().setName(combobox)).click();
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(option).setExact(true)).click();
        });
    }

    /** Chooses an icon of the icon dialog of the edited row (a built-in name or a project icon) and saves it. */
    public void chooseIcon(String icon) {
        Screenshots.step("Icon " + icon, () -> {
            editor().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Change icon")).click();
            Locator dialog = dialog();
            dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(icon).setExact(true)).click();
            Screenshots.screenshot("chosen");
            dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save")).click();
            assertThat(dialog).isHidden();
        });
    }

    /** Saves the edited row (of the page or of the open dialog) and waits until the editor closes. */
    public void save() {
        Screenshots.step("Save", () -> {
            Locator editor = editor();
            editor.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save")).click();
            assertThat(editor).isHidden();
        });
    }

    /** Clicks Save of the edited row expecting a refusal: the row stays in editing. */
    public void trySave() {
        Screenshots.step("Try to save", () -> {
            editor().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save")).click();
            assertThat(message()).isVisible();
        });
    }

    /** The message shown after an action, e.g. "Item is not valid". */
    public Locator message() {
        return page.getByRole(AriaRole.ALERT);
    }

    /** A button of the edited row, e.g. "Select buckets" (its text: the number chosen). */
    public Locator editorButton(String name) {
        return editor().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name));
    }

    /** Deletes the row containing the text, confirming the question in the row. */
    public void delete(String row) {
        Screenshots.step("Delete " + row, () -> {
            row(row).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete")).click();
            Locator question = editor();
            assertThat(question).containsText("Are you sure you want to delete this row?");
            Screenshots.screenshot("question");
            question.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save")).click();
            assertThat(question).isHidden();
        });
    }

    /**
     * Deletes a bucket: the question in the row, then the dialog "Confirm removing", where the name of the bucket is
     * typed to delete it with its data.
     */
    public void deleteBucket(String name) {
        Screenshots.step("Delete the bucket " + name, () -> {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            row(name).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete")).click();
            Locator question = editor();
            assertThat(question).containsText("Are you sure you want to delete this row?");
            question.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Save")).click();
            Locator confirm = page.getByRole(AriaRole.DIALOG).filter(new Locator.FilterOptions().setHasText("Confirm removing"));
            assertThat(confirm).containsText(java.util.regex.Pattern.compile("Remove bucket:\\s*" + name));
            Locator remove = confirm.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Remove"));
            assertThat(remove).isDisabled();
            confirm.getByRole(AriaRole.TEXTBOX).fill(name);
            Screenshots.screenshot("name typed");
            remove.click();
            assertThat(confirm).isHidden();
        });
    }
}
