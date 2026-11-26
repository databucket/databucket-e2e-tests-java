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
import pl.databucket.e2e.support.Area;
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
import static pl.databucket.e2e.support.Verify.check;
import static pl.databucket.e2e.support.Verify.verify;
import static pl.databucket.e2e.support.Verify.verifyAll;

/** Settings that describe the data: classes, enums, columns, filters, tasks. */
@ApiSuite
@Area(Area.SETTINGS)
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

            verify("paths", created.configuration.stream().map(f -> f.path).toList(), List.of("$.login", "$.age"));
            created.configuration.add(field("$.birthDate", "Birth date", "date"));
            DataClass changed = settings.classes().update(created.description("A user of the shop"));

            verifyAll(v -> v
                    .that("description", changed.description, "A user of the shop")
                    .that("types", changed.configuration.stream().map(f -> f.type).toList(), List.of("string", "numeric", "date")));
            List<DataClass> listed = settings.classes().list();
            verify("names", listed.stream().map(c -> c.name).toList(), List.of("user"));

            settings.classes().delete(created.id);
            List<DataClass> left = settings.classes().list();
            verify("classes: size", left.size(), 0);
        }

        @Test
        void a_class_name_is_unique(TestProject project) {
            settings.classes().create(new DataClass().name("user"));

            int status = settings.classes().tryCreate(new DataClass().name("user")).status();
            verify("status", status, 406);
        }

        @Test
        void a_class_used_by_a_bucket_can_not_be_removed(TestProject project) {
            DataClass dataClass = settings.classes().create(new DataClass().name("user"));
            settings.buckets().create(SettingsBucketsTest.bucket("users").classId(dataClass.id));

            ApiResponse response = settings.classes().tryDelete(dataClass.id);

            verifyAll(v -> v
                    .that("status", response.status(), 403)
                    .check("message names the bucket", () -> assertThat(response.text()).contains("users")));
            List<DataClass> listed = settings.classes().list();
            verify("classes: size", listed.size(), 1);
        }

        @Test
        void a_bucket_with_a_class_that_does_not_exist_is_refused(TestProject project) {
            int status = settings.buckets().tryCreate(SettingsBucketsTest.bucket("users").classId(999999L)).status();
            verify("status", status, 404);
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

            List<DataEnum> listed = settings.enums().list();
            verifyAll(v -> v
                    .that("enums: size", listed.size(), 1)
                    .that("values", listed.get(0).items.stream().map(i -> i.value).toList(), List.of("gold", "silver", "bronze")));

            DataEnum changed = settings.enums().update(created.items(List.of(levels.get(2), levels.get(0))));
            verify("texts", changed.items.stream().map(i -> i.text).toList(), List.of("Bronze", "Gold"));
        }

        @Test
        void enum_items_can_have_icons(TestProject project) {
            DataEnum created = settings.enums().create(new DataEnum().name("flags").iconsEnabled(true).items(List.of(
                    new EnumItem().value("pl").text("Poland").icon(new Icon().name("flag").color("#dc143c")))));

            verifyAll(v -> v
                    .that("iconsEnabled", created.iconsEnabled, true)
                    .that("items[0].icon.color", created.items.get(0).icon.color, "#dc143c"));
        }

        @Test
        void an_enum_name_is_unique(TestProject project) {
            settings.enums().create(new DataEnum().name("levels").items(levels));

            int status = settings.enums().tryCreate(new DataEnum().name("levels").items(levels)).status();
            verify("status", status, 406);
        }

        @Test
        void an_enum_used_by_a_class_can_not_be_removed(TestProject project) {
            DataEnum levelsEnum = settings.enums().create(new DataEnum().name("levels").items(levels));
            settings.classes().create(new DataClass().name("member").configuration(List.of(
                    field("$.level", "Level", "select").enumId(levelsEnum.id))));
            Columns columns = settings.columns().create(new Columns().name("cols").configuration(new ColumnsConfig()
                    .columns(List.of(column("u-level"))).properties(List.of(property("$.level", "Level").type("select").enumId(levelsEnum.id)))));

            ApiResponse response = settings.enums().tryDelete(levelsEnum.id);

            verifyAll(v -> v
                    .that("status", response.status(), 403)
                    .check("columns: id not null", () -> assertThat(columns.id).isNotNull()));
            List<DataEnum> listed = settings.enums().list();
            verify("enums: size", listed.size(), 1);
        }

        @Test
        void an_enum_that_is_not_used_can_be_removed(TestProject project) {
            DataEnum created = settings.enums().create(new DataEnum().name("levels").items(levels));

            settings.enums().delete(created.id);

            List<DataEnum> listed = settings.enums().list();
            verify("enums: size", listed.size(), 0);
        }
    }

    @Nested
    @DisplayName("Columns")
    class ColumnsSettings {

        @Test
        void creates_changes_and_removes_columns(TestProject project) {
            Columns created = columns("login columns");

            verify("uuids", created.configuration.columns.stream().map(c -> c.uuid).toList(), List.of("uuid_data_id", "u-login"));
            created.configuration.columns.get(1).enabled(false);
            Columns changed = settings.columns().update(created.description("only the id shown"));

            verifyAll(v -> v
                    .that("columns[1].enabled", changed.configuration.columns.get(1).enabled, false)
                    .that("description", changed.description, "only the id shown"));

            settings.columns().delete(created.id);
            List<Columns> left = settings.columns().list();
            verify("columns: size", left.size(), 0);
        }

        @Test
        void a_member_reads_the_columns_of_the_views_through_the_session(TestProject project) {
            Columns created = columns("login columns");

            List<Columns> read = project.as(MEMBER).settings().sessionColumns(created.id);

            verifyAll(v -> v
                    .that("columns: size", read.size(), 1)
                    .that("paths", read.get(0).configuration.properties.stream().map(p -> p.path).toList(), List.of("$.login")));
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

            verify("logic", created.configuration.logic, Map.of("==", List.of(Map.of("var", "$.status"), "NEW")));
            Filter changed = settings.filters().update(created.name("paid customers")
                    .configuration(created.configuration.logic(logic("==", var("$.status"), "PAID"))));

            verifyAll(v -> v
                    .that("name", changed.name, "paid customers")
                    .that("logic", changed.configuration.logic, Map.of("==", List.of(Map.of("var", "$.status"), "PAID"))));

            settings.filters().delete(created.id);
            List<Filter> left = settings.filters().list();
            verify("filters: size", left.size(), 0);
        }

        @Test
        void a_member_reads_the_filters_through_the_session(TestProject project) {
            Filter filter = settings.filters().create(newCustomers());

            List<Filter> read = project.as(MEMBER).settings().sessionFilters(filter.id);
            verify("names", read.stream().map(f -> f.name).toList(), List.of("new customers"));
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

            verifyAll(v -> v
                    .check("bucketsIds", () -> assertThat(created.bucketsIds).containsExactly(bucket.id))
                    .check("actions.tagId", () -> assertThat(created.configuration.actions).containsEntry("tagId", used.id.intValue())));

            Task changed = settings.tasks().update(created.description("tag used rows and release them"));
            verify("description", changed.description, "tag used rows and release them");

            settings.tasks().delete(created.id);
            List<Task> left = settings.tasks().list();
            verify("tasks: size", left.size(), 0);
        }

        @Test
        void a_member_reads_the_tasks_through_the_session(TestProject project) {
            Task task = settings.tasks().create(new Task().name("release").configuration(new TaskConfig()
                    .properties(List.of()).actions(Map.of("setReserved", true, "reserved", false))));

            List<Task> read = project.as(MEMBER).settings().sessionTasks(task.id);
            verify("names", read.stream().map(t -> t.name).toList(), List.of("release"));
        }
    }
}
