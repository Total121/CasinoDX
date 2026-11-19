package com.dravorax.casinodx.history;

import java.util.Collections;
import java.util.List;

public final class PagedHistory {

    private final List<HistoryEntry> entries;
    private final int page;
    private final int totalPages;
    private final int totalEntries;

    public PagedHistory(List<HistoryEntry> entries, int page, int totalPages, int totalEntries) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
        this.page = page;
        this.totalPages = Math.max(1, totalPages);
        this.totalEntries = totalEntries;
    }

    public List<HistoryEntry> entries() {
        return entries;
    }

    public int page() {
        return page;
    }

    public int totalPages() {
        return totalPages;
    }

    public int totalEntries() {
        return totalEntries;
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return page + 1 < totalPages;
    }

    public static PagedHistory empty() {
        return new PagedHistory(Collections.emptyList(), 0, 1, 0);
    }
}
