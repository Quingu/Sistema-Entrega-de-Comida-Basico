package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

import lombok.Builder;

/**
 * Distribuição dos pedidos por situação no período consultado.
 */
@Builder
public record SituacaoPedidosResponse(
        long total,
        long emAndamento,
        long finalizados,
        long cancelados,
        List<StatusResumo> status
) {
}
