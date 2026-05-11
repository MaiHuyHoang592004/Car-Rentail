package com.rentflow.common.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Getter
public class PageableValidation {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public static Pageable of(
            @Min(0) int page,
            @Min(1) @Max(MAX_PAGE_SIZE) int size,
            Sort sort) {
        int validatedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int validatedPage = Math.max(0, page);
        return org.springframework.data.domain.PageRequest.of(validatedPage, validatedSize, sort);
    }

    public static Pageable of(int page, int size) {
        return of(page, size, Sort.unsorted());
    }
}
