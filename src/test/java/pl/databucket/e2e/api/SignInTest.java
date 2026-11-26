package pl.databucket.e2e.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.model.AuthResponse;
import pl.databucket.e2e.model.SignIn;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;
import pl.databucket.e2e.support.TestUser;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

@ApiSuite
@Area(Area.ACCESS)
@DisplayName("Sign-in")
class SignInTest {

    @Test
    void a_user_signs_in_to_his_project_and_gets_a_token(TestProject project) {
        TestUser user = project.user(MEMBER);

        AuthResponse response = new Api("anonymous").post("/api/public/signin",
                call().body(new SignIn(user.username(), user.password(), project.id())).expect(200)).as(AuthResponse.class);

        verifyAll(v -> v
                .check("token: not blank", () -> assertThat(response.token()).isNotBlank())
                .that("project.id", response.project().id(), project.id())
                .that("project.name", response.project().name(), project.name())
                .check("roles: contain MEMBER", () -> assertThat(response.roles()).contains("MEMBER")));
    }

    @Test
    void a_wrong_password_is_refused(TestProject project) {
        ApiResponse response = new Api("anonymous").post("/api/public/signin",
                call().body(new SignIn(project.user(MEMBER).username(), "wrong", project.id())));

        verify("status", response.status(), 401);
    }

    @Test
    void calls_without_a_token_are_refused() {
        ApiResponse response = new Api("anonymous").get("/api/buckets");

        verify("status", response.status(), 401);
    }
}
