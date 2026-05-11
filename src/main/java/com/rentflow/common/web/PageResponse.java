package com.rentflow.common.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    @JsonProperty("pageNumber")
    public int pageNumber() {
        return page;
    }
}
