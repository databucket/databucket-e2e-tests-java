package pl.databucket.e2e.model;

import java.util.List;
import java.util.Set;

/** What the web application shows to the signed-in user: projects, groups, buckets and views. */
public record AccessTree(List<AuthResponse.AuthProject> projects, List<TreeGroup> groups, List<TreeBucket> buckets,
                         List<TreeView> views) {

    public List<String> bucketNames() {
        return buckets.stream().map(TreeBucket::name).toList();
    }

    public List<String> viewNames() {
        return views.stream().map(TreeView::name).toList();
    }

    public List<String> groupNames() {
        return groups == null ? List.of() : groups.stream().map(TreeGroup::name).toList();
    }

    public record TreeGroup(Long id, String name, String shortName, Set<Long> bucketsIds) {
    }

    public record TreeBucket(Long id, String name, String iconName, String iconColor, String iconSvg, Long classId,
                             boolean history) {
    }

    public record TreeView(Long id, String name, String description, Set<Long> classesIds, Set<Long> bucketsIds,
                           long columnsId, Long filterId, List<Short> featuresIds, String sort) {
    }
}
