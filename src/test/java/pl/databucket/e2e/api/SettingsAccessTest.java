package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.client.Settings;
import pl.databucket.e2e.model.AccessTree;
import pl.databucket.e2e.model.AuthResponse;
import pl.databucket.e2e.model.Bucket;
import pl.databucket.e2e.model.Column;
import pl.databucket.e2e.model.Columns;
import pl.databucket.e2e.model.ColumnsConfig;
import pl.databucket.e2e.model.Group;
import pl.databucket.e2e.model.ManagedUser;
import pl.databucket.e2e.model.ProjectUser;
import pl.databucket.e2e.model.RoleInfo;
import pl.databucket.e2e.model.SignIn;
import pl.databucket.e2e.model.Team;
import pl.databucket.e2e.model.View;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.Role;
import pl.databucket.e2e.support.TestProject;
import pl.databucket.e2e.support.TestUser;
import pl.databucket.e2e.support.UserKey;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.AGENT;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;

/** Settings that decide who sees what: groups, teams, views, the users of the project and their roles. */
@ApiSuite
@DisplayName("Settings: groups, teams, views, users")
class SettingsAccessTest {

    record Login(String login) {
    }

    private Settings settings;

    @BeforeEach
    void admin(TestProject project) {
        settings = project.as(ADMIN).settings();
    }

    private Long userId(TestProject project, UserKey key) {
        return settings.users().list().stream().filter(u -> u.username.equals(project.user(key).username()))
                .findFirst().orElseThrow().id;
    }

    private Columns columns() {
        return settings.columns().create(new Columns().name("cols").configuration(new ColumnsConfig()
                .columns(List.of(new Column().uuid("uuid_data_id").enabled(true).sorting(true).filtering(true).editable("never")))
                .properties(List.of())));
    }

    @Nested
    @DisplayName("Groups")
    class Groups {

        @Test
        void a_member_sees_a_group_with_its_buckets(TestProject project) {
            Bucket dev = settings.buckets().create(SettingsBucketsTest.bucket("dev-users"));
            Bucket test = settings.buckets().create(SettingsBucketsTest.bucket("test-users"));
            settings.groups().create(new Group().name("Development").shortName("DEV").bucketsIds(Set.of(dev.id, test.id))
                    .roleId((short) Role.MEMBER.id));

            AccessTree tree = project.as(MEMBER).settings().accessTree();

            assertThat(tree.groups()).singleElement().satisfies(group -> {
                assertThat(group.name()).isEqualTo("Development");
                assertThat(group.shortName()).isEqualTo("DEV");
                assertThat(group.bucketsIds()).containsExactlyInAnyOrder(dev.id, test.id);
            });
        }

        @Test
        void a_group_for_admins_is_hidden_from_a_member(TestProject project) {
            settings.groups().create(new Group().name("Admins").shortName("ADM").roleId((short) Role.ADMIN.id));

            assertThat(project.as(MEMBER).settings().accessTree().groupNames()).doesNotContain("Admins");
        }

        @Test
        void changes_and_removes_a_group_its_buckets_stay(TestProject project) {
            Bucket dev = settings.buckets().create(SettingsBucketsTest.bucket("dev-users"));
            Group created = settings.groups().create(new Group().name("Development").shortName("DEV")
                    .bucketsIds(Set.of(dev.id)).roleId((short) Role.MEMBER.id));

            Group changed = settings.groups().update(created.name("Dev environment").bucketsIds(Set.of()));
            assertThat(changed.name).isEqualTo("Dev environment");
            assertThat(changed.bucketsIds).isNullOrEmpty();

            settings.groups().delete(created.id);
            assertThat(settings.groups().list()).isEmpty();
            assertThat(settings.buckets().list()).extracting(b -> b.name).containsExactly("dev-users");
        }

    }

    @Nested
    @DisplayName("Teams")
    class Teams {

        @Test
        void a_bucket_given_to_a_team_is_used_only_by_its_members(TestProject project) {
            Team team = settings.teams().create(new Team().name("QA").usersIds(Set.of(userId(project, MEMBER), userId(project, AGENT))));
            settings.buckets().create(SettingsBucketsTest.bucket("qa-data").roleId(null).teamsIds(Set.of(team.id)));

            assertThat(project.as(MEMBER).bucket("qa-data", Login.class).tryFind(Search.all()).status()).isEqualTo(200);
            assertThat(project.as(AGENT).bucket("qa-data", Login.class).tryFind(Search.all()).status()).isEqualTo(200);
            assertThat(project.as(ROBOT).bucket("qa-data", Login.class).tryFind(Search.all()).status()).isEqualTo(404);
        }

