package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.Settings;
import pl.databucket.e2e.model.Bucket;
import pl.databucket.e2e.model.ClassField;
import pl.databucket.e2e.model.Column;
import pl.databucket.e2e.model.ColumnProperty;
import pl.databucket.e2e.model.Columns;
import pl.databucket.e2e.model.ColumnsConfig;
import pl.databucket.e2e.model.DataClass;
import pl.databucket.e2e.model.DataEnum;
import pl.databucket.e2e.model.EnumItem;
import pl.databucket.e2e.model.Filter;
import pl.databucket.e2e.model.FilterConfig;
import pl.databucket.e2e.model.Icon;
import pl.databucket.e2e.model.Tag;
import pl.databucket.e2e.model.Task;
import pl.databucket.e2e.model.TaskConfig;
import pl.databucket.e2e.model.View;
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
import static pl.databucket.e2e.support.UserKey.MEMBER;

/** Settings that describe the data: classes, enums, columns, filters, tasks. */
@ApiSuite
@DisplayName("Settings: classes, enums, columns, filters, tasks")
class SettingsStructureTest {

    private Settings settings;

    @BeforeEach
    void admin(TestProject project) {
        settings = project.as(ADMIN).settings();
    }

    static ClassField field(String path, String title, String type) {
        return new ClassField().path(path).title(title).type(type).uuid("u-" + path.substring(2));
    }

    static ColumnProperty property(String path, String title) {
        return new ColumnProperty().path(path).title(title).type("string").uuid("u-" + path.substring(2));
    }

    static Column column(String uuid) {
        return new Column().uuid(uuid).enabled(true).sorting(true).filtering(true).editable("always");
    }

    private Columns columns(String name) {
        return settings.columns().create(new Columns().name(name).configuration(new ColumnsConfig()
                .columns(List.of(column("uuid_data_id"), column("u-login")))
                .properties(List.of(property("$.login", "Login")))));
    }

    private View view(String name, Columns columns) {
        return new View().name(name).columnsId(columns.id).roleId((short) Role.MEMBER.id);
    }

    @Nested
    @DisplayName("Classes")
    class Classes {

        @Test
        void creates_changes_and_removes_a_class(TestProject project) {
            DataClass created = settings.classes().create(new DataClass().name("user").description("A user")
                    .configuration(List.of(field("$.login", "Login", "string"), field("$.age", "Age", "numeric"))));

            assertThat(created.configuration).extracting(f -> f.path).containsExactly("$.login", "$.age");
            created.configuration.add(field("$.birthDate", "Birth date", "date"));
            DataClass changed = settings.classes().update(created.description("A user of the shop"));

            assertThat(changed.description).isEqualTo("A user of the shop");
            assertThat(changed.configuration).extracting(f -> f.type).containsExactly("string", "numeric", "date");
            assertThat(settings.classes().list()).extracting(c -> c.name).containsExactly("user");

            settings.classes().delete(created.id);
            assertThat(settings.classes().list()).isEmpty();
        }

        @Test
        void a_class_name_is_unique(TestProject project) {
            settings.classes().create(new DataClass().name("user"));

            assertThat(settings.classes().tryCreate(new DataClass().name("user")).status()).isEqualTo(406);
        }

        @Test
        void a_class_used_by_a_bucket_can_not_be_removed(TestProject project) {
            DataClass dataClass = settings.classes().create(new DataClass().name("user"));
            settings.buckets().create(SettingsBucketsTest.bucket("users").classId(dataClass.id));

            ApiResponse response = settings.classes().tryDelete(dataClass.id);

            assertThat(response.status()).isEqualTo(403);
            assertThat(response.text()).contains("users");
            assertThat(settings.classes().list()).hasSize(1);
        }

