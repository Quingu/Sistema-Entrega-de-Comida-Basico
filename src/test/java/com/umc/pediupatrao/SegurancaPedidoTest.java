package com.umc.pediupatrao;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.umc.pediupatrao.dto.CancelamentoDTO;
import com.umc.pediupatrao.dto.DescontoDTO;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;

import static org.junit.jupiter.api.Assertions.*;

class SegurancaPedidoTest {

    Map<String, Pedido> pedidos = new HashMap<>();
    PedidoService service;

    @SuppressWarnings("unchecked")
    static <T> T fake(Class<T> iface, InvocationHandler h) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, h);
    }

    @BeforeEach
    void setup() {
        pedidos.clear();
        PedidoRepository pedidoRepo = fake(PedidoRepository.class, (proxy, m, a) -> {
            switch (m.getName()) {
                case "save": { Pedido p = (Pedido) a[0]; pedidos.put(p.getId(), p); return p; }
                case "findById": return Optional.ofNullable(pedidos.get((String) a[0]));
                default: return null;
            }
        });
        ProdutoRepository produtoRepo = fake(ProdutoRepository.class, (p, m, a) -> null);
        ClienteRepository clienteRepo = fake(ClienteRepository.class, (p, m, a) -> null);
        AuditoriaService aud = new AuditoriaService(null) {
            @Override
            public com.umc.pediupatrao.entity.Auditoria registrar(String o, String e, String id, String c, Object ant, Object novo, String j) {
                return null;
            }
        };
        service = new PedidoService(pedidoRepo, produtoRepo, clienteRepo, aud);
    }

    private void como(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(role.toLowerCase(), "x",
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void pronto() {
        Pedido p = new Pedido(); p.setId("1"); p.setStatus(StatusPedido.PRONTO);
        p.setItens(List.of()); service.recalcular(p); pedidos.put("1", p);
    }

    @Test void atendenteNaoAplicaDescontoViaApi() {
        como("ATENDENTE"); pronto();
        DescontoDTO d = new DescontoDTO(); d.setPercentual(new BigDecimal("10.00"));
        assertThrows(AccessDeniedException.class, () -> service.aplicarDesconto("1", d));
    }

    @Test void adminNaoAplicaDescontoViaApi() {
        como("ADMIN"); pronto();
        DescontoDTO d = new DescontoDTO(); d.setPercentual(new BigDecimal("10.00"));
        assertThrows(AccessDeniedException.class, () -> service.aplicarDesconto("1", d));
    }

    @Test void atendenteNaoCancelaViaApi() {
        como("ATENDENTE"); pronto();
        CancelamentoDTO c = new CancelamentoDTO(); c.setJustificativa("Motivo qualquer valido");
        assertThrows(AccessDeniedException.class, () -> service.cancelar("1", c));
    }

    @Test void adminNaoCancelaViaApi() {
        como("ADMIN"); pronto();
        CancelamentoDTO c = new CancelamentoDTO(); c.setJustificativa("Motivo qualquer valido");
        assertThrows(AccessDeniedException.class, () -> service.cancelar("1", c));
    }

    @Test void gerenteConsegueDescontoECancelamento() {
        como("GERENTE"); pronto();
        DescontoDTO d = new DescontoDTO(); d.setPercentual(new BigDecimal("10.00"));
        assertDoesNotThrow(() -> service.aplicarDesconto("1", d));
        pronto();
        CancelamentoDTO c = new CancelamentoDTO(); c.setJustificativa("Justificativa valida do gerente");
        assertDoesNotThrow(() -> service.cancelar("1", c));
    }

    @Test void atendenteNaoTransitaStatus() {
        como("ATENDENTE"); pronto();
        com.umc.pediupatrao.dto.StatusDTO s = new com.umc.pediupatrao.dto.StatusDTO();
        s.setStatus("FINALIZADO");
        assertThrows(AccessDeniedException.class, () -> service.transitar("1", s));
    }

    @Test void adminNaoTransitaStatus() {
        como("ADMIN"); pronto();
        com.umc.pediupatrao.dto.StatusDTO s = new com.umc.pediupatrao.dto.StatusDTO();
        s.setStatus("FINALIZADO");
        assertThrows(AccessDeniedException.class, () -> service.transitar("1", s));
    }

    @Test void gerenteTransitaStatusValido() {
        como("GERENTE");
        Pedido p = new Pedido(); p.setId("1"); p.setStatus(StatusPedido.RECEBIDO);
        p.setItens(List.of()); service.recalcular(p); pedidos.put("1", p);
        com.umc.pediupatrao.dto.StatusDTO s = new com.umc.pediupatrao.dto.StatusDTO();
        s.setStatus("EM_PREPARACAO");
        assertDoesNotThrow(() -> service.transitar("1", s));
    }
}

