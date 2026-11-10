package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

import lombok.Builder;

/**
 * Indicadores gerais de vendas do período consultado.
 */
@Builder
public record ResumoVendasResponse(
        String periodoInicio,
        String periodoFim,
        BigDecimal faturamentoTotal,
        BigDecimal faturamentoFinalizado,
        BigDecimal descontoTotal,
        BigDecimal ticketMedio,
        long totalPedidos,
        long pedidosNaoCancelados,
        long pedidosFinalizados,
        long pedidosEmAndamento,
        long pedidosCancelados,
        BigDecimal taxaCancelamento,
        long itensVendidos,
        BigDecimal faturamentoPeriodoAnterior,
        BigDecimal variacaoFaturamentoPercentual,
        String atualizadoEm
) {
}
