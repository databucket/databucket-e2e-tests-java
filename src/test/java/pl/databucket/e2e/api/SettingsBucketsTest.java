package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Change;
import pl.databucket.e2e.client.Resource;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.model.Bucket;
import pl.databucket.e2e.model.DataClass;
import pl.databucket.e2e.model.Icon;
import pl.databucket.e2e.model.IndexedProperty;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.Role;
import pl.databucket.e2e.support.TestProject;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

@ApiSuite
@Area(Area.SETTINGS)
@DisplayName("Buckets")
class SettingsBucketsTest {

    record No(int no) {
    }

    record Status(String status) {
    }

    private Resource<Bucket> buckets;

    @BeforeEach
    void admin(TestProject project) {
        buckets = project.as(ADMIN).settings().buckets();
    }

    static Bucket bucket(String name) {
        return new Bucket().name(name).icon(new Icon().name("Storage")).roleId((short) Role.AGENT.id);
    }

    @Test
    @DisplayName("creates a bucket with its options and lists it")
    void creates_and_lists(TestProject project) {
        DataClass dataClass = project.as(ADMIN).settings().classes().create(new DataClass().name("customer"));

        Bucket created = buckets.create(bucket("customers").description("Customers of the shop").classId(dataClass.id)
                .history(true).protectedData(true).icon(new Icon().name("person").color("#1565C0")));

        verifyAll(v -> v
                .check("id: not null", () -> assertThat(created.id).isNotNull())
                .that("createdBy", created.createdBy, project.user(ADMIN).username())
                .that("icon.name", created.icon.name, "person")
                .that("icon.color", created.icon.color, "#1565C0"));
        List<Bucket> listed = buckets.list();
        verifyAll(v -> v
                .that("buckets: size", listed.size(), 1)
                .that("name", listed.get(0).name, "customers")
                .that("description", listed.get(0).description, "Customers of the shop")
                .that("classId", listed.get(0).classId, dataClass.id)
                .that("history", listed.get(0).history, true)
                .that("protectedData", listed.get(0).protectedData, true)
                .that("roleId", listed.get(0).roleId, (short) Role.AGENT.id));
    }

    @Test
    void a_bucket_name_is_unique_in_the_project(TestProject project) {
        buckets.create(bucket("orders"));

        int status = buckets.tryCreate(bucket("orders")).status();
        verify("status", status, 406);
    }

    @Test
    void a_bucket_name_has_at_most_30_characters(TestProject project) {
        int status = buckets.tryCreate(bucket("x".repeat(31))).status();
        verify("status", status, 400);
    }

    @Test
    void a_renamed_bucket_keeps_its_data_under_the_new_name(TestProject project) {
        Bucket created = buckets.create(bucket("orders-old"));
        project.as(ROBOT).bucket("orders-old", No.class).insert(new No(1));

        buckets.update(created.name("orders-new"));

        int oldStatus = project.as(ROBOT).bucket("orders-old", No.class).tryFind(Search.all()).status();
        verify("old name: status", oldStatus, 404);
        List<No> rows = project.as(ROBOT).bucket("orders-new", No.class).properties(Search.all());
        verify("new name: rows", rows, List.of(new No(1)));
    }

    @Test
    void a_renamed_bucket_can_not_take_the_name_of_another(TestProject project) {
        buckets.create(bucket("orders"));
        Bucket other = buckets.create(bucket("invoices"));

        int status = buckets.tryUpdate(other.name("orders")).status();
        verify("status", status, 406);
    }

    @Test
    @DisplayName("a removed bucket is gone, and its name can be used again for a new, empty bucket")
    void removed_bucket(TestProject project) {
        Bucket created = buckets.create(bucket("temp"));
        BucketApi<No> temp = project.as(ROBOT).bucket("temp", No.class);
        temp.insert(new No(1));

        buckets.delete(created.id);
        int status = temp.tryFind(Search.all()).status();
        verify("data: status", status, 404);
        List<Bucket> listed = buckets.list();
        verify("buckets: size", listed.size(), 0);

        buckets.create(bucket("temp"));
        long count = temp.count(Search.all());
        verify("count", count, 0);
    }

