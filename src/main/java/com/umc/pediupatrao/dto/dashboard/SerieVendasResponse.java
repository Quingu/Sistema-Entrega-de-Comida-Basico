package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;

/**
 * Série temporal pronta para alimentar o gráfico de vendas.
 */
@Builder
public record SerieVendasResponse(
        String agrupamento,
        String periodoInicio,
        String periodoFim,
        BigDecimal faturamentoTotal,
        long totalPedidos,
        long totalItens,
        List<PontoSerieVendas> pontos
) {
}
