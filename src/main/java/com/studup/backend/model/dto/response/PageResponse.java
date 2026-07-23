package com.studup.backend.model.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Réponse paginée générique — format unique de toute l'API.
 *
 * Sérialiser directement un Page Spring exposerait sa structure interne
 * (pageable, sort, first/last...) et obligerait le client à gérer deux formats
 * de pagination selon l'endpoint appelé. On expose donc uniquement ce dont
 * l'application a besoin, `hasNext` en particulier : Spring ne le sérialise
 * pas — hasNext() n'est pas un getter au sens Jackson — et le client devrait
 * sinon le déduire de `last`.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.hasNext()
        );
    }
}
