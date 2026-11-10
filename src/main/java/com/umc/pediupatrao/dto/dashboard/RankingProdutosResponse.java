package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;

/**
 * Ranking de produtos mais vendidos por quantidade.
 */
@Builder
public record RankingProdutosResponse(
        long totalItensVendidos,
        BigDecimal faturamentoTotal,
        List<ProdutoRanking> produtos
) {
}
