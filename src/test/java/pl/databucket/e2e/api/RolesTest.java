package pl.databucket.e2e.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.model.Bucket;
import pl.databucket.e2e.model.Icon;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.Role;
import pl.databucket.e2e.support.TestProject;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;

@ApiSuite
@Area(Area.ACCESS)
@DisplayName("Roles")
class RolesTest {

    @Test
    void a_MEMBER_can_not_list_the_bucket_settings(TestProject project) {
        verify("status", project.as(MEMBER).settings().buckets().tryList().status(), 403);
    }

    @Test
    void a_ROBOT_can_not_manage_buckets(TestProject project) {
        Bucket bucket = new Bucket().name("not-allowed").icon(new Icon().name("Storage"));

        verify("status", project.as(ROBOT).settings().buckets().tryCreate(bucket).status(), 403);
    }

    @Test
    void an_ADMIN_creates_a_bucket_that_the_project_members_see(TestProject project) {
        project.as(ADMIN).settings().buckets().create(new Bucket().name("customers").icon(new Icon().name("Storage"))
                .history(true).roleId((short) Role.MEMBER.id));

        var names = project.as(MEMBER).settings().accessTree().bucketNames();
        check("buckets: contain customers", () -> assertThat(names).contains("customers"));
    }
}
