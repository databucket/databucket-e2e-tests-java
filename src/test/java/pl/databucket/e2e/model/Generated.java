package pl.databucket.e2e.model;

import java.util.List;
import java.util.Map;

/**
 * The answer of {@code POST /api/bucket/{bucket}/generate}: the number of inserted rows (0 for a preview), the range
 * of {@code {n}} used, and the first rows (a sample of 3, or all rows of a preview).
 */
public record Generated(int inserted, long firstN, long lastN, List<Map<String, Object>> rows) {
}
