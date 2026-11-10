package com.umc.pediupatrao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.umc.pediupatrao.dto.dashboard.DashboardFiltro;
import com.umc.pediupatrao.dto.dashboard.FiltrosResponse;
import com.umc.pediupatrao.dto.dashboard.PedidoRecenteResponse;
import com.umc.pediupatrao.dto.dashboard.RankingProdutosResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasResponse;
import com.umc.pediupatrao.dto.dashboard.SerieVendasResponse;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidosResponse;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.DashboardService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Testes de unidade das regras de agregação do dashboard.
 * O repositório é simulado para isolar os cálculos de negócio.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 10);

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private ClienteRepository clienteRepository;

    private DashboardService service;
    private List<Pedido> pedidos;

    @BeforeEach
    void setup() {
        service = new DashboardService(pedidoRepository, produtoRepository, clienteRepository);

        Produto pizza = produto("p1", "Pizza Calabresa", "Pizza", "50.00");
        Produto bebida = produto("p2", "Refrigerante 2L", "Bebida", "30.00");

        pedidos = List.of(
                pedido("ped-1", LocalDate.of(2026, 11, 1), StatusPedido.FINALIZADO, "100.00",
                        "c1", item(pizza, 2)),
                pedido("ped-2", LocalDate.of(2026, 11, 5), StatusPedido.CANCELADO, "50.00",
                        "c2", item(pizza, 1)),
                pedido("ped-3", LocalDate.of(2026, 11, 8), StatusPedido.EM_PREPARACAO, "30.00",
                        "c1", item(bebida, 1)),
                pedido("ped-4", LocalDate.of(2026, 10, 25), StatusPedido.FINALIZADO, "80.00",
                        "c2", item(bebida, 2)),
                pedido("ped-5", LocalDate.of(2026, 10, 1), StatusPedido.FINALIZADO, "40.00",
                        "c3", item(pizza, 1))
        );

        lenient().when(pedidoRepository.findByDataEntradaBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    LocalDateTime inicio = invocation.getArgument(0);
                    LocalDateTime fim = invocation.getArgument(1);
                    return pedidos.stream()
                            .filter(p -> !p.getDataEntrada().isBefore(inicio) && !p.getDataEntrada().isAfter(fim))
                            .toList();
                });
        lenient().when(produtoRepository.findAll()).thenReturn(List.of(pizza, bebida));
        lenient().when(clienteRepository.findAll()).thenReturn(List.of(
                cliente("c1", "Ana Souza"),
                cliente("c2", "Bruno Carvalho"),
                cliente("c3", "Camila Ferreira")));
        lenient().when(pedidoRepository.findFirstByOrderByDataEntradaAsc()).thenReturn(Optional.of(pedidos.get(4)));
        lenient().when(pedidoRepository.findFirstByOrderByDataEntradaDesc()).thenReturn(Optional.of(pedidos.get(2)));
    }

    @Test
    void resumoCalculaIndicadoresEDesconsideraCanceladosNoFaturamento() {
        ResumoVendasResponse resumo = service.resumo(filtro(INICIO, FIM));

        assertEquals(new BigDecimal("130.00"), resumo.faturamentoTotal());
        assertEquals(new BigDecimal("100.00"), resumo.faturamentoFinalizado());
        assertEquals(new BigDecimal("65.00"), resumo.ticketMedio());
        assertEquals(3, resumo.totalPedidos());
        assertEquals(2, resumo.pedidosNaoCancelados());
        assertEquals(1, resumo.pedidosFinalizados());
        assertEquals(1, resumo.pedidosEmAndamento());
        assertEquals(1, resumo.pedidosCancelados());
        assertEquals(new BigDecimal("33.33"), resumo.taxaCancelamento());
        assertEquals(3, resumo.itensVendidos());
        assertEquals(new BigDecimal("80.00"), resumo.faturamentoPeriodoAnterior());
        assertEquals(new BigDecimal("62.50"), resumo.variacaoFaturamentoPercentual());
        assertNotNull(resumo.atualizadoEm());
    }

    @Test
    void filtroDeStatusRestringeTodosOsIndicadores() {
        DashboardFiltro filtro = filtro(INICIO, FIM);
        filtro.setStatus("FINALIZADO");

        ResumoVendasResponse resumo = service.resumo(filtro);

        assertEquals(1, resumo.totalPedidos());
        assertEquals(0, resumo.pedidosCancelados());
        assertEquals(new BigDecimal("100.00"), resumo.faturamentoTotal());
    }

    @Test
    void serieDiariaPreencheDiasSemVendaComZeroEIgnoraCancelados() {
        SerieVendasResponse serie = service.vendas(filtro(INICIO, FIM));

        assertEquals("DIA", serie.agrupamento());
        assertEquals(10, serie.pontos().size());
        assertEquals(new BigDecimal("100.00"), serie.pontos().get(0).faturamento());
        assertEquals(1, serie.pontos().get(0).pedidos());
        assertEquals(0, serie.pontos().get(4).pedidos(), "05/11 é cancelado e não entra na série de vendas");
        assertEquals(new BigDecimal("30.00"), serie.pontos().get(7).faturamento());
        assertEquals(2, serie.totalPedidos());
    }

    @Test
    void serieMensalAgrupaPeriodoLongo() {
        DashboardFiltro filtro = filtro(LocalDate.of(2026, 9, 1), FIM);
        filtro.setAgrupamento(com.umc.pediupatrao.dto.dashboard.Agrupamento.MES);
        SerieVendasResponse serie = service.vendas(filtro);

        assertEquals("MES", serie.agrupamento());
        assertEquals(3, serie.pontos().size());
        assertEquals("09/2026", serie.pontos().get(0).rotulo());
        assertEquals(new BigDecimal("120.00"), serie.pontos().get(1).faturamento());
        assertEquals(new BigDecimal("130.00"), serie.pontos().get(2).faturamento());
    }

    @Test
    void situacaoContaTodosOsStatusEPercentuais() {
        SituacaoPedidosResponse situacao = service.situacao(filtro(INICIO, FIM));

        assertEquals(3, situacao.total());
        assertEquals(1, situacao.finalizados());
        assertEquals(1, situacao.cancelados());
        assertEquals(1, situacao.emAndamento());

        var finalizado = situacao.status().stream()
                .filter(s -> s.status().equals("FINALIZADO"))
                .findFirst().orElseThrow();
        assertEquals(1, finalizado.quantidade());
        assertEquals(new BigDecimal("33.33"), finalizado.percentual());
        assertEquals(new BigDecimal("100.00"), finalizado.valor());
    }

    @Test
    void rankingAgregaItensPorProdutoEOrdenaPorQuantidade() {
        RankingProdutosResponse ranking = service.produtosMaisVendidos(filtro(INICIO, FIM));

        assertEquals(3, ranking.totalItensVendidos());
        assertEquals(2, ranking.produtos().size());
        assertEquals("Pizza Calabresa", ranking.produtos().get(0).nome());
        assertEquals(2, ranking.produtos().get(0).quantidade());
        assertEquals(new BigDecimal("100.00"), ranking.produtos().get(0).faturamento());
        assertEquals("Pizza", ranking.produtos().get(0).categoria());
        assertEquals("Refrigerante 2L", ranking.produtos().get(1).nome());
    }

    @Test
    void rankingRespeitaLimite() {
        DashboardFiltro filtro = filtro(INICIO, FIM);
        filtro.setLimite(1);

        RankingProdutosResponse ranking = service.produtosMaisVendidos(filtro);

        assertEquals(1, ranking.produtos().size());
        assertEquals("Pizza Calabresa", ranking.produtos().get(0).nome());
    }

    @Test
    void pedidosRecentesOrdenaPorDataENomeiaCliente() {
        DashboardFiltro filtro = filtro(INICIO, FIM);
        filtro.setLimite(2);

        List<PedidoRecenteResponse> recentes = service.pedidosRecentes(filtro);

        assertEquals(2, recentes.size());
        assertEquals("ped-3", recentes.get(0).id());
        assertEquals("Ana Souza", recentes.get(0).clienteNome());
        assertEquals("Em preparação", recentes.get(0).statusDescricao());
        assertEquals("ped-2", recentes.get(1).id());
    }

    @Test
    void filtrosRetornaDatasLimitesEOpcoes() {
        FiltrosResponse filtros = service.filtros();

        assertEquals("2026-10-01", filtros.dataMinima());
        assertEquals("2026-11-08", filtros.dataMaxima());
        assertEquals(7, filtros.status().size());
        assertEquals(3, filtros.agrupamentos().size());
        assertEquals(30, filtros.periodoPadraoDias());
    }

    @Test
    void periodoInvertidoGeraErroDeValidacao() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resumo(filtro(FIM, INICIO)));
    }

    @Test
    void periodoAcimaDoLimiteGeraErroDeValidacao() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resumo(filtro(INICIO.minusDays(800), FIM)));
    }

    @Test
    void statusInvalidoGeraErroDeValidacao() {
        DashboardFiltro filtro = filtro(INICIO, FIM);
        filtro.setStatus("ENTREGUE");
        assertThrows(IllegalArgumentException.class, () -> service.resumo(filtro));
    }

    @Test
    void limiteInvalidoGeraErroDeValidacao() {
        DashboardFiltro filtro = filtro(INICIO, FIM);
        filtro.setLimite(0);
        assertThrows(IllegalArgumentException.class, () -> service.produtosMaisVendidos(filtro));
    }

    @Test
    void bancoVazioRetornaIndicadoresZeradosSemErro() {
        when(pedidoRepository.findByDataEntradaBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        ResumoVendasResponse resumo = service.resumo(filtro(INICIO, FIM));

        assertEquals(BigDecimal.ZERO.setScale(2), resumo.faturamentoTotal());
        assertEquals(0, resumo.totalPedidos());
        assertEquals(0, service.produtosMaisVendidos(filtro(INICIO, FIM)).produtos().size());
        assertTrue(service.pedidosRecentes(filtro(INICIO, FIM)).isEmpty());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private DashboardFiltro filtro(LocalDate inicio, LocalDate fim) {
        DashboardFiltro filtro = new DashboardFiltro();
        filtro.setDataInicio(inicio);
        filtro.setDataFim(fim);
        filtro.setAgrupamento(com.umc.pediupatrao.dto.dashboard.Agrupamento.DIA);
        return filtro;
    }

    private Pedido pedido(String id, LocalDate data, StatusPedido status, String total,
                          String clienteId, ItemPedido item) {
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setDataEntrada(data.atTime(20, 0));
        pedido.setStatus(status);
        pedido.setClienteId(clienteId);
        pedido.setUsuarioResponsavel("atendente");
        pedido.setItens(List.of(item));
        pedido.setSubtotal(new BigDecimal(total));
        pedido.setValorDesconto(BigDecimal.ZERO);
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        pedido.setTotalFinal(new BigDecimal(total));
        return pedido;
    }

    private ItemPedido item(Produto produto, int quantidade) {
        return new ItemPedido(produto.getId(), produto.getNome(), quantidade, produto.getPreco());
    }

    private Produto produto(String id, String nome, String tipo, String preco) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(new BigDecimal(preco));
        produto.setAtivo(true);
        return produto;
    }

    private Cliente cliente(String id, String nome) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setNome(nome);
        return cliente;
    }
}
