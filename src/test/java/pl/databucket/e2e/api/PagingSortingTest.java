package pl.databucket.e2e.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.databucket.e2e.client.BucketApi;
import pl.databucket.e2e.client.Search;
import pl.databucket.e2e.model.DataPage;
import pl.databucket.e2e.support.ApiSuite;
import pl.databucket.e2e.support.TestProject;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.databucket.e2e.support.UserKey.ROBOT;

@ApiSuite
@DisplayName("Paging and sorting")
class PagingSortingTest {

    private static final String BUCKET = "items";

    record Item(String name, int rank) {
    }

    private static final List<Item> ITEMS = List.of(new Item("pear", 9), new Item("Apple", 100), new Item("cherry", 10),
            new Item("banana", 1), new Item("date", 55));

    private BucketApi<Item> items;

    @BeforeEach
    void items(TestProject project) {
        project.createBucket(BUCKET);
        project.quietly(ROBOT).bucket(BUCKET, Item.class).insertAll(ITEMS);
        items = project.as(ROBOT).bucket(BUCKET, Item.class);
    }

    private static List<String> names(DataPage<Item> page) {
        return page.properties().stream().map(Item::name).toList();
    }

    @Test
    @DisplayName("pages through the rows by id, with the total and the number of pages")
    void pages(TestProject project) {
        DataPage<Item> first = items.find(Search.all().limit(2).page(1));
        DataPage<Item> second = items.find(Search.all().limit(2).page(2));
        DataPage<Item> last = items.find(Search.all().limit(2).page(3));

        assertThat(names(first)).containsExactly("pear", "Apple");
        assertThat(names(second)).containsExactly("cherry", "banana");
        assertThat(names(last)).containsExactly("date");
        assertThat(first.total()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.page()).isEqualTo(1);
        assertThat(first.limit()).isEqualTo(2);
    }

    @Test
    @DisplayName("without a limit returns one row, and a page after the last is empty")
    void defaults(TestProject project) {
        DataPage<Item> one = items.find(Search.all());
        DataPage<Item> beyond = items.find(Search.all().limit(2).page(9));

        assertThat(one.rows()).hasSize(1);
        assertThat(one.total()).isEqualTo(5);
        assertThat(beyond.rows()).isEmpty();
    }

    @Test
    void sorts_by_id_descending(TestProject project) {
        assertThat(names(items.find(Search.all().limit(10).sort("desc(id)"))))
                .containsExactly("date", "banana", "cherry", "Apple", "pear");
    }

    @Test
    @DisplayName("sorts numbers as numbers, not as texts")
    void numbers(TestProject project) {
        DataPage<Item> page = items.find(Search.all().limit(10).sort("desc($.rank)"));

        assertThat(page.properties()).extracting(Item::rank).containsExactly(100, 55, 10, 9, 1);
    }

    @Test
    void sorts_texts_by_a_property(TestProject project) {
        List<String> sorted = names(items.find(Search.all().limit(10).sort("asc($.name)")));

        // whatever the collation does with upper case, the lower-case names are in order
        assertThat(sorted.stream().filter(name -> !name.equals("Apple")).toList()).containsExactly("banana", "cherry", "date", "pear");
        assertThat(sorted).hasSize(5);
    }

    @Test
    void random_order_returns_every_row_once(TestProject project) {
        assertThat(names(items.find(Search.all().limit(10).sort("random"))))
                .containsExactlyInAnyOrderElementsOf(ITEMS.stream().map(Item::name).toList());
    }

    @Test
    @DisplayName("count=false leaves out the total")
    void without_count(TestProject project) {
        DataPage<Item> page = items.find(Search.all().limit(2).count(false));

        assertThat(names(page)).containsExactly("pear", "Apple");
        assertThat(page.total()).isNull();
        assertThat(page.totalPages()).isNull();
    }

    @Test
    void an_unknown_sort_field_is_refused(TestProject project) {
        assertThat(items.tryFind(Search.all().sort("desc(password)")).status()).isEqualTo(400);
    }
}
