package com.umc.pediupatrao;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.umc.pediupatrao.dto.CancelamentoDTO;
import com.umc.pediupatrao.dto.DescontoDTO;
import com.umc.pediupatrao.dto.ItemPedidoDTO;
import com.umc.pediupatrao.dto.PedidoCreateDTO;
import com.umc.pediupatrao.dto.StatusDTO;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.infra.NegocioException;
import com.umc.pediupatrao.infra.TransicaoInvalidaException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;

import static org.junit.jupiter.api.Assertions.*;

class PedidoServiceTest {

    Map<String, Pedido> pedidos = new HashMap<>();
    Map<String, Produto> produtos = new HashMap<>();
    PedidoService service;
    int auditorias;

    @SuppressWarnings("unchecked")
    static <T> T fake(Class<T> iface, InvocationHandler h) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, h);
    }

    @BeforeEach
    void setup() {
        pedidos.clear();
        produtos.clear();
        auditorias = 0;
        Produto p1 = new Produto(); p1.setId("p1"); p1.setNome("Pizza p1");
        p1.setPreco(new BigDecimal("50.00")); p1.setAtivo(true);
        Produto p2 = new Produto(); p2.setId("p2"); p2.setNome("Pizza p2");
        p2.setPreco(new BigDecimal("10.00")); p2.setAtivo(true);
        produtos.put("p1", p1); produtos.put("p2", p2);

        PedidoRepository pedidoRepo = fake(PedidoRepository.class, (proxy, m, a) -> {
            switch (m.getName()) {
                case "save": { Pedido p = (Pedido) a[0]; if (p.getId() == null) p.setId("gen-" + System.nanoTime()); pedidos.put(p.getId(), p); return p; }
                case "findById": return Optional.ofNullable(pedidos.get((String) a[0]));
                case "findAll": return List.copyOf(pedidos.values());
                default: return defaultValue(m);
            }
        });
        ProdutoRepository produtoRepo = fake(ProdutoRepository.class, (proxy, m, a) -> {
            if (m.getName().equals("findById")) return Optional.ofNullable(produtos.get((String) a[0]));
            return defaultValue(m);
        });
        ClienteRepository clienteRepo = fake(ClienteRepository.class, (proxy, m, a) -> {
            if (m.getName().equals("existsById")) return "c1".equals(a[0]);
            return defaultValue(m);
        });
        AuditoriaService aud = new AuditoriaService(null) {
            @Override
            public com.umc.pediupatrao.entity.Auditoria registrar(String o, String e, String id, String c, Object ant, Object novo, String j) {
                auditorias++;
                return null;
            }
        };
        service = new PedidoService(pedidoRepo, produtoRepo, clienteRepo, aud);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("gerente", "x",
                        List.of(new SimpleGrantedAuthority("ROLE_GERENTE"))));
    }

    static Object defaultValue(Method m) {
        Class<?> r = m.getReturnType();
        if (r.equals(void.class)) return null;
        if (r.equals(boolean.class) || r.equals(Boolean.class)) return false;
        if (r.equals(Optional.class)) return Optional.empty();
        if (r.equals(List.class)) return List.of();
        return null;
    }

    @Test
    void criaPedidoCalculandoNoServidorIgnorandoTotalDoNavegador() {
        PedidoCreateDTO dto = new PedidoCreateDTO();
        dto.setClienteId("c1");
        ItemPedidoDTO a = new ItemPedidoDTO(); a.setProdutoId("p1"); a.setQuantidade(2);
        ItemPedidoDTO b = new ItemPedidoDTO(); b.setProdutoId("p2"); b.setQuantidade(1);
        dto.setItens(List.of(a, b));

        Pedido salvo = service.criar(dto);
        assertEquals(new BigDecimal("110.00"), salvo.getSubtotal());
        assertEquals(new BigDecimal("110.00"), salvo.getTotalFinal());
        assertEquals(StatusPedido.RECEBIDO, salvo.getStatus());
        assertNotNull(salvo.getDataEntrada());
        assertEquals("gerente", salvo.getUsuarioResponsavel());
        assertTrue(auditorias >= 1);
    }

    @Test
    void bloqueiaSaltoDeEtapa() {
        Pedido p = new Pedido(); p.setId("1"); p.setStatus(StatusPedido.RECEBIDO);
        p.setItens(List.of()); service.recalcular(p); pedidos.put("1", p);
        StatusDTO dto = new StatusDTO(); dto.setStatus("PRONTO");
        assertThrows(TransicaoInvalidaException.class, () -> service.transitar("1", dto));
    }

    @Test
    void sequenciaValidaAteSaidaRegistraHoraEResponsavel() {
        Pedido p = new Pedido(); p.setId("1"); p.setStatus(StatusPedido.PRONTO);
        p.setItens(List.of()); service.recalcular(p); pedidos.put("1", p);
        StatusDTO dto = new StatusDTO(); dto.setStatus("SAIU_PARA_ENTREGA");
        Pedido salvo = service.transitar("1", dto);
        assertEquals(StatusPedido.SAIU_PARA_ENTREGA, salvo.getStatus());
        assertNotNull(salvo.getDataSaida());
        assertEquals("gerente", salvo.getResponsavelSaida());
    }

    @Test
    void descontoAcimaDe20EBloqueado() {
        Pedido p = new Pedido(); p.setId("1"); p.setStatus(StatusPedido.PRONTO);
        p.setItens(List.of()); service.recalcular(p); pedidos.put("1", p);
        DescontoDTO d = new DescontoDTO(); d.setPercentual(new BigDecimal("20.01"));
        assertThrows(NegocioException.class, () -> service.aplicarDesconto("1", d));
    }

    @Test
    void descontoAposSaidaEBloqueado() {
        Pedido p = new Pedido(); p.setId("1"); p.setStatus(StatusPedido.SAIU_PARA_ENTREGA);
        p.setItens(List.of()); service.recalcular(p); pedidos.put("1", p);
        DescontoDTO d = new DescontoDTO(); d.setPercentual(new BigDecimal("10.00"));
        assertThrows(NegocioException.class, () -> service.aplicarDesconto("1", d));
    }

    @Test
    void cancelamentoExigeJustificativaEPreservaPedido() {
        Pedido p = new Pedido(); p.setId("1"); p.setStatus(StatusPedido.PRONTO);
        p.setItens(List.of()); service.recalcular(p); pedidos.put("1", p);
        CancelamentoDTO sem = new CancelamentoDTO(); sem.setJustificativa("abc");
        assertThrows(NegocioException.class, () -> service.cancelar("1", sem));
        CancelamentoDTO ok = new CancelamentoDTO(); ok.setJustificativa("Cliente desistiu, registrado pelo gerente.");
        Pedido salvo = service.cancelar("1", ok);
        assertEquals(StatusPedido.CANCELADO, salvo.getStatus());
        assertTrue(pedidos.containsKey("1"), "pedido cancelado deve ser preservado, não excluído");
        StatusDTO t = new StatusDTO(); t.setStatus("FINALIZADO");
        assertThrows(TransicaoInvalidaException.class, () -> service.transitar("1", t));
    }

    @Test
    void bloqueiaPedidoSemItens() {
        PedidoCreateDTO dto = new PedidoCreateDTO();
        dto.setClienteId("c1");
        dto.setItens(List.of());
        assertThrows(NegocioException.class, () -> service.criar(dto));
    }

    @Test
    void bloqueiaListaDeItensNula() {
        PedidoCreateDTO dto = new PedidoCreateDTO();
        dto.setClienteId("c1");
        dto.setItens(null);

        assertThrows(NegocioException.class, () -> service.criar(dto));
    }

    @Test
    void bloqueiaQuantidadeZeroOuNegativa() {
        PedidoCreateDTO dto = new PedidoCreateDTO();
        dto.setClienteId("c1");
        ItemPedidoDTO a = new ItemPedidoDTO(); a.setProdutoId("p1"); a.setQuantidade(0);
        dto.setItens(List.of(a));
        assertThrows(NegocioException.class, () -> service.criar(dto));
    }

    @Test
    void bloqueiaProdutoInexistente() {
        PedidoCreateDTO dto = new PedidoCreateDTO();
        dto.setClienteId("c1");
        ItemPedidoDTO a = new ItemPedidoDTO(); a.setProdutoId("inexistente"); a.setQuantidade(1);
        dto.setItens(List.of(a));
        assertThrows(NegocioException.class, () -> service.criar(dto));
    }

    @Test
    void bloqueiaStatusNulo() {
        Pedido p = new Pedido(); p.setId("1"); p.setStatus(StatusPedido.RECEBIDO);
        p.setItens(List.of()); service.recalcular(p); pedidos.put("1", p);
        StatusDTO dto = new StatusDTO(); dto.setStatus(null);
        assertThrows(TransicaoInvalidaException.class, () -> service.transitar("1", dto));
        StatusDTO vazio = new StatusDTO(); vazio.setStatus("   ");
        assertThrows(TransicaoInvalidaException.class, () -> service.transitar("1", vazio));
    }

    @Test
    void bloqueiaDescontoEmPedidoCancelado() {
        Pedido p = new Pedido(); p.setId("1"); p.setStatus(StatusPedido.CANCELADO);
        p.setItens(List.of()); service.recalcular(p); pedidos.put("1", p);
        DescontoDTO d = new DescontoDTO(); d.setPercentual(new BigDecimal("10.00"));
        assertThrows(NegocioException.class, () -> service.aplicarDesconto("1", d));
        CancelamentoDTO c = new CancelamentoDTO(); c.setJustificativa("Tentativa duplicada de cancelamento");
        assertThrows(NegocioException.class, () -> service.cancelar("1", c));
    }

    @Test
    void bloqueiaJustificativaLonga() {
        Pedido p = new Pedido(); p.setId("1"); p.setStatus(StatusPedido.PRONTO);
        p.setItens(List.of()); service.recalcular(p); pedidos.put("1", p);
        CancelamentoDTO c = new CancelamentoDTO(); c.setJustificativa("x".repeat(501));
        assertThrows(NegocioException.class, () -> service.cancelar("1", c));
    }

    @Test
    void descontoRegistraPercentualEValores() {
        Produto p1 = produtos.get("p1");
        Pedido p = new Pedido(); p.setId("1"); p.setStatus(StatusPedido.PRONTO);
        com.umc.pediupatrao.entity.ItemPedido item =
                new com.umc.pediupatrao.entity.ItemPedido(p1.getId(), p1.getNome(), 2, p1.getPreco());
        p.setItens(List.of(item)); service.recalcular(p); pedidos.put("1", p);
        int antes = auditorias;
        DescontoDTO d = new DescontoDTO(); d.setPercentual(new BigDecimal("10.00"));
        Pedido salvo = service.aplicarDesconto("1", d);
        assertEquals(new BigDecimal("100.00"), salvo.getSubtotal());
        assertEquals(new BigDecimal("10.00"), salvo.getValorDesconto());
        assertEquals(new BigDecimal("90.00"), salvo.getTotalFinal());
        assertEquals(new BigDecimal("10.00"), salvo.getPercentualDesconto());
        assertTrue(auditorias > antes, "desconto deve gerar auditoria");
    }

    @Test
    void transicaoGeraAuditoria() {
        Pedido p = new Pedido(); p.setId("1"); p.setStatus(StatusPedido.RECEBIDO);
        p.setItens(List.of()); service.recalcular(p); pedidos.put("1", p);
        int antes = auditorias;
        StatusDTO dto = new StatusDTO(); dto.setStatus("EM_PREPARACAO");
        service.transitar("1", dto);
        assertTrue(auditorias > antes, "transição deve gerar auditoria");
    }
}

