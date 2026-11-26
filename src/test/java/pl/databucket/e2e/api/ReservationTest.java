package pl.databucket.e2e.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Change;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.model.DataRow;
import pl.databucket.e2e.model.Message;
import pl.databucket.e2e.model.Reservation;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.client.Rules.rule;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.AGENT;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

@ApiSuite
@Area(Area.DATA)
@DisplayName("Reservation")
class ReservationTest {

    private static final String BUCKET = "accounts";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Account(String login, String plan) {
    }

    private BucketApi<Account> accounts;

    @BeforeEach
    void bucket(TestProject project) {
        project.createBucket(BUCKET);
        accounts = project.as(ROBOT).bucket(BUCKET, Account.class);
    }

    private static void seed(TestProject project, int count) {
        seed(project, count, i -> new Account("user-" + i, null));
    }

    private static void seed(TestProject project, int count, IntFunction<Account> account) {
        project.quietly(ROBOT).bucket(BUCKET, Account.class).insertAll(IntStream.rangeClosed(1, count).mapToObj(account).toList());
    }

    @Test
    @DisplayName("limit 0 only counts the free rows")
    void limit_0(TestProject project) {
        seed(project, 4);

        Reservation<Account> count = accounts.reserve(Search.all().limit(0));
        Reservation<Account> again = accounts.reserve(Search.all().limit(0));

        verifyAll(v -> v
                .that("count: reserved", count.reserved(), 0)
                .that("count: available", count.available(), 4)
                .that("again: available", again.available(), 4));
    }

    @Test
    @DisplayName("says how many free rows are left, unless count=false")
    void available(TestProject project) {
        seed(project, 5);

        Reservation<Account> counted = accounts.reserve(Search.all().limit(2));
        Reservation<Account> notCounted = accounts.reserve(Search.all().limit(1).count(false));

        verifyAll(v -> v
                .that("counted: reserved", counted.reserved(), 2)
                .that("counted: available", counted.available(), 3)
                .that("not counted: reserved", notCounted.reserved(), 1)
                .check("not counted: available null", () -> assertThat(notCounted.available()).isNull()));
    }

    @Test
    void reserves_only_the_rows_matching_the_rules(TestProject project) {
        seed(project, 6, i -> new Account("user-" + i, i % 2 == 1 ? "free" : "premium"));

        Reservation<Account> reservation = accounts.reserve(Search.where(rule("$.plan", "==", "premium")).limit(10));

        verifyAll(v -> v
                .that("reserved", reservation.reserved(), 3)
                .check("plans: only premium", () -> assertThat(reservation.rows())
                        .extracting(row -> row.properties().plan()).containsOnly("premium"))
                .that("available", reservation.available(), 0));
    }

    @Test
    void when_nothing_is_free_there_is_a_message_not_an_error(TestProject project) {
        seed(project, 1);
        accounts.reserve(Search.all().limit(1));

        Reservation<Account> reservation = accounts.reserve(Search.all().limit(1));

        verifyAll(v -> v
                .that("reserved", reservation.reserved(), 0)
                .that("available", reservation.available(), 0)
                .that("message", reservation.message(), "No data matches the rules!"));
    }

    @Test
    void rows_reserved_by_one_user_are_not_given_to_another(TestProject project) {
        seed(project, 3);

        Reservation<Account> memberGot = project.as(MEMBER).bucket(BUCKET, Account.class).reserve(Search.all().limit(2));
        Reservation<Account> robotGot = accounts.reserve(Search.all().limit(5));

        verifyAll(v -> v
                .that("member: ids: size", memberGot.ids().size(), 2)
                .that("robot: ids: size", robotGot.ids().size(), 1)
                .check("robot: other rows than the member", () -> assertThat(robotGot.ids())
                        .doesNotContainAnyElementsOf(memberGot.ids()))
                .that("robot: owners", robotGot.rows().stream().map(DataRow::owner).toList(),
                        List.of(project.user(ROBOT).username())));
    }

