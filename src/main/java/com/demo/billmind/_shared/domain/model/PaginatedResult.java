package com.demo.billmind._shared.domain.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PaginatedResult<T> {
    private final List<T> items;
    private final long totalItems;
    private final int totalPages;
    private final int currentPage; // JPA: 0-based
    private final int itemsPerPage;
    private String firstPageLink;
    private String lastPageLink;
    private String nextPageLink;
    private String previousPageLink;

    private PaginatedResult(List<T> items, long totalItems, int totalPages, int currentPage, int itemsPerPage) {
        this.items = items;
        this.totalItems = totalItems;
        this.totalPages = totalPages;
        this.currentPage = currentPage; // 0-based
        this.itemsPerPage = itemsPerPage;
    }

    public static <T> PaginatedResult<T> of(List<T> items, long totalItems, int totalPages, int currentPage, int itemsPerPage) {
        return new PaginatedResult<>(items, totalItems, totalPages, currentPage, itemsPerPage);
    }

    public void generateLinks(String baseUrl, Map<String, String> filters) {
        String filterParams = buildFilterParams(filters);

        int currentPageExposed = currentPage + 1;

        this.firstPageLink = buildLink(baseUrl, 1, filterParams);
        this.lastPageLink = buildLink(baseUrl, totalPages, filterParams);
        this.nextPageLink = hasNextPage() ? buildLink(baseUrl, currentPageExposed + 1, filterParams) : null;
        this.previousPageLink = hasPreviousPage() ? buildLink(baseUrl, currentPageExposed - 1, filterParams) : null;
    }


    private String buildLink(String baseUrl, int page, String filterParams) {
        return baseUrl + "?page=" + page + "&perPage=" + itemsPerPage + filterParams;
    }

    private String buildFilterParams(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) return "";
        return filters.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> "&" + entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining());
    }

    public boolean hasNextPage() {
        return currentPage + 1 < totalPages; // porque currentPage es 0-based
    }

    public boolean hasPreviousPage() {
        return currentPage > 0;
    }

    // Métodos públicos
    public List<T> getItems() {
        return items;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getCurrentPage() {
        return currentPage + 1;
    } // 1-based para el exterior

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public String getFirstPageLink() {
        return firstPageLink;
    }

    public String getLastPageLink() {
        return lastPageLink;
    }

    public String getNextPageLink() {
        return nextPageLink;
    }

    public String getPreviousPageLink() {
        return previousPageLink;
    }
}