        @Test
        void a_bucket_with_a_class_that_does_not_exist_is_refused(TestProject project) {
            assertThat(settings.buckets().tryCreate(SettingsBucketsTest.bucket("users").classId(999999L)).status()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("Enums")
    class Enums {

        private final List<EnumItem> levels = List.of(new EnumItem().value("gold").text("Gold"),
                new EnumItem().value("silver").text("Silver"), new EnumItem().value("bronze").text("Bronze"));

        @Test
        void an_enum_keeps_its_items_in_order_and_can_be_changed(TestProject project) {
            DataEnum created = settings.enums().create(new DataEnum().name("levels").items(levels));

            assertThat(settings.enums().list()).singleElement()
                    .satisfies(e -> assertThat(e.items).extracting(i -> i.value).containsExactly("gold", "silver", "bronze"));

            DataEnum changed = settings.enums().update(created.items(List.of(levels.get(2), levels.get(0))));
            assertThat(changed.items).extracting(i -> i.text).containsExactly("Bronze", "Gold");
        }

        @Test
        void enum_items_can_have_icons(TestProject project) {
            DataEnum created = settings.enums().create(new DataEnum().name("flags").iconsEnabled(true).items(List.of(
                    new EnumItem().value("pl").text("Poland").icon(new Icon().name("flag").color("#dc143c")))));

            assertThat(created.iconsEnabled).isTrue();
            assertThat(created.items.get(0).icon.color).isEqualTo("#dc143c");
        }

        @Test
        void an_enum_name_is_unique(TestProject project) {
            settings.enums().create(new DataEnum().name("levels").items(levels));

            assertThat(settings.enums().tryCreate(new DataEnum().name("levels").items(levels)).status()).isEqualTo(406);
        }

        @Test
        void an_enum_used_by_a_class_can_not_be_removed(TestProject project) {
            DataEnum levelsEnum = settings.enums().create(new DataEnum().name("levels").items(levels));
            settings.classes().create(new DataClass().name("member").configuration(List.of(
                    field("$.level", "Level", "select").enumId(levelsEnum.id))));
            Columns columns = settings.columns().create(new Columns().name("cols").configuration(new ColumnsConfig()
                    .columns(List.of(column("u-level"))).properties(List.of(property("$.level", "Level").type("select").enumId(levelsEnum.id)))));

            ApiResponse response = settings.enums().tryDelete(levelsEnum.id);

            assertThat(response.status()).isEqualTo(403);
            assertThat(columns.id).isNotNull();
            assertThat(settings.enums().list()).hasSize(1);
        }

        @Test
        void an_enum_that_is_not_used_can_be_removed(TestProject project) {
            DataEnum created = settings.enums().create(new DataEnum().name("levels").items(levels));

            settings.enums().delete(created.id);

            assertThat(settings.enums().list()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Columns")
    class ColumnsSettings {

        @Test
        void creates_changes_and_removes_columns(TestProject project) {
            Columns created = columns("login columns");

            assertThat(created.configuration.columns).extracting(c -> c.uuid).containsExactly("uuid_data_id", "u-login");
            created.configuration.columns.get(1).enabled(false);
            Columns changed = settings.columns().update(created.description("only the id shown"));

            assertThat(changed.configuration.columns.get(1).enabled).isFalse();
            assertThat(changed.description).isEqualTo("only the id shown");

            settings.columns().delete(created.id);
            assertThat(settings.columns().list()).isEmpty();
        }

        @Test
        void a_member_reads_the_columns_of_the_views_through_the_session(TestProject project) {
            Columns created = columns("login columns");

            List<Columns> read = project.as(MEMBER).settings().sessionColumns(created.id);

            assertThat(read).singleElement().satisfies(c ->
                    assertThat(c.configuration.properties).extracting(p -> p.path).containsExactly("$.login"));
        }
    }

    @Nested
    @DisplayName("Filters")
    class Filters {

        private Filter newCustomers() {
            return new Filter().name("new customers").configuration(new FilterConfig()
                    .properties(List.of(property("$.status", "Status")))
                    .logic(logic("==", var("$.status"), "NEW")));
        }

        @Test
        void creates_changes_and_removes_a_filter(TestProject project) {
            Filter created = settings.filters().create(newCustomers());

            assertThat(created.configuration.logic).isEqualTo(Map.of("==", List.of(Map.of("var", "$.status"), "NEW")));
            Filter changed = settings.filters().update(created.name("paid customers")
                    .configuration(created.configuration.logic(logic("==", var("$.status"), "PAID"))));

            assertThat(changed.name).isEqualTo("paid customers");
            assertThat(changed.configuration.logic).isEqualTo(Map.of("==", List.of(Map.of("var", "$.status"), "PAID")));

            settings.filters().delete(created.id);
            assertThat(settings.filters().list()).isEmpty();
        }

        @Test
        void a_member_reads_the_filters_through_the_session(TestProject project) {
            Filter filter = settings.filters().create(newCustomers());

            assertThat(project.as(MEMBER).settings().sessionFilters(filter.id)).extracting(f -> f.name).containsExactly("new customers");
        }
    }

    @Nested
    @DisplayName("Tasks")
    class Tasks {

        @Test
        void creates_changes_and_removes_a_task(TestProject project) {
            Tag used = settings.tags().create(new Tag().name("used"));
            Bucket bucket = settings.buckets().create(SettingsBucketsTest.bucket("accounts"));
            Task created = settings.tasks().create(new Task().name("mark used").bucketsIds(Set.of(bucket.id))
                    .configuration(new TaskConfig().properties(List.of(property("$.status", "Status")))
                            .actions(Map.of("setTag", true, "tagId", used.id, "setReserved", true, "reserved", false))));

            assertThat(created.bucketsIds).containsExactly(bucket.id);
            assertThat(created.configuration.actions).containsEntry("tagId", used.id.intValue());

            Task changed = settings.tasks().update(created.description("tag used rows and release them"));
            assertThat(changed.description).isEqualTo("tag used rows and release them");

            settings.tasks().delete(created.id);
            assertThat(settings.tasks().list()).isEmpty();
        }

        @Test
        void a_member_reads_the_tasks_through_the_session(TestProject project) {
            Task task = settings.tasks().create(new Task().name("release").configuration(new TaskConfig()
                    .properties(List.of()).actions(Map.of("setReserved", true, "reserved", false))));

            assertThat(project.as(MEMBER).settings().sessionTasks(task.id)).extracting(t -> t.name).containsExactly("release");
        }
    }
}
