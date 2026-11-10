package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

import lombok.Builder;

/**
 * Linha resumida da lista de pedidos recentes do dashboard.
 */
@Builder
public record PedidoRecenteResponse(
        String id,
        String dataEntrada,
        String clienteId,
        String clienteNome,
        String status,
        String statusDescricao,
        int quantidadeItens,
        BigDecimal total,
        String responsavel
) {
}
