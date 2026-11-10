package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

import lombok.Builder;

/**
 * Um ponto (dia, semana ou mês) da série temporal de vendas.
 */
@Builder
public record PontoSerieVendas(
        String rotulo,
        String inicio,
        String fim,
        BigDecimal faturamento,
        long pedidos,
        long itens
) {
}
