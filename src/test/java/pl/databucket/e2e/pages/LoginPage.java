package pl.databucket.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Allure;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public final class LoginPage {

    private final Page page;

    public LoginPage(Page page) {
        this.page = page;
    }

    public Locator username() {
        return page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Username"));
    }

    public Locator password() {
        return page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password"));
    }

    public Locator loginButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login"));
    }

    public void open() {
        Allure.step("Open the login page", () -> {
            page.navigate("/login");
            assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Login"))).isVisible();
        });
    }

    public void signIn(String username, String password) {
        Allure.step("Sign in as " + username, () -> {
            username().fill(username);
            password().fill(password);
            loginButton().click();
        });
    }
}
