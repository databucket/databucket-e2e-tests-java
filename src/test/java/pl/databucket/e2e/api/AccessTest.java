package pl.databucket.e2e.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.Role;
import pl.databucket.e2e.support.TestProject;
import pl.databucket.e2e.support.TestUser;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.arr;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.Json.write;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.AGENT;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;

@ApiSuite
@DisplayName("Access")
class AccessTest {

    @Nested
    @DisplayName("Projects are separate")
    class Projects {

        @Test
        void a_bucket_of_another_project_does_not_exist_for_the_user(TestProject project) {
            try (TestProject other = TestProject.create()) {
                other.createBucket("secret-orders");
                other.quietly(ROBOT).post("/api/bucket/secret-orders", call().body(obj("properties", obj("card", "4111"))).expect(201));

                Api robot = project.as(ROBOT);
                ApiResponse search = robot.post("/api/bucket/secret-orders/get", call().body(obj()));
                ApiResponse insert = robot.post("/api/bucket/secret-orders", call().body(obj("properties", obj("x", 1))));

                assertThat(search.status()).isEqualTo(404);
                assertThat(insert.status()).isEqualTo(404);
            }
        }

        @Test
        void the_same_bucket_name_in_two_projects_means_two_separate_buckets(TestProject project) {
            try (TestProject other = TestProject.create()) {
                project.createBucket("orders");
                other.createBucket("orders");
                ApiResponse theirs = other.quietly(ROBOT).post("/api/bucket/orders",
                        call().body(obj("properties", obj("owner", "other project"))).expect(201));

                Api robot = project.as(ROBOT);
                ApiResponse ours = robot.post("/api/bucket/orders/get?limit=10", call().body(obj()).expect(200));
                ApiResponse byTheirId = robot.get("/api/bucket/orders/" + theirs.path("id"), call().expect(200));

                assertThat(ours.<Integer>path("total")).isZero();
                assertThat(byTheirId.<List<Object>>path("$")).isEmpty();
            }
        }

        @Test
        void the_settings_list_only_the_objects_of_the_own_project(TestProject project) {
            try (TestProject other = TestProject.create()) {
                other.createBucket("their-bucket");
                project.createBucket("our-bucket");

                ApiResponse buckets = project.as(ADMIN).get("/api/buckets", call().expect(200));

                assertThat(buckets.<List<String>>path("name")).containsExactly("our-bucket");
            }
        }

        @Test
        void a_user_can_not_sign_in_to_a_project_they_are_not_assigned_to(TestProject project) {
            try (TestProject other = TestProject.create()) {
                TestUser user = project.user(MEMBER);

                ApiResponse response = new Api("anonymous").post("/api/public/signin",
                        call().body(obj("username", user.username(), "password", user.password(), "projectId", other.id())));

                assertThat(response.<Object>path("token")).isNull();
                assertThat(response.status()).isNotEqualTo(200);
            }
        }
    }

    @Nested
    @DisplayName("Access to buckets")
    class Buckets {

        @Test
        @DisplayName("a bucket for ADMIN and up is hidden from a MEMBER")
        void admins_only(TestProject project) {
            project.quietly(ADMIN).post("/api/buckets", call().body(obj(
                    "name", "admins-only", "icon", obj("name", "Storage"), "roleId", Role.ADMIN.id)).expect(201));
            Api member = project.as(MEMBER);

            ApiResponse response = member.post("/api/bucket/admins-only/get", call().body(obj()));
            ApiResponse tree = member.get("/api/session/access-tree", call().expect(200));

            assertThat(response.status()).isEqualTo(404);
            assertThat(tree.<List<String>>path("buckets.name")).doesNotContain("admins-only");
        }

        @Test
        @DisplayName("a bucket for ROBOT and up is hidden from an AGENT")
        void robots_only(TestProject project) {
            project.quietly(ADMIN).post("/api/buckets", call().body(obj(
                    "name", "robots", "icon", obj("name", "Storage"), "roleId", Role.ROBOT.id)).expect(201));

            assertThat(project.as(AGENT).post("/api/bucket/robots/get", call().body(obj())).status()).isEqualTo(404);
            assertThat(project.as(ROBOT).post("/api/bucket/robots/get", call().body(obj())).status()).isEqualTo(200);
        }

        @Test
        void a_bucket_given_to_chosen_users_only(TestProject project) {
            Api admin = project.quietly(ADMIN);
            List<Map<String, Object>> users = admin.get("/api/users", call().expect(200)).path("$");
            Object robotId = users.stream().filter(u -> project.user(ROBOT).username().equals(u.get("username")))
                    .findFirst().orElseThrow().get("id");
            admin.post("/api/buckets", call().body(obj(
                    "name", "robot-only", "icon", obj("name", "Storage"), "usersIds", arr(robotId))).expect(201));

            assertThat(project.as(ROBOT).post("/api/bucket/robot-only/get", call().body(obj())).status()).isEqualTo(200);
            assertThat(project.as(MEMBER).post("/api/bucket/robot-only/get", call().body(obj())).status()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("Roles")
    class Roles {

        @Test
        @DisplayName("ROBOT and AGENT can not use the web application endpoints")
        void api_only_roles(TestProject project) {
            Api robot = project.as(ROBOT);
            Api agent = project.as(AGENT);

            assertThat(robot.get("/api/session/access-tree").status()).isEqualTo(403);
            assertThat(agent.get("/api/session/access-tree").status()).isEqualTo(403);
            assertThat(robot.get("/api/users").status()).isEqualTo(403);
        }

        @Test
        @DisplayName("a MEMBER can not change the settings of the project")
        void member_settings(TestProject project) {
            Api member = project.as(MEMBER);

            assertThat(member.post("/api/tags", call().body(obj("name", "x"))).status()).isEqualTo(403);
            assertThat(member.post("/api/classes", call().body(obj("name", "x"))).status()).isEqualTo(403);
            assertThat(member.post("/api/enums", call().body(obj("name", "x", "items", arr()))).status()).isEqualTo(403);
            assertThat(member.post("/api/views", call().body(obj("name", "x", "columnsId", 1))).status()).isEqualTo(403);
        }

        @Test
        @DisplayName("only a SUPER user manages projects and users")
        void super_only(TestProject project) {
            Api admin = project.as(ADMIN);

            assertThat(admin.get("/api/manage/projects").status()).isEqualTo(403);
            assertThat(admin.get("/api/manage/users").status()).isEqualTo(403);
            assertThat(admin.post("/api/manage/projects", call().body(obj("name", "mine", "enabled", true))).status()).isEqualTo(403);
        }

        @Test
        void a_token_that_is_broken_or_made_up_is_refused(TestProject project) {
            String valid = project.token(ROBOT);
            String tampered = valid.substring(0, valid.length() - 4) + (valid.endsWith("AAAA") ? "BBBB" : "AAAA");
            Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
            String header = base64.encodeToString(write(obj("alg", "none")).getBytes(UTF_8));
            String payload = base64.encodeToString(write(obj("sub", "super", "a-key", "ROLE_SUPER", "p-id", project.id())).getBytes(UTF_8));

            for (String token : List.of(tampered, header + "." + payload + ".", "not-a-token")) {
                ApiResponse response = new Api("anonymous").withToken(token).post("/api/bucket/any/get", call().body(obj()));
                assertThat(response.status()).as("token %s", token).isEqualTo(401);
            }
        }
    }
}
