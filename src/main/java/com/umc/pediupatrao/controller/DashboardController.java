package com.umc.pediupatrao.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.umc.pediupatrao.dto.dashboard.DashboardFiltro;
import com.umc.pediupatrao.dto.dashboard.FiltrosResponse;
import com.umc.pediupatrao.dto.dashboard.PedidoRecenteResponse;
import com.umc.pediupatrao.dto.dashboard.RankingProdutosResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasResponse;
import com.umc.pediupatrao.dto.dashboard.SerieVendasResponse;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidosResponse;
import com.umc.pediupatrao.service.DashboardService;

import lombok.RequiredArgsConstructor;

/**
 * API do dashboard administrativo.
 *
 * <p>Todos os endpoints são somente leitura e exigem perfil ADMIN ou GERENTE.
 * Os filtros são opcionais; quando omitidos, o período padrão é dos últimos
 * 30 dias (incluindo hoje).</p>
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
public class DashboardController {

    private final DashboardService dashboardService;

    /** RF01 — indicadores gerais de vendas. */
    @GetMapping("/resumo")
    public ResumoVendasResponse resumo(@ModelAttribute DashboardFiltro filtro) {
        return dashboardService.resumo(filtro);
    }

    /** RF02 — série temporal de faturamento, pedidos e itens. */
    @GetMapping("/vendas")
    public SerieVendasResponse vendas(@ModelAttribute DashboardFiltro filtro) {
        return dashboardService.vendas(filtro);
    }

    /** RF03 — distribuição dos pedidos por situação. */
    @GetMapping("/status")
    public SituacaoPedidosResponse status(@ModelAttribute DashboardFiltro filtro) {
        return dashboardService.situacao(filtro);
    }

    /** RF04 — ranking de produtos mais vendidos. */
    @GetMapping("/produtos-mais-vendidos")
    public RankingProdutosResponse produtosMaisVendidos(@ModelAttribute DashboardFiltro filtro) {
        return dashboardService.produtosMaisVendidos(filtro);
    }

    /** RF05 — metadados para montar os filtros da interface. */
    @GetMapping("/filtros")
    public FiltrosResponse filtros() {
        return dashboardService.filtros();
    }

    /** RF07 — pedidos mais recentes para acompanhamento operacional. */
    @GetMapping("/pedidos-recentes")
    public List<PedidoRecenteResponse> pedidosRecentes(@ModelAttribute DashboardFiltro filtro) {
        return dashboardService.pedidosRecentes(filtro);
    }
}
