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
import pl.databucket.e2e.support.Area;
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
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

/** Settings that decide who sees what: groups, teams, views, the users of the project and their roles. */
@ApiSuite
@Area(Area.SETTINGS)
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

            verifyAll(v -> v
                    .that("groups: size", tree.groups().size(), 1)
                    .that("group.name", tree.groups().get(0).name(), "Development")
                    .that("group.shortName", tree.groups().get(0).shortName(), "DEV")
                    .check("group.bucketsIds: dev and test", () -> assertThat(tree.groups().get(0).bucketsIds())
                            .containsExactlyInAnyOrder(dev.id, test.id)));
        }

        @Test
        void a_group_for_admins_is_hidden_from_a_member(TestProject project) {
            settings.groups().create(new Group().name("Admins").shortName("ADM").roleId((short) Role.ADMIN.id));

            List<String> groups = project.as(MEMBER).settings().accessTree().groupNames();
            check("groups: without Admins", () -> assertThat(groups).doesNotContain("Admins"));
        }

        @Test
        void changes_and_removes_a_group_its_buckets_stay(TestProject project) {
            Bucket dev = settings.buckets().create(SettingsBucketsTest.bucket("dev-users"));
            Group created = settings.groups().create(new Group().name("Development").shortName("DEV")
                    .bucketsIds(Set.of(dev.id)).roleId((short) Role.MEMBER.id));

            Group changed = settings.groups().update(created.name("Dev environment").bucketsIds(Set.of()));
            verifyAll(v -> v
                    .that("name", changed.name, "Dev environment")
                    .check("bucketsIds: none", () -> assertThat(changed.bucketsIds).isNullOrEmpty()));

            settings.groups().delete(created.id);
            verify("groups: size", settings.groups().list().size(), 0);
            verify("buckets", settings.buckets().list().stream().map(b -> b.name).toList(), List.of("dev-users"));
        }

    }

    @Nested
    @DisplayName("Teams")
    class Teams {

        @Test
        void a_bucket_given_to_a_team_is_used_only_by_its_members(TestProject project) {
            Team team = settings.teams().create(new Team().name("QA").usersIds(Set.of(userId(project, MEMBER), userId(project, AGENT))));
            settings.buckets().create(SettingsBucketsTest.bucket("qa-data").roleId(null).teamsIds(Set.of(team.id)));

            verify("member: status", project.as(MEMBER).bucket("qa-data", Login.class).tryFind(Search.all()).status(), 200);
            verify("agent: status", project.as(AGENT).bucket("qa-data", Login.class).tryFind(Search.all()).status(), 200);
            verify("robot: status", project.as(ROBOT).bucket("qa-data", Login.class).tryFind(Search.all()).status(), 404);
        }

        @Test
        void a_bucket_for_a_team_and_a_role_needs_both(TestProject project) {
            Team team = settings.teams().create(new Team().name("QA").usersIds(Set.of(userId(project, MEMBER), userId(project, AGENT))));
            settings.buckets().create(SettingsBucketsTest.bucket("qa-robots").roleId((short) Role.ROBOT.id).teamsIds(Set.of(team.id)));

            verify("member: status", project.as(MEMBER).bucket("qa-robots", Login.class).tryFind(Search.all()).status(), 200);
            // in the team, but below ROBOT
            verify("agent: status", project.as(AGENT).bucket("qa-robots", Login.class).tryFind(Search.all()).status(), 404);
            // ROBOT, but not in the team
            verify("robot: status", project.as(ROBOT).bucket("qa-robots", Login.class).tryFind(Search.all()).status(), 404);
        }

        @Test
        void an_ADMIN_puts_a_user_into_a_team(TestProject project) {
            Team team = settings.teams().create(new Team().name("QA"));
            ProjectUser robot = settings.users().list().stream()
                    .filter(u -> u.username.equals(project.user(ROBOT).username())).findFirst().orElseThrow();

            ProjectUser changed = settings.users().update(new ProjectUser().username(robot.username).teamsIds(Set.of(team.id)));

            verify("teamsIds", changed.teamsIds, List.of(team.id));
            List<Team> teams = settings.teams().list();
            verifyAll(v -> v
                    .that("teams: size", teams.size(), 1)
                    .that("team.usersIds", teams.get(0).usersIds, List.of(robot.id)));
        }

        @Test
        void changes_and_removes_a_team(TestProject project) {
            Team created = settings.teams().create(new Team().name("QA").description("testers"));

            verify("description", settings.teams().update(created.description("testers and automation")).description,
                    "testers and automation");

            settings.teams().delete(created.id);
            verify("teams: size", settings.teams().list().size(), 0);
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

            verifyAll(v -> v
                    .that("views", tree.viewNames(), List.of("all orders"))
                    .that("view.featuresIds", tree.views().get(0).featuresIds(), List.of((short) 1, (short) 2))
                    .that("view.sort", tree.views().get(0).sort(), "desc(id)"));
        }

        @Test
        void changes_and_removes_a_view(TestProject project) {
            Bucket bucket = settings.buckets().create(SettingsBucketsTest.bucket("orders"));
            View created = settings.views().create(new View().name("all").columnsId(columns().id)
                    .bucketsIds(Set.of(bucket.id)).roleId((short) Role.MEMBER.id));

            View changed = settings.views().update(created.name("everything").sort("asc($.customer.name)"));
            verifyAll(v -> v
                    .that("name", changed.name, "everything")
                    .that("sort", changed.sort, "asc($.customer.name)"));

            settings.views().delete(created.id);
            verify("views: size", settings.views().list().size(), 0);
        }

        @Test
        void a_view_starts_sorted_only_by_an_allowed_field(TestProject project) {
            Columns columns = columns();
            View view = new View().name("all").columnsId(columns.id).roleId((short) Role.MEMBER.id);

            verify("status", settings.views().tryCreate(view.sort("desc(password)")).status(), 400);
            verify("status", settings.views().tryCreate(view.sort("asc($.a'))--)")).status(), 400);
            verify("sort", settings.views().create(view.sort("asc($.customer.name)")).sort, "asc($.customer.name)");
        }

        @Test
        void a_view_needs_columns_and_filters_that_exist(TestProject project) {
            View noColumns = new View().name("all").columnsId(999999L).roleId((short) Role.MEMBER.id);
            View noFilter = new View().name("all").columnsId(columns().id).filterId(999999L).roleId((short) Role.MEMBER.id);

            verify("no columns: status", settings.views().tryCreate(noColumns).status(), 404);
            verify("no filter: status", settings.views().tryCreate(noFilter).status(), 404);
        }
    }

    @Nested
    @DisplayName("Users and roles")
    class Users {

        @Test
        void an_ADMIN_sees_the_users_of_the_project_only(TestProject project) {
            List<ProjectUser> users = settings.users().list();

            verifyAll(v -> v
                    .check("users: the four of the project", () -> assertThat(users).extracting(u -> u.username)
                            .containsExactlyInAnyOrder(project.user(ADMIN).username(), project.user(MEMBER).username(),
                                    project.user(ROBOT).username(), project.user(AGENT).username()))
                    .check("robot: rolesIds ROBOT", () -> assertThat(users)
                            .filteredOn(u -> u.username.equals(project.user(ROBOT).username()))
                            .singleElement().satisfies(u -> assertThat(u.rolesIds).containsExactly((short) Role.ROBOT.id))));
        }

        @Test
        void the_roles_from_the_lowest_are_AGENT_ROBOT_MEMBER_ADMIN_SUPER(TestProject project) {
            List<RoleInfo> roles = project.as(MEMBER).settings().roles();
            check("roles: the five", () -> assertThat(roles).extracting(RoleInfo::name)
                    .containsExactlyInAnyOrder("SUPER", "ADMIN", "MEMBER", "ROBOT", "AGENT"));
        }

        @Test
        void a_user_changes_their_password_and_signs_in_with_the_new_one(TestProject project) {
            TestUser member = project.user(MEMBER);
            Api signedIn = project.as(MEMBER);

            ApiResponse wrongOld = signedIn.settings().changePassword(member.username(), "not my password", "N3w-Passw0rd!");
            verify("wrong old password: status", wrongOld.status(), 406);
            ApiResponse changed = signedIn.settings().changePassword(member.username(), member.password(), "N3w-Passw0rd!");
            verify("status", changed.status(), 200);

            verify("old password: status", signIn(member.username(), member.password(), project.id()).status(), 401);
            verify("new password: status", signIn(member.username(), "N3w-Passw0rd!", project.id()).status(), 200);
        }

        @Test
        void a_member_can_not_change_the_password_of_a_person(TestProject project) {
            TestUser admin = project.user(ADMIN);

            ApiResponse response = project.as(MEMBER).settings().changePassword(admin.username(), admin.password(), "N3w-Passw0rd!");

            verifyAll(v -> v
                    .that("status", response.status(), 406)
                    .that("message", response.<String>path("message"), "You cannot change the password of this user!"));
            verify("admin signs in: status", signIn(admin.username(), admin.password(), project.id()).status(), 200);
        }

        @Test
        @DisplayName("a member changes the password of a ROBOT or AGENT account, knowing the current one")
        void program_account_password(TestProject project) {
            TestUser agent = project.user(AGENT);
            project.token(AGENT);

            ApiResponse wrong = project.as(MEMBER).settings().changePassword(agent.username(), "guess", "N3w-Passw0rd!");
            verify("wrong password: status", wrong.status(), 406);
            ApiResponse changed = project.as(MEMBER).settings().changePassword(agent.username(), agent.password(), "N3w-Passw0rd!");
            verify("status", changed.status(), 200);

            verify("new password: status", signIn(agent.username(), "N3w-Passw0rd!", project.id()).status(), 200);
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
                verify("status", switched.status(), 200);
                String token = switched.as(AuthResponse.class).token();

                verify("other project data: status",
                        new Api("member").withToken(token).bucket("other-orders", Login.class).tryFind(Search.all()).status(), 200);
                // the admin of this project isn't assigned to the other one
                verify("admin: status", project.as(ADMIN).settings().changeProject(other.id()).status(), 403);
            }
        }
    }

    private static ApiResponse signIn(String username, String password, long projectId) {
        return new Api("anonymous").post("/api/public/signin", call().body(new SignIn(username, password, projectId)));
    }
}