        @Test
        void a_bucket_for_a_team_and_a_role_needs_both(TestProject project) {
            Team team = settings.teams().create(new Team().name("QA").usersIds(Set.of(userId(project, MEMBER), userId(project, AGENT))));
            settings.buckets().create(SettingsBucketsTest.bucket("qa-robots").roleId((short) Role.ROBOT.id).teamsIds(Set.of(team.id)));

            assertThat(project.as(MEMBER).bucket("qa-robots", Login.class).tryFind(Search.all()).status()).isEqualTo(200);
            // in the team, but below ROBOT
            assertThat(project.as(AGENT).bucket("qa-robots", Login.class).tryFind(Search.all()).status()).isEqualTo(404);
            // ROBOT, but not in the team
            assertThat(project.as(ROBOT).bucket("qa-robots", Login.class).tryFind(Search.all()).status()).isEqualTo(404);
        }

        @Test
        void an_ADMIN_puts_a_user_into_a_team(TestProject project) {
            Team team = settings.teams().create(new Team().name("QA"));
            ProjectUser robot = settings.users().list().stream()
                    .filter(u -> u.username.equals(project.user(ROBOT).username())).findFirst().orElseThrow();

            ProjectUser changed = settings.users().update(new ProjectUser().username(robot.username).teamsIds(Set.of(team.id)));

            assertThat(changed.teamsIds).containsExactly(team.id);
            assertThat(settings.teams().list()).singleElement().satisfies(t -> assertThat(t.usersIds).containsExactly(robot.id));
        }

