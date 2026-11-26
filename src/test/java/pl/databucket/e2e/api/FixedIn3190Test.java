package pl.databucket.e2e.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Management;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.client.Settings;
import pl.databucket.e2e.model.Column;
import pl.databucket.e2e.model.ColumnProperty;
import pl.databucket.e2e.model.Columns;
import pl.databucket.e2e.model.ColumnsConfig;
import pl.databucket.e2e.model.Filter;
import pl.databucket.e2e.model.FilterConfig;
import pl.databucket.e2e.model.ManagedProject;
import pl.databucket.e2e.model.ManagedUser;
import pl.databucket.e2e.model.View;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.Role;
import pl.databucket.e2e.support.TestProject;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.client.Rules.logic;
import static pl.databucket.e2e.client.Rules.var;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

/**
 * What these tests found wrong in the server and 3.19.0 fixed (databucket-server#266-#270): kept as regression tests.
 * The comment of each test says how it was before.
 */
@ApiSuite
@DisplayName("Fixed in 3.19.0")
class FixedIn3190Test {

    record Login(String login) {
    }

    // ---------------------------------------------------------------- tokens

    @Test
    // before 3.19.0: a token stays valid until it expires (24 h) after its project is disabled; fixed by databucket-server#268
    void a_token_stops_working_when_its_project_is_disabled(TestProject project) {
        Management management = project.management();
        project.createBucket("orders");
        BucketApi<Login> orders = project.as(ROBOT).bucket("orders", Login.class);
        ManagedProject self = management.projects().list().stream().filter(p -> p.id == project.id()).findFirst().orElseThrow();

        management.projects().update(self.enabled(false));
        int status = orders.tryFind(Search.all()).status();
        management.projects().update(self.enabled(true));

        check("status of the call with the disabled project: 401 or 403", () -> assertThat(status).isIn(401, 403));
    }

    @Test
    // before 3.19.0: a token stays valid until it expires (24 h) after its user is disabled; fixed by databucket-server#268
    void a_token_stops_working_when_its_user_is_disabled(TestProject project) {
        project.createBucket("orders");
        String token = project.token(ROBOT);
        ManagedUser robot = robot(project);

        project.management().users().update(robot.enabled(false));
        int status = new Api("robot").withToken(token).bucket("orders", Login.class).tryFind(Search.all()).status();

        verify("status", status, 401);
    }

    @Test
    // before 3.19.0: a token stays valid until it expires (24 h) after its user is taken out of the project; fixed by databucket-server#268
    void a_token_stops_working_when_its_user_leaves_the_project(TestProject project) {
        project.createBucket("orders");
        String token = project.token(ROBOT);
        ManagedUser robot = robot(project);

        project.management().users().update(robot.projectsIds(Set.of()));
        int status = new Api("robot").withToken(token).bucket("orders", Login.class).tryFind(Search.all()).status();

        check("status: 401 or 403", () -> assertThat(status).isIn(401, 403));
    }

    @Test
    // before 3.19.0: the roles written into a token apply until it expires (24 h), also after a role is taken away; fixed by databucket-server#268
    void a_role_taken_away_applies_at_once(TestProject project) {
        String token = project.token(ADMIN);
        ManagedUser admin = project.management().users().list().stream()
                .filter(u -> u.username.equals(project.user(ADMIN).username())).findFirst().orElseThrow();

        project.management().users().update(admin.rolesIds(Set.of((short) Role.MEMBER.id)));
        int status = new Api("admin").withToken(token).settings().buckets().tryList().status();

        verify("status", status, 403);
    }

    private static ManagedUser robot(TestProject project) {
        return project.management().users().list().stream()
                .filter(u -> u.username.equals(project.user(ROBOT).username())).findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------- removing objects in use

    @Test
    // before 3.19.0: removing columns used by a view answers 500 (ItemAlreadyUsedException not handled), classes and enums answer 403; fixed by databucket-server#269
    void columns_used_by_a_view_can_not_be_removed(TestProject project) {
        Settings settings = project.as(ADMIN).settings();
        Columns columns = columns(settings);
        settings.views().create(new View().name("all").columnsId(columns.id).roleId((short) Role.MEMBER.id));

        ApiResponse response = settings.columns().tryDelete(columns.id);
        verify("status", response.status(), 403);

        List<Columns> left = settings.columns().list();
        verify("columns: size", left.size(), 1);
    }

    @Test
    // before 3.19.0: removing a filter used by a view answers 500 (ItemAlreadyUsedException not handled), classes and enums answer 403; fixed by databucket-server#269
    void a_filter_used_by_a_view_can_not_be_removed(TestProject project) {
        Settings settings = project.as(ADMIN).settings();
        Filter filter = settings.filters().create(new Filter().name("new").configuration(new FilterConfig()
                .properties(List.of()).logic(logic("==", var("$.status"), "NEW"))));
        settings.views().create(new View().name("new").columnsId(columns(settings).id).filterId(filter.id)
                .roleId((short) Role.MEMBER.id));

        ApiResponse response = settings.filters().tryDelete(filter.id);
        verify("status", response.status(), 403);

        List<Filter> left = settings.filters().list();
        verify("filters: size", left.size(), 1);
    }

    private static Columns columns(Settings settings) {
        return settings.columns().create(new Columns().name("cols").configuration(new ColumnsConfig()
                .columns(List.of(new Column().uuid("uuid_data_id").enabled(true).sorting(true).filtering(true).editable("never")))
                .properties(List.of(new ColumnProperty().path("$.status").type("string").uuid("u-status").title("Status")))));
    }

    // ---------------------------------------------------------------- dates

    @Test
    // before 3.19.0: spring.jackson.date-format yyyy-MM-dd'T'HH:mm:ss.SSS'Z' refuses ISO 8601 without milliseconds (400); fixed by databucket-server#270
    void a_date_without_milliseconds_is_accepted(TestProject project) {
        ApiResponse response = project.management().projects().tryCreate(Map.of(
                "name", "e2e-date-" + System.nanoTime() % 100000, "enabled", true, "expirationDate", "2030-01-01T00:00:00Z"));
        if (response.status() == 201) project.management().projects().delete(response.<Number>path("id"));

        verifyAll(v -> v
                .that("created: status", response.status(), 201)
                .that("expirationDate", response.<String>path("expirationDate"), "2030-01-01T00:00:00.000Z"));
    }

    @Test
    // before 3.19.0: the lenient date format reads nanoseconds as milliseconds and moves the date by days without an error; fixed by databucket-server#270
    void a_date_with_nanoseconds_is_not_moved(TestProject project) {
        ApiResponse response = project.management().projects().tryCreate(Map.of(
                "name", "e2e-date-" + System.nanoTime() % 100000, "enabled", true, "expirationDate", "2030-01-01T00:00:00.123456789Z"));
        if (response.status() == 201) project.management().projects().delete(response.<Number>path("id"));

        // either refused, or the same moment (rounded to milliseconds)
        if (response.status() != 400)
            verify("expirationDate", response.<String>path("expirationDate"), "2030-01-01T00:00:00.123Z");
        else
            verify("refused: status", response.status(), 400);
    }
}
