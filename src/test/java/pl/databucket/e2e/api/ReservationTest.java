package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.support.Api;
import pl.databucket.e2e.support.ApiResponse;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.Call.call;
import static pl.databucket.e2e.support.Json.arr;
import static pl.databucket.e2e.support.Json.obj;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.AGENT;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;

@ApiSuite
@DisplayName("Reservation")
class ReservationTest {

    private static final String BUCKET = "accounts";
    private static final String DATA = "/api/bucket/" + BUCKET;

    private Api robot;

    @BeforeEach
    void bucket(TestProject project) {
        project.createBucket(BUCKET);
        robot = project.as(ROBOT);
    }

    private static void seed(TestProject project, int count) {
        seed(project, count, i -> obj("login", "user-" + i));
    }

    private static void seed(TestProject project, int count, IntFunction<Map<String, Object>> properties) {
        List<Object> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) rows.add(obj("properties", properties.apply(i)));
        project.quietly(ROBOT).post(DATA + "/multi", call().body(rows).expect(201));
    }

    private static ApiResponse reserve(Api api, String query) {
        return reserve(api, query, obj());
    }

    private static ApiResponse reserve(Api api, String query, Map<String, Object> body) {
        return api.post(DATA + "/reserve?" + query, call().body(body).expect(200));
    }

    private static List<Number> ids(ApiResponse response) {
        List<Number> ids = response.path("data.id");
        return ids == null ? List.of() : ids;
    }

    @Test
    @DisplayName("limit 0 only counts the free rows")
    void limit_0(TestProject project) {
        seed(project, 4);

        ApiResponse count = reserve(robot, "limit=0");
        ApiResponse again = reserve(robot, "limit=0");

        assertThat(count.<Integer>path("reserved")).isZero();
        assertThat(count.<Integer>path("available")).isEqualTo(4);
        assertThat(again.<Integer>path("available")).isEqualTo(4);
    }

    @Test
    @DisplayName("says how many free rows are left, unless count=false")
    void available(TestProject project) {
        seed(project, 5);

        ApiResponse counted = reserve(robot, "limit=2");
        ApiResponse notCounted = reserve(robot, "limit=1&count=false");

        assertThat(counted.<Integer>path("reserved")).isEqualTo(2);
        assertThat(counted.<Integer>path("available")).isEqualTo(3);
        assertThat(notCounted.<Integer>path("reserved")).isEqualTo(1);
        assertThat(notCounted.<Object>path("available")).isNull();
    }

    @Test
    void reserves_only_the_rows_matching_the_rules(TestProject project) {
        seed(project, 6, i -> obj("login", "user-" + i, "plan", i % 2 == 1 ? "free" : "premium"));

        ApiResponse response = reserve(robot, "limit=10", obj("rules", arr(arr("$.plan", "==", "premium"))));

        assertThat(response.<Integer>path("reserved")).isEqualTo(3);
        assertThat(response.<List<String>>path("data.properties.plan")).containsExactly("premium", "premium", "premium");
        assertThat(response.<Integer>path("available")).isZero();
    }

    @Test
    void when_nothing_is_free_there_is_a_message_not_an_error(TestProject project) {
        seed(project, 1);
        reserve(robot, "limit=1");

        ApiResponse response = reserve(robot, "limit=1");

        assertThat(response.<Integer>path("reserved")).isZero();
        assertThat(response.<Integer>path("available")).isZero();
        assertThat(response.<String>path("message")).isEqualTo("No data matches the rules!");
    }

    @Test
    void rows_reserved_by_one_user_are_not_given_to_another(TestProject project) {
        seed(project, 3);
        Api member = project.as(MEMBER);

        ApiResponse memberGot = reserve(member, "limit=2");
        ApiResponse robotGot = reserve(robot, "limit=5");

        assertThat(ids(memberGot)).hasSize(2);
        assertThat(ids(robotGot)).hasSize(1);
        assertThat(ids(robotGot)).doesNotContainAnyElementsOf(ids(memberGot));
        assertThat(robotGot.<List<String>>path("data.owner")).containsExactly(project.user(ROBOT).username());
    }

    @Test
    void parallel_reservations_never_get_the_same_row(TestProject project) throws Exception {
        seed(project, 30);
        List<Api> clients = List.of(project.quietly(ROBOT), project.quietly(MEMBER), project.quietly(AGENT));

        List<Number> ids = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(30);
        try {
            List<CompletableFuture<ApiResponse>> calls = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                Api client = clients.get(i % clients.size());
                calls.add(CompletableFuture.supplyAsync(() -> reserve(client, "limit=1&sort=random"), pool));
            }
            for (CompletableFuture<ApiResponse> call : calls) ids.addAll(ids(call.get()));
        } finally {
            pool.shutdown();
        }

        assertThat(new HashSet<>(ids)).hasSize(ids.size());
        // every row is taken once at most; a reservation that met only taken rows got none
        assertThat(ids).isNotEmpty();
        assertThat(reserve(robot, "limit=0").<Integer>path("available")).isEqualTo(30 - ids.size());
    }

    @Test
    void released_rows_can_be_reserved_again(TestProject project) {
        seed(project, 1);
        Api member = project.as(MEMBER);
        Number id = ids(reserve(robot, "limit=1")).get(0);

        robot.put(DATA + "/" + id, call().body(obj("reserved", false)).expect(200));
        ApiResponse again = reserve(member, "limit=1");

        assertThat(ids(again)).containsExactly(id);
        assertThat(again.<List<String>>path("data.owner")).containsExactly(project.user(MEMBER).username());
    }

    @Test
    @DisplayName("an ADMIN reserves rows for another user; others can not")
    void for_another_user(TestProject project) {
        seed(project, 2);
        Api admin = project.as(ADMIN);
        String member = project.user(MEMBER).username();

        ApiResponse forMember = reserve(admin, "limit=1", obj("targetOwnerUsername", member));
        ApiResponse robotTries = reserve(robot, "limit=1", obj("targetOwnerUsername", member));

        assertThat(forMember.<List<String>>path("data.owner")).containsExactly(member);
        assertThat(robotTries.<List<String>>path("data.owner")).containsExactly(project.user(ROBOT).username());
    }

    @Test
    void a_negative_limit_is_refused(TestProject project) {
        ApiResponse response = robot.post(DATA + "/reserve?limit=-1", call().body(obj()));

        assertThat(response.status()).isEqualTo(406);
    }

    @Test
    void reserving_by_rules_changes_only_the_free_rows(TestProject project) {
        seed(project, 3);
        reserve(project.as(MEMBER), "limit=1");

        ApiResponse response = robot.put(DATA, call().body(obj("rules", arr(arr("id", ">", 0)), "reserved", true)).expect(200));

        assertThat(response.<String>path("message")).isEqualTo("Modified 2 data row(s)");
        ApiResponse owners = robot.post(DATA + "/get?limit=10", call().body(obj()).expect(200));
        assertThat(owners.<List<String>>path("data.owner")).containsExactly(
                project.user(MEMBER).username(), project.user(ROBOT).username(), project.user(ROBOT).username());
    }

    @Test
    void in_a_bucket_with_protected_data_each_user_sees_only_the_rows_they_reserved(TestProject project) {
        project.createBucket("vault", true, true);
        Api admin = project.quietly(ADMIN);
        admin.post("/api/bucket/vault/multi", call().body(arr(
                obj("properties", obj("login", "a")),
                obj("properties", obj("login", "b")),
                obj("properties", obj("login", "c")))).expect(201));
        Api member = project.as(MEMBER);

        ApiResponse reserved = member.post("/api/bucket/vault/reserve?limit=1", call().body(obj()).expect(200));
        ApiResponse memberSees = member.post("/api/bucket/vault/get?limit=10", call().body(obj()).expect(200));
        ApiResponse robotSees = robot.post("/api/bucket/vault/get?limit=10", call().body(obj()).expect(200));
        ApiResponse adminSees = admin.post("/api/bucket/vault/get?limit=10", call().body(obj()).expect(200));

        // free rows are counted for everyone, as anyone may reserve them
        assertThat(reserved.<Integer>path("available")).isEqualTo(2);
        assertThat(memberSees.<List<String>>path("data.properties.login"))
                .isEqualTo(reserved.<List<String>>path("data.properties.login"));
        assertThat(robotSees.<List<Object>>path("data")).isNullOrEmpty();
        assertThat(adminSees.<Integer>path("total")).isEqualTo(3);
    }
}
