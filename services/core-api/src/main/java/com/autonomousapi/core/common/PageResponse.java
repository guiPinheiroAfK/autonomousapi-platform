package com.autonomousapi.core.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** Envelope de paginação genérico para endpoints de listagem grandes o bastante para não
 *  devolver o resultado inteiro de uma vez (ex. /v1/vehicles, /v1/expenses). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
