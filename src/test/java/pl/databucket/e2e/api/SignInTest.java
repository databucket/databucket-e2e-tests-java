package pl.databucket.e2e.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;
import pl.databucket.e2e.support.TestUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.MEMBER;

@ApiSuite
@DisplayName("Sign-in")
class SignInTest {

    @Test
    void a_user_signs_in_to_his_project_and_gets_a_token(TestProject project) {
        TestUser user = project.user(MEMBER);
        Api anonymous = new Api("anonymous");

        ApiResponse response = anonymous.post("/api/public/signin", call()
                .body(obj("username", user.username(), "password", user.password(), "projectId", project.id()))
                .expect(200));

        assertThat(response.<String>path("token")).isNotBlank();
        assertThat(response.<Number>path("project.id").longValue()).isEqualTo(project.id());
        assertThat(response.<String>path("project.name")).isEqualTo(project.name());
        assertThat(response.<List<String>>path("roles")).contains("MEMBER");
    }

    @Test
    void a_wrong_password_is_refused(TestProject project) {
        Api anonymous = new Api("anonymous");

        ApiResponse response = anonymous.post("/api/public/signin", call()
                .body(obj("username", project.user(MEMBER).username(), "password", "wrong", "projectId", project.id())));

        assertThat(response.status()).isEqualTo(401);
    }

    @Test
    void calls_without_a_token_are_refused() {
        Api anonymous = new Api("anonymous");

        ApiResponse response = anonymous.get("/api/buckets");

        assertThat(response.status()).isEqualTo(401);
    }
}
