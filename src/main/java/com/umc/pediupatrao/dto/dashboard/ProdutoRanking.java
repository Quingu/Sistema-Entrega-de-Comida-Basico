package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

import lombok.Builder;

/**
 * Posição de um produto no ranking de mais vendidos.
 */
@Builder
public record ProdutoRanking(
        int posicao,
        String produtoId,
        String nome,
        String categoria,
        long quantidade,
        BigDecimal faturamento,
        BigDecimal participacaoPercentual
) {
}
