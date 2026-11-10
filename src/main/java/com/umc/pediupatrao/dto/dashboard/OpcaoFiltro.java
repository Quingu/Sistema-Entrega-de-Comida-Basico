package com.umc.pediupatrao.dto.dashboard;

import lombok.Builder;

/**
 * Opção genérica usada para montar os selects de filtro na interface.
 */
@Builder
public record OpcaoFiltro(
        String valor,
        String rotulo
) {
}
