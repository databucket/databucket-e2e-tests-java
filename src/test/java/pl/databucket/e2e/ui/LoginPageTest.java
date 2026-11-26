package pl.databucket.e2e.ui;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.pages.LoginPage;
import pl.databucket.e2e.pages.ProjectPage;
import pl.databucket.e2e.support.TestProject;
import pl.databucket.e2e.support.TestUser;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.UiSuite;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static pl.databucket.e2e.support.Screenshots.screenshot;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.Verify.verifyAll;

@UiSuite
@Area(Area.ACCESS)
@DisplayName("Login page (GUI)")
class LoginPageTest {

    @Test
    void a_member_signs_in_and_sees_the_buckets_of_his_project(TestProject project, Page page) {
        project.createBucket("orders");
        LoginPage login = new LoginPage(page);
        login.open();

        TestUser member = project.user(MEMBER);
        login.signIn(member.username(), member.password());

        verifyAll(v -> v
                .check("the project page is open", () -> assertThat(page).hasURL(Pattern.compile("/project/" + project.id() + "$")))
                .check("the bucket orders is shown", () -> assertThat(new ProjectPage(page).bucket("orders")).isVisible()));
    }

    @Test
    void a_wrong_password_keeps_the_user_on_the_login_page(TestProject project, Page page) {
        LoginPage login = new LoginPage(page);
        login.open();

        login.signIn(project.user(MEMBER).username(), "wrong password");

        verifyAll(v -> v
                .check("the message says bad credentials",
                        () -> assertThat(page.getByText(Pattern.compile("bad credentials", Pattern.CASE_INSENSITIVE))).isVisible())
                .check("still on the login page", () -> assertThat(page).hasURL(Pattern.compile("/login$"))));
        screenshot(page, "error message");
    }
}
