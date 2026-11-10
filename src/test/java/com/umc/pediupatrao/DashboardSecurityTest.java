package com.umc.pediupatrao;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.DashboardController;
import com.umc.pediupatrao.dto.dashboard.FiltrosResponse;
import com.umc.pediupatrao.dto.dashboard.OpcaoFiltro;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garante que o dashboard e sua API respeitam a matriz de perfis:
 * ADMIN e GERENTE acessam; ATENDENTE recebe 403; anônimo vai para o login.
 */
@WebMvcTest(controllers = DashboardController.class)
@Import(SecurityConfig.class)
class DashboardSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private UsuarioDetailsService usuarioDetailsService;

    @BeforeEach
    void setup() {
        when(dashboardService.filtros()).thenReturn(FiltrosResponse.builder()
                .periodoPadraoDias(30)
                .status(List.of(new OpcaoFiltro("FINALIZADO", "Finalizado")))
                .agrupamentos(List.of(new OpcaoFiltro("DIA", "Diário")))
                .periodosRapidos(List.of(new OpcaoFiltro("30", "Últimos 30 dias")))
                .build());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteAcessaApiDoDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/filtros"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAcessaApiDoDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/filtros"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    void atendenteNaoAcessaApiDoDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/filtros"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonimoEhRedirecionadoParaLogin() throws Exception {
        mockMvc.perform(get("/api/dashboard/filtros"))
                .andExpect(status().is3xxRedirection());
    }
}
