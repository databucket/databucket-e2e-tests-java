package pl.databucket.e2e.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;

@ApiSuite
@DisplayName("Roles")
class RolesTest {

    @Test
    void a_MEMBER_can_not_list_the_bucket_settings(TestProject project) {
        Api member = project.as(MEMBER);

        ApiResponse response = member.get("/api/buckets");

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void a_ROBOT_can_not_manage_buckets(TestProject project) {
        Api robot = project.as(ROBOT);

        ApiResponse response = robot.post("/api/buckets", call().body(obj("name", "not-allowed", "icon", obj("name", "Storage"))));

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void an_ADMIN_creates_a_bucket_that_the_project_members_see(TestProject project) {
        Api admin = project.as(ADMIN);
        admin.post("/api/buckets", call()
                .body(obj("name", "customers", "icon", obj("name", "Storage"), "history", true, "roleId", 3))
                .expect(201));

        Api member = project.as(MEMBER);
        ApiResponse tree = member.get("/api/session/access-tree", call().expect(200));

        assertThat(tree.<List<String>>path("buckets.name")).contains("customers");
    }
}
