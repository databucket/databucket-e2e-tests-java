package pl.databucket.e2e.model;

import java.util.List;

/** A dataset that can be imported into an empty project (Management → Projects → Import). */
public record DatasetInfo(String id, String source, String name, String description, int buckets, int views, int rows,
                          int icons, int changes, List<String> users) {
}
