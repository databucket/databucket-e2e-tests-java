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
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.Role;
import pl.databucket.e2e.support.TestProject;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.MEMBER;
import static pl.databucket.e2e.support.UserKey.ROBOT;

@ApiSuite
@DisplayName("Settings: buckets")
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

        assertThat(created.id).isNotNull();
        assertThat(created.createdBy).isEqualTo(project.user(ADMIN).username());
        assertThat(created.icon.name).isEqualTo("person");
        assertThat(created.icon.color).isEqualTo("#1565C0");
        assertThat(buckets.list()).singleElement().satisfies(listed -> {
            assertThat(listed.name).isEqualTo("customers");
            assertThat(listed.description).isEqualTo("Customers of the shop");
            assertThat(listed.classId).isEqualTo(dataClass.id);
            assertThat(listed.history).isTrue();
            assertThat(listed.protectedData).isTrue();
            assertThat(listed.roleId).isEqualTo((short) Role.AGENT.id);
        });
    }

    @Test
    void a_bucket_name_is_unique_in_the_project(TestProject project) {
        buckets.create(bucket("orders"));

        assertThat(buckets.tryCreate(bucket("orders")).status()).isEqualTo(406);
    }

    @Test
    void a_bucket_name_has_at_most_30_characters(TestProject project) {
        assertThat(buckets.tryCreate(bucket("x".repeat(31))).status()).isEqualTo(400);
    }

    @Test
    void a_renamed_bucket_keeps_its_data_under_the_new_name(TestProject project) {
        Bucket created = buckets.create(bucket("orders-old"));
        project.as(ROBOT).bucket("orders-old", No.class).insert(new No(1));

        buckets.update(created.name("orders-new"));

        assertThat(project.as(ROBOT).bucket("orders-old", No.class).tryFind(Search.all()).status()).isEqualTo(404);
        assertThat(project.as(ROBOT).bucket("orders-new", No.class).properties(Search.all())).containsExactly(new No(1));
    }

    @Test
    void a_renamed_bucket_can_not_take_the_name_of_another(TestProject project) {
        buckets.create(bucket("orders"));
        Bucket other = buckets.create(bucket("invoices"));

        assertThat(buckets.tryUpdate(other.name("orders")).status()).isEqualTo(406);
    }

    @Test
    @DisplayName("a removed bucket is gone, and its name can be used again for a new, empty bucket")
    void removed_bucket(TestProject project) {
        Bucket created = buckets.create(bucket("temp"));
        BucketApi<No> temp = project.as(ROBOT).bucket("temp", No.class);
        temp.insert(new No(1));

        buckets.delete(created.id);
        assertThat(temp.tryFind(Search.all()).status()).isEqualTo(404);
        assertThat(buckets.list()).isEmpty();

        buckets.create(bucket("temp"));
        assertThat(temp.count(Search.all())).isZero();
    }

    @Test
    void switching_the_history_off_stops_recording_the_changes(TestProject project) {
        Bucket created = buckets.create(bucket("orders").history(true));
        BucketApi<Status> orders = project.as(ROBOT).bucket("orders", Status.class);
        long id = orders.insert(new Status("NEW")).id();

        buckets.update(created.history(false));
        orders.update(Change.set("$.status", "PAID"), id);

        assertThat(orders.history(id)).hasSize(1);
    }

    @Test
    void switching_the_history_on_starts_recording_the_changes(TestProject project) {
        Bucket created = buckets.create(bucket("orders").history(false));
        BucketApi<Status> orders = project.as(ROBOT).bucket("orders", Status.class);
        long id = orders.insert(new Status("NEW")).id();

        buckets.update(created.history(true));
        orders.update(Change.set("$.status", "PAID"), id);

        assertThat(orders.history(id)).hasSize(1);
    }

    @Test
    @DisplayName("indexed properties: a valid path and type, at most 5")
    void indexed_properties(TestProject project) {
        assertThat(buckets.tryCreate(bucket("idx-1").indexedProperties(List.of(
                new IndexedProperty().path("$.a'); DROP TABLE tags; --").type("text")))).status()).isEqualTo(400);
        assertThat(buckets.tryCreate(bucket("idx-2").indexedProperties(List.of(
                new IndexedProperty().path("$.a").type("date")))).status()).isEqualTo(400);
        assertThat(buckets.tryCreate(bucket("idx-3").indexedProperties(Stream.of("a", "b", "c", "d", "e", "f")
                .map(p -> new IndexedProperty().path("$." + p).type("text")).toList())).status()).isEqualTo(400);

        Bucket fine = buckets.create(bucket("idx-4").indexedProperties(List.of(
                new IndexedProperty().path("$.login").type("text"), new IndexedProperty().path("$.age").type("number"))));

        assertThat(fine.indexedProperties).extracting(p -> p.path + ":" + p.type).containsExactly("$.login:text", "$.age:number");
    }

    @Test
    void indexed_properties_can_be_changed_later_and_searches_still_work(TestProject project) {
        Bucket created = buckets.create(bucket("accounts"));
        BucketApi<Status> accounts = project.as(ROBOT).bucket("accounts", Status.class);
        accounts.insertAll(new Status("NEW"), new Status("PAID"), new Status("NEW"));

        Bucket indexed = buckets.update(created.indexedProperties(List.of(new IndexedProperty().path("$.status").type("text"))));

        assertThat(indexed.indexedProperties).extracting(p -> p.path).containsExactly("$.status");
        assertThat(accounts.count(Search.where(pl.databucket.e2e.client.Rules.rule("$.status", "==", "NEW")))).isEqualTo(2);
        assertThat(accounts.count(Search.where(pl.databucket.e2e.client.Rules.rule("$.status", "like", "PA%")))).isEqualTo(1);
    }

    @Test
    void a_bucket_hidden_from_the_overview_is_still_used_by_its_users(TestProject project) {
        buckets.create(bucket("hidden").overviewVisible(false).roleId((short) Role.MEMBER.id));

        assertThat(buckets.list()).singleElement().satisfies(b -> assertThat(b.overviewVisible).isFalse());
        assertThat(project.as(MEMBER).settings().accessTree().bucketNames()).contains("hidden");
    }
}