        @Test
        void changes_and_removes_a_team(TestProject project) {
            Team created = settings.teams().create(new Team().name("QA").description("testers"));

            assertThat(settings.teams().update(created.description("testers and automation")).description).isEqualTo("testers and automation");

            settings.teams().delete(created.id);
            assertThat(settings.teams().list()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Views")
    class Views {

        @Test
        void a_member_sees_the_views_of_a_bucket_for_members_only(TestProject project) {
            Bucket bucket = settings.buckets().create(SettingsBucketsTest.bucket("orders"));
            Columns columns = columns();
            settings.views().create(new View().name("all orders").columnsId(columns.id).bucketsIds(Set.of(bucket.id))
                    .roleId((short) Role.MEMBER.id).featuresIds(List.of((short) 1, (short) 2)).sort("desc(id)"));
            settings.views().create(new View().name("admin view").columnsId(columns.id).bucketsIds(Set.of(bucket.id))
                    .roleId((short) Role.ADMIN.id));

            AccessTree tree = project.as(MEMBER).settings().accessTree();

            assertThat(tree.viewNames()).containsExactly("all orders");
            assertThat(tree.views().get(0).featuresIds()).containsExactly((short) 1, (short) 2);
            assertThat(tree.views().get(0).sort()).isEqualTo("desc(id)");
        }

        @Test
        void changes_and_removes_a_view(TestProject project) {
            Bucket bucket = settings.buckets().create(SettingsBucketsTest.bucket("orders"));
            View created = settings.views().create(new View().name("all").columnsId(columns().id)
                    .bucketsIds(Set.of(bucket.id)).roleId((short) Role.MEMBER.id));

            View changed = settings.views().update(created.name("everything").sort("asc($.customer.name)"));
            assertThat(changed.name).isEqualTo("everything");
            assertThat(changed.sort).isEqualTo("asc($.customer.name)");

            settings.views().delete(created.id);
            assertThat(settings.views().list()).isEmpty();
        }

        @Test
        void a_view_starts_sorted_only_by_an_allowed_field(TestProject project) {
            Columns columns = columns();
            View view = new View().name("all").columnsId(columns.id).roleId((short) Role.MEMBER.id);

            assertThat(settings.views().tryCreate(view.sort("desc(password)")).status()).isEqualTo(400);
            assertThat(settings.views().tryCreate(view.sort("asc($.a'))--)")).status()).isEqualTo(400);
            assertThat(settings.views().create(view.sort("asc($.customer.name)")).sort).isEqualTo("asc($.customer.name)");
        }

        @Test
        void a_view_needs_columns_and_filters_that_exist(TestProject project) {
            View noColumns = new View().name("all").columnsId(999999L).roleId((short) Role.MEMBER.id);
            View noFilter = new View().name("all").columnsId(columns().id).filterId(999999L).roleId((short) Role.MEMBER.id);

            assertThat(settings.views().tryCreate(noColumns).status()).isEqualTo(404);
            assertThat(settings.views().tryCreate(noFilter).status()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("Users and roles")
    class Users {

        @Test
        void an_ADMIN_sees_the_users_of_the_project_only(TestProject project) {
            List<ProjectUser> users = settings.users().list();

            assertThat(users).extracting(u -> u.username).containsExactlyInAnyOrder(
                    project.user(ADMIN).username(), project.user(MEMBER).username(),
                    project.user(ROBOT).username(), project.user(AGENT).username());
            assertThat(users).filteredOn(u -> u.username.equals(project.user(ROBOT).username()))
                    .singleElement().satisfies(u -> assertThat(u.rolesIds).containsExactly((short) Role.ROBOT.id));
        }

        @Test
        void the_roles_from_the_lowest_are_AGENT_ROBOT_MEMBER_ADMIN_SUPER(TestProject project) {
            assertThat(project.as(MEMBER).settings().roles()).extracting(RoleInfo::name)
                    .containsExactlyInAnyOrder("SUPER", "ADMIN", "MEMBER", "ROBOT", "AGENT");
        }

        @Test
        void a_user_changes_their_password_and_signs_in_with_the_new_one(TestProject project) {
            TestUser member = project.user(MEMBER);
            Api signedIn = project.as(MEMBER);

            ApiResponse wrongOld = signedIn.settings().changePassword(member.username(), "not my password", "N3w-Passw0rd!");
            ApiResponse changed = signedIn.settings().changePassword(member.username(), member.password(), "N3w-Passw0rd!");

            assertThat(wrongOld.status()).isEqualTo(406);
            assertThat(changed.status()).isEqualTo(200);
            assertThat(signIn(member.username(), member.password(), project.id()).status()).isEqualTo(401);
            assertThat(signIn(member.username(), "N3w-Passw0rd!", project.id()).status()).isEqualTo(200);
        }

        @Test
        void a_member_can_not_change_the_password_of_a_person(TestProject project) {
            TestUser admin = project.user(ADMIN);

            ApiResponse response = project.as(MEMBER).settings().changePassword(admin.username(), admin.password(), "N3w-Passw0rd!");

            assertThat(response.status()).isEqualTo(406);
            assertThat(response.<String>path("message")).isEqualTo("You cannot change the password of this user!");
            assertThat(signIn(admin.username(), admin.password(), project.id()).status()).isEqualTo(200);
        }

        @Test
        @DisplayName("a member changes the password of a ROBOT or AGENT account, knowing the current one")
        void program_account_password(TestProject project) {
            TestUser agent = project.user(AGENT);
            project.token(AGENT);

            ApiResponse wrong = project.as(MEMBER).settings().changePassword(agent.username(), "guess", "N3w-Passw0rd!");
            ApiResponse changed = project.as(MEMBER).settings().changePassword(agent.username(), agent.password(), "N3w-Passw0rd!");

            assertThat(wrong.status()).isEqualTo(406);
            assertThat(changed.status()).isEqualTo(200);
            assertThat(signIn(agent.username(), "N3w-Passw0rd!", project.id()).status()).isEqualTo(200);
        }

        @Test
        void a_user_in_two_projects_switches_between_them(TestProject project) {
            try (TestProject other = TestProject.create()) {
                other.createBucket("other-orders");
                ManagedUser member = project.management().users().list().stream()
                        .filter(u -> u.username.equals(project.user(MEMBER).username())).findFirst().orElseThrow();
                Set<Integer> projects = new HashSet<>(member.projectsIds);
                projects.add((int) other.id());
                project.management().users().update(member.projectsIds(projects).password(null));

                ApiResponse switched = project.as(MEMBER).settings().changeProject(other.id());
                String token = switched.as(AuthResponse.class).token();

                assertThat(switched.status()).isEqualTo(200);
                assertThat(new Api("member").withToken(token).bucket("other-orders", Login.class).tryFind(Search.all()).status())
                        .isEqualTo(200);
                // the admin of this project isn't assigned to the other one
                assertThat(project.as(ADMIN).settings().changeProject(other.id()).status()).isEqualTo(403);
            }
        }
    }

    private static ApiResponse signIn(String username, String password, long projectId) {
        return new Api("anonymous").post("/api/public/signin", call().body(new SignIn(username, password, projectId)));
    }
}