    @Test
    void parallel_reservations_never_get_the_same_row(TestProject project) throws Exception {
        seed(project, 30);
        List<BucketApi<Account>> clients = List.of(project.quietly(ROBOT).bucket(BUCKET, Account.class),
                project.quietly(MEMBER).bucket(BUCKET, Account.class), project.quietly(AGENT).bucket(BUCKET, Account.class));

        List<Long> ids = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(30);
        try {
            List<CompletableFuture<Reservation<Account>>> calls = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                BucketApi<Account> client = clients.get(i % clients.size());
                calls.add(CompletableFuture.supplyAsync(() -> client.reserve(Search.all().limit(1).sort("random")), pool));
            }
            for (CompletableFuture<Reservation<Account>> call : calls) ids.addAll(call.get().ids());
        } finally {
            pool.shutdown();
        }

        Long available = accounts.reserve(Search.all().limit(0)).available();
        // every row is taken once at most; a reservation that met only taken rows got none
        verifyAll(v -> v
                .that("distinct ids", new HashSet<>(ids).size(), ids.size())
                .check("ids: not empty", () -> assertThat(ids).isNotEmpty())
                .that("available", available, 30 - ids.size()));
    }

    @Test
    void released_rows_can_be_reserved_again(TestProject project) {
        seed(project, 1);
        long id = accounts.reserve(Search.all().limit(1)).ids().get(0);

        accounts.release(id);
        Reservation<Account> again = project.as(MEMBER).bucket(BUCKET, Account.class).reserve(Search.all().limit(1));

        verifyAll(v -> v
                .that("ids", again.ids(), List.of(id))
                .that("owners", again.rows().stream().map(DataRow::owner).toList(), List.of(project.user(MEMBER).username())));
    }

    @Test
    @DisplayName("an ADMIN reserves rows for another user; others can not")
    void for_another_user(TestProject project) {
        seed(project, 2);
        String member = project.user(MEMBER).username();

        Reservation<Account> forMember = project.as(ADMIN).bucket(BUCKET, Account.class).reserve(Search.all().limit(1).forOwner(member));
        Reservation<Account> robotTries = accounts.reserve(Search.all().limit(1).forOwner(member));

        verifyAll(v -> v
                .that("for the member: owners", forMember.rows().stream().map(DataRow::owner).toList(), List.of(member))
                .that("robot tries: owners", robotTries.rows().stream().map(DataRow::owner).toList(),
                        List.of(project.user(ROBOT).username())));
    }

    @Test
    void a_negative_limit_is_refused(TestProject project) {
        int status = accounts.tryReserve(Search.all().limit(-1)).status();
        verify("status", status, 406);
    }

    @Test
    void reserving_by_rules_changes_only_the_free_rows(TestProject project) {
        seed(project, 3);
        project.as(MEMBER).bucket(BUCKET, Account.class).reserve(Search.all().limit(1));

        Message message = accounts.updateWhere(Change.reserve(true).where(rule("id", ">", 0)));

        verify("message", message.message(), "Modified 2 data row(s)");
        List<String> owners = accounts.find(Search.all().limit(10)).rows().stream().map(DataRow::owner).toList();
        verify("owners", owners, List.of(project.user(MEMBER).username(), project.user(ROBOT).username(),
                project.user(ROBOT).username()));
    }

    @Test
    void in_a_bucket_with_protected_data_each_user_sees_only_the_rows_they_reserved(TestProject project) {
        project.createBucket("vault", true, true);
        BucketApi<Account> asAdmin = project.quietly(ADMIN).bucket("vault", Account.class);
        asAdmin.insertAll(new Account("a", null), new Account("b", null), new Account("c", null));
        BucketApi<Account> asMember = project.as(MEMBER).bucket("vault", Account.class);

        Reservation<Account> reserved = asMember.reserve(Search.all().limit(1));

        // free rows are counted for everyone, as anyone may reserve them
        verify("available", reserved.available(), 2);
        List<Account> memberSees = asMember.properties(Search.all());
        verify("member sees", memberSees, List.of(reserved.rows().get(0).properties()));
        List<Account> robotSees = project.as(ROBOT).bucket("vault", Account.class).properties(Search.all());
        verify("robot sees: size", robotSees.size(), 0);
        long all = asAdmin.count(Search.all());
        verify("admin counts", all, 3);
    }
}
