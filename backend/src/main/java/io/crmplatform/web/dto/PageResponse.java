package io.crmplatform.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination envelope.
 *
 * <p>Spring's {@code Page} serialises its internal {@code Pageable} shape, which is both verbose
 * and version-sensitive; pinning our own wrapper means the client contract does not change when
 * Spring Data does.
 *
 * @param content   the page of items
 * @param page      zero-based page index
 * @param size      requested page size
 * @param totalElements total rows matching the filter
 * @param totalPages number of pages at this size
 * @param first     true when this is the first page
 * @param last      true when this is the final page
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    /** Maps entities to DTOs while preserving the page metadata. */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
