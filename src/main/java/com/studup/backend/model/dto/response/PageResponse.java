package com.studup.backend.model.dto.response;

import java.util.List;

/**
 * Réponse paginée générique.
 * nextCursor = id du dernier élément retourné, à passer en paramètre pour la page suivante.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {}