    @Test
    void switching_the_history_off_stops_recording_the_changes(TestProject project) {
        Bucket created = buckets.create(bucket("orders").history(true));
        BucketApi<Status> orders = project.as(ROBOT).bucket("orders", Status.class);
        long id = orders.insert(new Status("NEW")).id();

        buckets.update(created.history(false));
        orders.update(Change.set("$.status", "PAID"), id);

        int entries = orders.history(id).size();
        verify("history: size", entries, 1);
    }

    @Test
    void switching_the_history_on_starts_recording_the_changes(TestProject project) {
        Bucket created = buckets.create(bucket("orders").history(false));
        BucketApi<Status> orders = project.as(ROBOT).bucket("orders", Status.class);
        long id = orders.insert(new Status("NEW")).id();

        buckets.update(created.history(true));
        orders.update(Change.set("$.status", "PAID"), id);

        int entries = orders.history(id).size();
        verify("history: size", entries, 1);
    }

    @Test
    @DisplayName("indexed properties: a valid path and type, at most 5")
    void indexed_properties(TestProject project) {
        int injection = buckets.tryCreate(bucket("idx-1").indexedProperties(List.of(
                new IndexedProperty().path("$.a'); DROP TABLE tags; --").type("text")))).status();
        verify("a path with SQL: status", injection, 400);
        int wrongType = buckets.tryCreate(bucket("idx-2").indexedProperties(List.of(
                new IndexedProperty().path("$.a").type("date")))).status();
        verify("type date: status", wrongType, 400);
        int six = buckets.tryCreate(bucket("idx-3").indexedProperties(Stream.of("a", "b", "c", "d", "e", "f")
                .map(p -> new IndexedProperty().path("$." + p).type("text")).toList())).status();
        verify("6 properties: status", six, 400);

        Bucket fine = buckets.create(bucket("idx-4").indexedProperties(List.of(
                new IndexedProperty().path("$.login").type("text"), new IndexedProperty().path("$.age").type("number"))));

        verify("indexedProperties", fine.indexedProperties.stream().map(p -> p.path + ":" + p.type).toList(),
                List.of("$.login:text", "$.age:number"));
    }

    @Test
    void indexed_properties_can_be_changed_later_and_searches_still_work(TestProject project) {
        Bucket created = buckets.create(bucket("accounts"));
        BucketApi<Status> accounts = project.as(ROBOT).bucket("accounts", Status.class);
        accounts.insertAll(new Status("NEW"), new Status("PAID"), new Status("NEW"));

        Bucket indexed = buckets.update(created.indexedProperties(List.of(new IndexedProperty().path("$.status").type("text"))));

        verify("indexedProperties", indexed.indexedProperties.stream().map(p -> p.path).toList(), List.of("$.status"));
        long newOnes = accounts.count(Search.where(pl.databucket.e2e.client.Rules.rule("$.status", "==", "NEW")));
        verify("count NEW", newOnes, 2);
        long paid = accounts.count(Search.where(pl.databucket.e2e.client.Rules.rule("$.status", "like", "PA%")));
        verify("count like PA%", paid, 1);
    }

    @Test
    void a_bucket_hidden_from_the_overview_is_still_used_by_its_users(TestProject project) {
        buckets.create(bucket("hidden").overviewVisible(false).roleId((short) Role.MEMBER.id));

        List<Bucket> listed = buckets.list();
        verifyAll(v -> v
                .that("buckets: size", listed.size(), 1)
                .that("overviewVisible", listed.get(0).overviewVisible, false));
        List<String> names = project.as(MEMBER).settings().accessTree().bucketNames();
        check("the member's access tree has the bucket", () -> assertThat(names).contains("hidden"));
    }
}
