package pl.databucket.e2e.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.client.Settings;
import pl.databucket.e2e.model.Bucket;
import pl.databucket.e2e.model.DataRow;
import pl.databucket.e2e.model.Icon;
import pl.databucket.e2e.model.ManagedProject;
import pl.databucket.e2e.model.ManagedUser;
import pl.databucket.e2e.model.NewRow;
import pl.databucket.e2e.model.SignIn;
import pl.databucket.e2e.model.Tag;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.Role;
import pl.databucket.e2e.support.TestProject;
import pl.databucket.e2e.support.TestUser;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.write;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.AGENT;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

@ApiSuite
@DisplayName("Access")
class AccessTest {

    record Card(String card) {
    }

    private static Bucket bucket(String name, Role role) {
        return new Bucket().name(name).icon(new Icon().name("Storage")).roleId((short) role.id);
    }

    @Nested
    @DisplayName("Projects are separate")
    class Projects {

        @Test
        void a_bucket_of_another_project_does_not_exist_for_the_user(TestProject project) {
            try (TestProject other = TestProject.create()) {
                other.createBucket("secret-orders");
                other.quietly(ROBOT).bucket("secret-orders", Card.class).insert(new Card("4111"));

                BucketApi<Card> theirs = project.as(ROBOT).bucket("secret-orders", Card.class);

                verify("status", theirs.tryFind(Search.all()).status(), 404);
                verify("status", theirs.tryInsert(NewRow.of(new Card("x"))).status(), 404);
            }
        }

        @Test
        void the_same_bucket_name_in_two_projects_means_two_separate_buckets(TestProject project) {
            try (TestProject other = TestProject.create()) {
                project.createBucket("orders");
                other.createBucket("orders");
                DataRow<Card> theirs = other.quietly(ROBOT).bucket("orders", Card.class).insert(new Card("other project"));

                BucketApi<Card> ours = project.as(ROBOT).bucket("orders", Card.class);

                verify("count", ours.count(Search.all()), 0);
                check("the row of the other project is not found", () -> assertThat(ours.get(theirs.id())).isEmpty());
            }
        }

        @Test
        void the_settings_list_only_the_objects_of_the_own_project(TestProject project) {
            try (TestProject other = TestProject.create()) {
                other.createBucket("their-bucket");
                other.quietly(ADMIN).settings().tags().create(new Tag().name("their-tag"));
                project.createBucket("our-bucket");

                Settings settings = project.as(ADMIN).settings();

                verify("bucket names", settings.buckets().list().stream().map(b -> b.name).toList(), List.of("our-bucket"));
                check("the tag of the other project is not listed",
                        () -> assertThat(settings.tags().list()).extracting(t -> t.name).doesNotContain("their-tag"));
            }
        }

        @Test
        void a_user_can_not_sign_in_to_a_project_they_are_not_assigned_to(TestProject project) {
            try (TestProject other = TestProject.create()) {
                TestUser user = project.user(MEMBER);

                ApiResponse response = new Api("anonymous").post("/api/public/signin",
                        call().body(new SignIn(user.username(), user.password(), other.id())));

                verifyAll(v -> v
                        .that("status", response.status(), 403)
                        .check("no token", () -> assertThat(response.<Object>path("token")).isNull())
                        .that("message", response.<String>path("message"), "The user is not assigned to given project!"));
            }
        }
    }

    @Nested
    @DisplayName("Access to buckets")
    class Buckets {

        @Test
        @DisplayName("a bucket for ADMIN and up is hidden from a MEMBER")
        void admins_only(TestProject project) {
            project.quietly(ADMIN).settings().buckets().create(bucket("admins-only", Role.ADMIN));

            verify("status", project.as(MEMBER).bucket("admins-only", Card.class).tryFind(Search.all()).status(), 404);
            check("the bucket is not in the access tree",
                    () -> assertThat(project.as(MEMBER).settings().accessTree().bucketNames()).doesNotContain("admins-only"));
        }

        @Test
        @DisplayName("a bucket for ROBOT and up is hidden from an AGENT")
        void robots_only(TestProject project) {
            project.quietly(ADMIN).settings().buckets().create(bucket("robots", Role.ROBOT));

            verify("status for the agent", project.as(AGENT).bucket("robots", Card.class).tryFind(Search.all()).status(), 404);
            verify("status for the robot", project.as(ROBOT).bucket("robots", Card.class).tryFind(Search.all()).status(), 200);
        }

        @Test
        void a_bucket_given_to_chosen_users_only(TestProject project) {
            Settings admin = project.quietly(ADMIN).settings();
            Long robotId = admin.users().list().stream()
                    .filter(u -> u.username.equals(project.user(ROBOT).username())).findFirst().orElseThrow().id;
            admin.buckets().create(new Bucket().name("robot-only").icon(new Icon().name("Storage")).usersIds(Set.of(robotId)));

            verify("status for the robot", project.as(ROBOT).bucket("robot-only", Card.class).tryFind(Search.all()).status(), 200);
            verify("status for the member", project.as(MEMBER).bucket("robot-only", Card.class).tryFind(Search.all()).status(), 404);
        }
    }

    @Nested
    @DisplayName("Roles")
    class Roles {

        @Test
        @DisplayName("ROBOT and AGENT can not use the web application endpoints")
        void api_only_roles(TestProject project) {
            verify("status for the robot", project.as(ROBOT).get("/api/session/access-tree").status(), 403);
            verify("status for the agent", project.as(AGENT).get("/api/session/access-tree").status(), 403);
            verify("status of the users list", project.as(ROBOT).settings().users().tryList().status(), 403);
        }

        @Test
        @DisplayName("a MEMBER can not change the settings of the project")
        void member_settings(TestProject project) {
            Settings member = project.as(MEMBER).settings();

            verify("status", member.tags().tryCreate(new Tag().name("x")).status(), 403);
            verify("status", member.classes().tryCreate(Map.of("name", "x")).status(), 403);
            verify("status", member.enums().tryCreate(Map.of("name", "x", "items", List.of())).status(), 403);
            verify("status", member.views().tryCreate(Map.of("name", "x", "columnsId", 1)).status(), 403);
        }

        @Test
        @DisplayName("only a SUPER user manages projects and users")
        void super_only(TestProject project) {
            Api admin = project.as(ADMIN);

            verify("status", admin.get("/api/manage/projects").status(), 403);
            verify("status", admin.get("/api/manage/users").status(), 403);
            verify("status", admin.post("/api/manage/projects", call().body(new ManagedProject().name("mine").enabled(true))).status(),
                    403);
            verify("status", admin.post("/api/manage/users", call().body(new ManagedUser().username("mine"))).status(), 403);
        }

        @Test
        void a_token_that_is_broken_or_made_up_is_refused(TestProject project) {
            String valid = project.token(ROBOT);
            String tampered = valid.substring(0, valid.length() - 4) + (valid.endsWith("AAAA") ? "BBBB" : "AAAA");
            Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
            String header = base64.encodeToString(write(Map.of("alg", "none")).getBytes(UTF_8));
            String payload = base64.encodeToString(write(Map.of("sub", "super", "a-key", "ROLE_SUPER", "p-id", project.id())).getBytes(UTF_8));

            Map<String, String> tokens = new LinkedHashMap<>();
            tokens.put("a tampered token", tampered);
            tokens.put("a token without a signature", header + "." + payload + ".");
            tokens.put("not a token", "not-a-token");
            tokens.forEach((what, token) -> {
                ApiResponse response = new Api("anonymous").withToken(token).bucket("any", Card.class).tryFind(Search.all());
                verify("status for " + what, response.status(), 401);
            });
        }
    }
}
