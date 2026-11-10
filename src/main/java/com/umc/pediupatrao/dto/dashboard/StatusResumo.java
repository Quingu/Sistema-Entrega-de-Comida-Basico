package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

import lombok.Builder;

/**
 * Quantidade, participação e valor dos pedidos em um status.
 */
@Builder
public record StatusResumo(
        String status,
        String descricao,
        long quantidade,
        BigDecimal percentual,
        BigDecimal valor
) {
}
