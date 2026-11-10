package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

import lombok.Builder;

/**
 * Metadados para montar os filtros do dashboard sem valores fixos no front-end.
 */
@Builder
public record FiltrosResponse(
        String dataMinima,
        String dataMaxima,
        int periodoPadraoDias,
        List<OpcaoFiltro> status,
        List<OpcaoFiltro> agrupamentos,
        List<OpcaoFiltro> periodosRapidos
) {
}
