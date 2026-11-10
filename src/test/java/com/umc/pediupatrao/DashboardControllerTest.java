package com.umc.pediupatrao;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.umc.pediupatrao.controller.DashboardController;
import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.DashboardFiltro;
import com.umc.pediupatrao.dto.dashboard.FiltrosResponse;
import com.umc.pediupatrao.dto.dashboard.OpcaoFiltro;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasResponse;
import com.umc.pediupatrao.infra.GlobalExceptionHandler;
import com.umc.pediupatrao.service.DashboardService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes do contrato HTTP dos endpoints do dashboard (sem subir o MongoDB).
 */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboardService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void resumoDevolveJsonComIndicadores() throws Exception {
        when(dashboardService.resumo(any())).thenReturn(ResumoVendasResponse.builder()
                .periodoInicio("2026-11-01")
                .periodoFim("2026-11-10")
                .faturamentoTotal(new BigDecimal("130.00"))
                .ticketMedio(new BigDecimal("65.00"))
                .totalPedidos(3)
                .pedidosNaoCancelados(2)
                .build());

        mockMvc.perform(get("/api/dashboard/resumo")
                        .param("dataInicio", "2026-11-01")
                        .param("dataFim", "2026-11-10")
                        .param("status", "FINALIZADO,CANCELADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faturamentoTotal").value(130.00))
                .andExpect(jsonPath("$.ticketMedio").value(65.00))
                .andExpect(jsonPath("$.totalPedidos").value(3));

        ArgumentCaptor<DashboardFiltro> captor = ArgumentCaptor.forClass(DashboardFiltro.class);
        verify(dashboardService).resumo(captor.capture());
        assertEquals("FINALIZADO,CANCELADO", captor.getValue().getStatus());
        assertEquals(2, captor.getValue().statusSelecionados().size());
    }

    @Test
    void filtrosDevolveOpcoesParaInterface() throws Exception {
        when(dashboardService.filtros()).thenReturn(FiltrosResponse.builder()
                .dataMinima("2026-10-01")
                .dataMaxima("2026-11-10")
                .periodoPadraoDias(30)
                .status(List.of(new OpcaoFiltro("FINALIZADO", "Finalizado")))
                .agrupamentos(List.of(new OpcaoFiltro("DIA", "Diário")))
                .periodosRapidos(List.of(new OpcaoFiltro("30", "Últimos 30 dias")))
                .build());

        mockMvc.perform(get("/api/dashboard/filtros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodoPadraoDias").value(30))
                .andExpect(jsonPath("$.status[0].valor").value("FINALIZADO"))
                .andExpect(jsonPath("$.agrupamentos[0].rotulo").value("Diário"));
    }

    @Test
    void agrupamentoEhConvertidoParaEnum() throws Exception {
        when(dashboardService.vendas(any())).thenReturn(com.umc.pediupatrao.dto.dashboard.SerieVendasResponse.builder()
                .agrupamento("SEMANA")
                .pontos(List.of())
                .build());

        mockMvc.perform(get("/api/dashboard/vendas").param("agrupamento", "SEMANA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agrupamento").value("SEMANA"));

        ArgumentCaptor<DashboardFiltro> captor = ArgumentCaptor.forClass(DashboardFiltro.class);
        verify(dashboardService).vendas(captor.capture());
        assertEquals(Agrupamento.SEMANA, captor.getValue().getAgrupamento());
    }

    @Test
    void dataInvalidaRetornaErro400ComMensagemClara() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo").param("dataInicio", "2026-13-99"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void erroDeRegraDeNegocioRetorna400() throws Exception {
        when(dashboardService.resumo(any()))
                .thenThrow(new IllegalArgumentException("A data inicial não pode ser posterior à data final."));

        mockMvc.perform(get("/api/dashboard/resumo")
                        .param("dataInicio", "2026-11-10")
                        .param("dataFim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Requisição inválida."))
                .andExpect(jsonPath("$.detalhe").value("A data inicial não pode ser posterior à data final."));
    }
}
