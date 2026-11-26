package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Settings;
import pl.databucket.e2e.model.Bucket;
import pl.databucket.e2e.model.DataClass;
import pl.databucket.e2e.model.DataRow;
import pl.databucket.e2e.model.NewRow;
import pl.databucket.e2e.model.Svg;
import pl.databucket.e2e.model.Tag;
import pl.databucket.e2e.support.Area;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.UserKey.ADMIN;
import static pl.databucket.e2e.support.UserKey.ROBOT;
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

@ApiSuite
@Area(Area.SETTINGS)
@DisplayName("Tags and icons")
class SettingsTagsIconsTest {

    private static final String FLAG = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 16\">"
            + "<rect width=\"24\" height=\"8\" fill=\"#ffffff\"/><rect y=\"8\" width=\"24\" height=\"8\" fill=\"#dc143c\"/></svg>";

    record Item(String name) {
    }

    private Settings settings;

    @BeforeEach
    void admin(TestProject project) {
        settings = project.as(ADMIN).settings();
    }

    @Nested
    @DisplayName("Tags")
    class Tags {

        @Test
        void a_tag_belongs_to_buckets_and_classes(TestProject project) {
            Bucket bucket = settings.buckets().create(SettingsBucketsTest.bucket("orders"));
            DataClass dataClass = settings.classes().create(new DataClass().name("order"));

            Tag tag = settings.tags().create(new Tag().name("new").description("just created")
                    .bucketsIds(Set.of(bucket.id)).classesIds(Set.of(dataClass.id)));

            verifyAll(v -> v
                    .that("bucketsIds", List.copyOf(tag.bucketsIds), List.of(bucket.id))
                    .that("classesIds", List.copyOf(tag.classesIds), List.of(dataClass.id)));
            verify("descriptions", settings.tags().list().stream().map(t -> t.description).toList(), List.of("just created"));
        }

        @Test
        void a_tag_name_is_unique_also_when_renaming(TestProject project) {
            settings.tags().create(new Tag().name("new"));
            Tag paid = settings.tags().create(new Tag().name("paid"));

            verify("status", settings.tags().tryCreate(new Tag().name("new")).status(), 406);
            verify("status", settings.tags().tryUpdate(paid.name("new")).status(), 406);
        }

        @Test
        void a_tag_in_use_can_be_renamed_and_the_rows_keep_it(TestProject project) {
            Tag tag = settings.tags().create(new Tag().name("new"));
            project.createBucket("orders");
            BucketApi<Item> orders = project.quietly(ROBOT).bucket("orders", Item.class);
            long id = orders.insert(NewRow.of(new Item("x")).tagged(tag.id)).id();

            settings.tags().update(tag.name("fresh"));

            check("the row keeps the tag", () -> assertThat(orders.get(id).map(DataRow::tagId)).contains(tag.id));
            verify("tag names", settings.tags().list().stream().map(t -> t.name).toList(), List.of("fresh"));
        }

        @Test
        void a_removed_tag_is_not_listed(TestProject project) {
            Tag tag = settings.tags().create(new Tag().name("old"));

            settings.tags().delete(tag.id);

            verify("tags", settings.tags().list().size(), 0);
        }
    }

    @Nested
    @DisplayName("Icons")
    class Icons {

        @Test
        void creates_changes_and_removes_an_icon(TestProject project) {
            Svg created = settings.icons().create(new Svg().name("flag-pl").structure(FLAG));

            verify("structure", created.structure, FLAG);
            Svg changed = settings.icons().update(created.name("flag-poland"));
            verify("name", changed.name, "flag-poland");

            settings.icons().delete(created.id);
            verify("icons", settings.icons().list().size(), 0);
        }

        @Test
        void an_icon_with_a_script_is_refused(TestProject project) {
            String withScript = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>";
            String withHandler = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"1\" height=\"1\" onload=\"alert(1)\"/></svg>";

            verify("status", settings.icons().tryCreate(new Svg().name("evil").structure(withScript)).status(), 400);
            verify("status", settings.icons().tryCreate(new Svg().name("evil2").structure(withHandler)).status(), 400);
            verify("icons", settings.icons().list().size(), 0);
        }
    }
}
