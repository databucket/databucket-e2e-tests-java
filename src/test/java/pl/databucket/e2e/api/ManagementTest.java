package pl.databucket.e2e.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Management;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.model.AuthResponse;
import pl.databucket.e2e.model.DataClass;
import pl.databucket.e2e.model.DatasetInfo;
import pl.databucket.e2e.model.ManagedProject;
import pl.databucket.e2e.model.ManagedUser;
import pl.databucket.e2e.model.SignIn;
import pl.databucket.e2e.model.Tag;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.Env;
import pl.databucket.e2e.support.Role;
import pl.databucket.e2e.support.TestProject;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

/** The management of all projects and users (SUPER). Projects made here are removed, users disabled. */
@ApiSuite
@Area(Area.MANAGEMENT)
@DisplayName("Management")
class ManagementTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    record Login(String login) {
    }

    private static String unique(String prefix) {
        return prefix + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(RANDOM.nextInt(36 * 36 * 36), 36);
    }

    /** A project of the test, removed at the end (cleared first). */
    private record TempProject(Management management, ManagedProject project) implements AutoCloseable {

        static TempProject create(Management management, ManagedProject project) {
            return new TempProject(management, management.projects().create(project));
        }

        int id() {
            return project.id;
        }

        @Override
        public void close() {
            management.clear(project.id);
            management.projects().delete(project.id);
        }
    }

    /** Users made by the test: disabled at the end (users can't be removed). */
    private static final class TempUsers implements AutoCloseable {
        private final Management management;
        private final List<ManagedUser> users = new ArrayList<>();

        TempUsers(Management management) {
            this.management = management;
        }

        ManagedUser create(ManagedUser user) {
            ManagedUser created = management.users().create(user);
            users.add(created);
            return created;
        }

        /** A user that can sign in to the projects with the given password (temporary password changed once). */
        ManagedUser usable(String username, Role role, Set<Integer> projects, String password) {
            ManagedUser user = create(new ManagedUser().username(username).enabled(true)
                    .rolesIds(Set.of((short) role.id)).projectsIds(projects));
            management.resetPassword(username, "tmp-" + password);
            AuthResponse first = signIn(username, "tmp-" + password, null).as(AuthResponse.class);
            verify("changePassword", first.changePassword(), true);
            new Api(username).withToken(first.token()).settings().changePassword(username, "tmp-" + password, password);
            return user;
        }

        @Override
        public void close() {
            for (ManagedUser user : users)
                management.users().tryUpdate(new ManagedUser().username(user.username).enabled(false)
                        .rolesIds(user.rolesIds).projectsIds(Set.of()));
        }
    }

    private static ApiResponse signIn(String username, String password, Integer projectId) {
        return new Api("anonymous").post("/api/public/signin",
                call().body(new SignIn(username, password, projectId == null ? null : projectId.longValue())));
    }

    @Nested
    @DisplayName("Projects")
    class Projects {

        @Test
        void creates_lists_and_changes_a_project(TestProject project) {
            Management management = project.management();
            String name = unique("e2e-m-");
            try (TempProject temp = TempProject.create(management, new ManagedProject().name(name).description("made by a test").enabled(true))) {
                verify("empty", temp.project().empty, true);
                List<String> names = management.projects().list().stream().map(p -> p.name).toList();
                check("projects: contain the new one", () -> assertThat(names).contains(name));

                ManagedProject changed = management.projects().update(temp.project().description("changed").publicVisible(true));

                verifyAll(v -> v
                        .that("description", changed.description, "changed")
                        .that("publicVisible", changed.publicVisible, true));
            }
            List<String> after = management.projects().list().stream().map(p -> p.name).toList();
            check("projects: without the removed one", () -> assertThat(after).doesNotContain(name));
        }

        @Test
        void a_project_name_has_at_most_30_characters(TestProject project) {
            verify("status", project.management().projects().tryCreate(new ManagedProject().name("x".repeat(31)).enabled(true))
                    .status(), 400);
        }

        @Test
        void counts_what_a_project_holds_and_clears_it(TestProject project) {
            project.createBucket("orders");
            project.quietly(ROBOT).bucket("orders", Login.class).insertAll(new Login("a"), new Login("b"));
            project.quietly(ADMIN).settings().tags().create(new Tag().name("new"));
            project.quietly(ADMIN).settings().classes().create(new DataClass().name("order"));
            Management management = project.management();

            Map<String, Integer> content = management.content((int) project.id());

            verify("content", content, Map.of("Buckets", 1, "Data rows", 2, "Tags", 1, "Classes", 1));

            management.clear((int) project.id());
            Map<String, Integer> cleared = management.content((int) project.id());
            check("content: all 0", () -> assertThat(cleared.values()).containsOnly(0));
            verify("data: status", project.as(ROBOT).bucket("orders", Login.class).tryFind(Search.all()).status(), 404);
        }

        @Test
        void a_project_that_is_not_empty_can_not_be_removed(TestProject project) {
            project.createBucket("orders");

            ApiResponse response = project.management().projects().tryDelete(project.id());

            verify("status", response.status(), 409);
            verify("data: status", project.as(ROBOT).bucket("orders", Login.class).tryFind(Search.all()).status(), 200);
        }

        @Test
        void nobody_signs_in_to_a_disabled_project(TestProject project) {
            Management management = project.management();
            ManagedProject self = management.projects().list().stream().filter(p -> p.id == project.id()).findFirst().orElseThrow();

            management.projects().update(self.enabled(false));
            ApiResponse response = signIn(project.user(MEMBER).username(), project.user(MEMBER).password(), (int) project.id());
            management.projects().update(self.enabled(true));

            verifyAll(v -> v
                    .that("status", response.status(), 403)
                    .that("message", response.<String>path("message"), "The project is disabled!"));
        }

        @Test
        void nobody_signs_in_to_an_expired_project(TestProject project) {
            Management management = project.management();
            ManagedProject self = management.projects().list().stream().filter(p -> p.id == project.id()).findFirst().orElseThrow();

            management.projects().update(self.expirationDate(Instant.now().minus(1, ChronoUnit.DAYS)));
            ApiResponse response = signIn(project.user(MEMBER).username(), project.user(MEMBER).password(), (int) project.id());
            management.projects().update(self.expirationDate(null));

            verifyAll(v -> v
                    .that("status", response.status(), 403)
                    .that("message", response.<String>path("message"), "The project expired!"));
        }

    }

    @Nested
    @DisplayName("Users")
    class Users {

        @Test
        void a_new_user_changes_the_temporary_password_and_signs_in(TestProject project) {
            Management management = project.management();
            try (TempUsers users = new TempUsers(management)) {
                String name = unique("e2e-u-");
                users.usable(name, Role.MEMBER, Set.of((int) project.id()), Env.TEST_PASSWORD);

                AuthResponse signedIn = signIn(name, Env.TEST_PASSWORD, (int) project.id()).as(AuthResponse.class);

                verifyAll(v -> v
                        .check("token: not blank", () -> assertThat(signedIn.token()).isNotBlank())
                        .check("changePassword: none", () -> assertThat(signedIn.changePassword()).isNull())
                        .check("roles: MEMBER, ROBOT, AGENT", () -> assertThat(signedIn.roles())
                                .containsExactlyInAnyOrder("MEMBER", "ROBOT", "AGENT")));
                List<ManagedUser> all = management.users().list();
                check("user: in the project", () -> assertThat(all).filteredOn(u -> u.username.equals(name)).singleElement()
                        .satisfies(u -> assertThat(u.projectsIds).containsExactly((int) project.id())));
            }
        }

        @Test
        void a_user_name_and_an_email_are_unique(TestProject project) {
            Management management = project.management();
            try (TempUsers users = new TempUsers(management)) {
                String name = unique("e2e-u-");
                users.create(new ManagedUser().username(name).email(name + "@example.com").enabled(true)
                        .rolesIds(Set.of((short) Role.MEMBER.id)));

                verify("same name: status", management.users().tryCreate(new ManagedUser().username(name).enabled(true)).status(), 406);
                verify("same email: status", management.users().tryCreate(new ManagedUser().username(name + "-2")
                        .email(name.toUpperCase() + "@example.com").enabled(true)).status(), 406);
            }
        }

        @Test
        void a_user_has_one_role(TestProject project) {
            ApiResponse response = project.management().users().tryCreate(new ManagedUser().username(unique("e2e-u-"))
                    .enabled(true).rolesIds(Set.of((short) Role.MEMBER.id, (short) Role.ROBOT.id)));

            verify("status", response.status(), 400);
        }

        @Test
        void a_disabled_user_can_not_sign_in(TestProject project) {
            Management management = project.management();
            try (TempUsers users = new TempUsers(management)) {
                String name = unique("e2e-u-");
                ManagedUser user = users.usable(name, Role.ROBOT, Set.of((int) project.id()), Env.TEST_PASSWORD);

                management.users().update(user.enabled(false).password(null));

                verify("status", signIn(name, Env.TEST_PASSWORD, (int) project.id()).status(), 401);
            }
        }

        @Test
        void a_user_whose_access_expired_can_not_sign_in(TestProject project) {
            Management management = project.management();
            try (TempUsers users = new TempUsers(management)) {
                String name = unique("e2e-u-");
                ManagedUser user = users.usable(name, Role.ROBOT, Set.of((int) project.id()), Env.TEST_PASSWORD);

                management.users().update(user.expirationDate(Instant.now().minus(1, ChronoUnit.DAYS)).password(null));
                ApiResponse response = signIn(name, Env.TEST_PASSWORD, (int) project.id());

                verifyAll(v -> v
                        .that("status", response.status(), 403)
                        .that("message", response.<String>path("message"), "User access has expired!"));
            }
        }

        @Test
        void a_user_taken_out_of_a_project_can_not_sign_in_to_it(TestProject project) {
            Management management = project.management();
            try (TempUsers users = new TempUsers(management)) {
                String name = unique("e2e-u-");
                ManagedUser user = users.usable(name, Role.ROBOT, Set.of((int) project.id()), Env.TEST_PASSWORD);

                management.users().update(user.projectsIds(Set.of()).password(null));

                verify("status", signIn(name, Env.TEST_PASSWORD, (int) project.id()).status(), 403);
            }
        }

        @Test
        void a_new_role_applies_to_the_next_sign_in(TestProject project) {
            Management management = project.management();
            try (TempUsers users = new TempUsers(management)) {
                String name = unique("e2e-u-");
                ManagedUser user = users.usable(name, Role.AGENT, Set.of((int) project.id()), Env.TEST_PASSWORD);

                management.users().update(user.rolesIds(Set.of((short) Role.ADMIN.id)).password(null));
                AuthResponse signedIn = signIn(name, Env.TEST_PASSWORD, (int) project.id()).as(AuthResponse.class);

                check("roles: ADMIN and below", () -> assertThat(signedIn.roles())
                        .containsExactlyInAnyOrder("ADMIN", "MEMBER", "ROBOT", "AGENT"));
                verify("bucket settings: status", new Api(name).withToken(signedIn.token()).settings().buckets().tryList().status(), 200);
            }
        }

    }

    @Nested
    @DisplayName("Datasets")
    class Datasets {

        @Test
        void the_built_in_DEMO_dataset_is_offered(TestProject project) {
            List<DatasetInfo> datasets = project.management().datasets();

            List<DatasetInfo> demo = datasets.stream().filter(d -> d.id().equals("demo")).toList();
            verifyAll(v -> v
                    .that("demo: found", demo.size(), 1)
                    .check("demo.source: built-in", () -> assertThat(demo.get(0).source()).isEqualTo("built-in"))
                    .check("demo.buckets: positive", () -> assertThat(demo.get(0).buckets()).isPositive())
                    .check("demo.rows: positive", () -> assertThat(demo.get(0).rows()).isPositive()));
        }

        @Test
        void a_project_exported_and_imported_into_an_empty_one_has_the_same_content(TestProject project) {
            project.createBucket("orders");
            project.quietly(ROBOT).bucket("orders", Login.class).insertAll(new Login("a"), new Login("b"), new Login("c"));
            project.quietly(ADMIN).settings().tags().create(new Tag().name("new"));
            Management management = project.management();

            JsonNode dataset = management.export((int) project.id());
            try (TempProject copy = TempProject.create(management, new ManagedProject().name(unique("e2e-c-")).enabled(true))) {
                ApiResponse imported = management.importDataset(copy.id(), dataset);

                verifyAll(v -> v
                        .that("status", imported.status(), 200)
                        .that("rows", imported.<Integer>path("rows"), 3));
                Map<String, Integer> original = management.content((int) project.id());
                Map<String, Integer> copied = management.content(copy.id());
                verifyAll(v -> v
                        .that("content: size", copied.size(), original.size())
                        .that("content", copied, original));
            }
        }

        @Test
        void a_dataset_is_imported_only_into_an_empty_project(TestProject project) {
            project.createBucket("orders");
            Management management = project.management();
            JsonNode dataset = management.export((int) project.id());

            verify("status", management.importDataset((int) project.id(), dataset).status(), 409);
        }

        @Test
        void a_broken_dataset_is_refused(TestProject project) {
            Management management = project.management();
            JsonNode dataset = management.export((int) project.id());
            ((ObjectNode) dataset).put("format", 99);
            try (TempProject copy = TempProject.create(management, new ManagedProject().name(unique("e2e-c-")).enabled(true))) {
                verify("format 99: status", management.importDataset(copy.id(), dataset).status(), 400);
                verify("buckets not a list: status", management.importDataset(copy.id(), Map.of("buckets", "not a list")).status(), 400);
            }
        }
    }
}
