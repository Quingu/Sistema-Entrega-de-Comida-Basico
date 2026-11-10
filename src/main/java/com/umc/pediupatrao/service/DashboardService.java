package com.umc.pediupatrao.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.umc.pediupatrao.dto.dashboard.Agrupamento;
import com.umc.pediupatrao.dto.dashboard.DashboardFiltro;
import com.umc.pediupatrao.dto.dashboard.FiltrosResponse;
import com.umc.pediupatrao.dto.dashboard.OpcaoFiltro;
import com.umc.pediupatrao.dto.dashboard.PedidoRecenteResponse;
import com.umc.pediupatrao.dto.dashboard.PontoSerieVendas;
import com.umc.pediupatrao.dto.dashboard.ProdutoRanking;
import com.umc.pediupatrao.dto.dashboard.RankingProdutosResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoVendasResponse;
import com.umc.pediupatrao.dto.dashboard.SerieVendasResponse;
import com.umc.pediupatrao.dto.dashboard.SituacaoPedidosResponse;
import com.umc.pediupatrao.dto.dashboard.StatusResumo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Regras de negócio do dashboard administrativo.
 *
 * <p>Todos os cálculos são feitos a partir dos pedidos persistidos no MongoDB.
 * O período é sempre inclusivo e os pedidos cancelados não entram nas métricas
 * de faturamento, ticket médio, itens vendidos ou ranking de produtos. Eles
 * continuam aparecendo na situação dos pedidos e no indicador de cancelamentos,
 * pois são informação operacional relevante.</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    public static final int PERIODO_PADRAO_DIAS = 30;
    public static final int PERIODO_MAXIMO_DIAS = 731;
    public static final int LIMITE_MAXIMO = 50;

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter LABEL_DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter LABEL_DIA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter LABEL_MES = DateTimeFormatter.ofPattern("MM/yyyy");

    private static final Map<StatusPedido, String> DESCRICOES_STATUS = new EnumMap<>(StatusPedido.class);

    static {
        DESCRICOES_STATUS.put(StatusPedido.RECEBIDO, "Recebido");
        DESCRICOES_STATUS.put(StatusPedido.EM_PREPARACAO, "Em preparação");
        DESCRICOES_STATUS.put(StatusPedido.PRONTO, "Pronto");
        DESCRICOES_STATUS.put(StatusPedido.SAIU_PARA_ENTREGA, "Saiu para entrega");
        DESCRICOES_STATUS.put(StatusPedido.RETIRADO, "Retirado");
        DESCRICOES_STATUS.put(StatusPedido.FINALIZADO, "Finalizado");
        DESCRICOES_STATUS.put(StatusPedido.CANCELADO, "Cancelado");
    }

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;

    // ------------------------------------------------------------------
    // RF01 — Indicadores gerais
    // ------------------------------------------------------------------

    public ResumoVendasResponse resumo(DashboardFiltro filtro) {
        DashboardFiltro f = filtro == null ? new DashboardFiltro() : filtro;
        Periodo periodo = resolverPeriodo(f);
        Set<StatusPedido> status = f.statusSelecionados();
        List<Pedido> pedidos = carregar(periodo, status);
        List<Pedido> vendas = apenasVendas(pedidos);

        long dias = ChronoUnit.DAYS.between(periodo.inicio(), periodo.fim()) + 1;
        Periodo anterior = new Periodo(periodo.inicio().minusDays(dias), periodo.inicio().minusDays(1));
        List<Pedido> vendasAnteriores = apenasVendas(carregar(anterior, status));

        BigDecimal faturamento = somarTotal(vendas);
        BigDecimal faturamentoFinalizado = somarTotal(vendas.stream()
                .filter(p -> p.getStatus() == StatusPedido.FINALIZADO)
                .toList());
        BigDecimal desconto = vendas.stream()
                .map(p -> nuloParaZero(p.getValorDesconto()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long naoCancelados = vendas.size();
        long finalizados = pedidos.stream().filter(p -> p.getStatus() == StatusPedido.FINALIZADO).count();
        long cancelados = pedidos.stream().filter(p -> p.getStatus() == StatusPedido.CANCELADO).count();
        long emAndamento = pedidos.size() - finalizados - cancelados;
        long itens = somarItens(vendas);

        BigDecimal faturamentoAnterior = somarTotal(vendasAnteriores);
        BigDecimal variacao = calcularVariacao(faturamento, faturamentoAnterior);

        return ResumoVendasResponse.builder()
                .periodoInicio(periodo.inicio().format(ISO_DATE))
                .periodoFim(periodo.fim().format(ISO_DATE))
                .faturamentoTotal(escala(faturamento))
                .faturamentoFinalizado(escala(faturamentoFinalizado))
                .descontoTotal(escala(desconto))
                .ticketMedio(naoCancelados == 0 ? BigDecimal.ZERO.setScale(2)
                        : faturamento.divide(BigDecimal.valueOf(naoCancelados), 2, RoundingMode.HALF_UP))
                .totalPedidos(pedidos.size())
                .pedidosNaoCancelados(naoCancelados)
                .pedidosFinalizados(finalizados)
                .pedidosEmAndamento(emAndamento)
                .pedidosCancelados(cancelados)
                .taxaCancelamento(percentual(cancelados, pedidos.size()))
                .itensVendidos(itens)
                .faturamentoPeriodoAnterior(escala(faturamentoAnterior))
                .variacaoFaturamentoPercentual(variacao)
                .atualizadoEm(LocalDateTime.now().withNano(0).toString())
                .build();
    }

    // ------------------------------------------------------------------
    // RF02 — Análise de vendas (série temporal para gráficos)
    // ------------------------------------------------------------------

    public SerieVendasResponse vendas(DashboardFiltro filtro) {
        DashboardFiltro f = filtro == null ? new DashboardFiltro() : filtro;
        Periodo periodo = resolverPeriodo(f);
        Set<StatusPedido> status = f.statusSelecionados();
        List<Pedido> vendas = apenasVendas(carregar(periodo, status));
        Agrupamento agrupamento = f.agrupamentoOuPadrao();

        List<Bucket> buckets = criarBuckets(periodo, agrupamento);
        Map<LocalDate, Acumulador> acumuladores = new LinkedHashMap<>();
        for (Bucket bucket : buckets) {
            acumuladores.put(bucket.chave(), new Acumulador());
        }

        for (Pedido pedido : vendas) {
            LocalDate chave = inicioDoBucket(pedido.getDataEntrada().toLocalDate(), agrupamento);
            Acumulador acumulador = acumuladores.get(chave);
            if (acumulador != null) {
                acumulador.adicionar(pedido);
            }
        }

        List<PontoSerieVendas> pontos = new ArrayList<>();
        for (Bucket bucket : buckets) {
            Acumulador acumulador = acumuladores.get(bucket.chave());
            pontos.add(PontoSerieVendas.builder()
                    .rotulo(bucket.rotulo())
                    .inicio(bucket.inicio().format(ISO_DATE))
                    .fim(bucket.fim().format(ISO_DATE))
                    .faturamento(escala(acumulador.faturamento))
                    .pedidos(acumulador.pedidos)
                    .itens(acumulador.itens)
                    .build());
        }

        return SerieVendasResponse.builder()
                .agrupamento(agrupamento.name())
                .periodoInicio(periodo.inicio().format(ISO_DATE))
                .periodoFim(periodo.fim().format(ISO_DATE))
                .faturamentoTotal(escala(somarTotal(vendas)))
                .totalPedidos(vendas.size())
                .totalItens(somarItens(vendas))
                .pontos(pontos)
                .build();
    }

    // ------------------------------------------------------------------
    // RF03 — Situação dos pedidos
    // ------------------------------------------------------------------

    public SituacaoPedidosResponse situacao(DashboardFiltro filtro) {
        DashboardFiltro f = filtro == null ? new DashboardFiltro() : filtro;
        Periodo periodo = resolverPeriodo(f);
        List<Pedido> pedidos = carregar(periodo, f.statusSelecionados());

        Map<StatusPedido, Long> quantidades = new EnumMap<>(StatusPedido.class);
        Map<StatusPedido, BigDecimal> valores = new EnumMap<>(StatusPedido.class);
        for (StatusPedido s : StatusPedido.values()) {
            quantidades.put(s, 0L);
            valores.put(s, BigDecimal.ZERO);
        }

        for (Pedido pedido : pedidos) {
            StatusPedido s = pedido.getStatus() == null ? StatusPedido.RECEBIDO : pedido.getStatus();
            quantidades.merge(s, 1L, Long::sum);
            valores.merge(s, nuloParaZero(pedido.getTotalFinal()), BigDecimal::add);
        }

        long total = pedidos.size();
        long finalizados = quantidades.get(StatusPedido.FINALIZADO);
        long cancelados = quantidades.get(StatusPedido.CANCELADO);

        List<StatusResumo> status = new ArrayList<>();
        for (StatusPedido s : StatusPedido.values()) {
            status.add(StatusResumo.builder()
                    .status(s.name())
                    .descricao(descricao(s))
                    .quantidade(quantidades.get(s))
                    .percentual(percentual(quantidades.get(s), total))
                    .valor(escala(valores.get(s)))
                    .build());
        }

        return SituacaoPedidosResponse.builder()
                .total(total)
                .emAndamento(total - finalizados - cancelados)
                .finalizados(finalizados)
                .cancelados(cancelados)
                .status(status)
                .build();
    }

    // ------------------------------------------------------------------
    // RF04 — Produtos mais vendidos
    // ------------------------------------------------------------------

    public RankingProdutosResponse produtosMaisVendidos(DashboardFiltro filtro) {
        DashboardFiltro f = filtro == null ? new DashboardFiltro() : filtro;
        int limite = validarLimite(f.limiteOuPadrao(10));
        Periodo periodo = resolverPeriodo(f);
        List<Pedido> vendas = apenasVendas(carregar(periodo, f.statusSelecionados()));

        Map<String, ItemAcumulado> acumulados = new HashMap<>();
        long totalItens = 0;
        BigDecimal faturamentoTotal = BigDecimal.ZERO;

        for (Pedido pedido : vendas) {
            if (pedido.getItens() == null) {
                continue;
            }
            for (ItemPedido item : pedido.getItens()) {
                if (item == null || item.getQuantidade() <= 0) {
                    continue;
                }
                String chave = item.getProdutoId() != null && !item.getProdutoId().isBlank()
                        ? item.getProdutoId()
                        : "nome:" + (item.getNomeProduto() == null ? "sem-nome" : item.getNomeProduto());
                BigDecimal valor = item.getSubtotal() != null
                        ? item.getSubtotal()
                        : nuloParaZero(item.getPrecoUnitario()).multiply(BigDecimal.valueOf(item.getQuantidade()));
                ItemAcumulado acumulado = acumulados.computeIfAbsent(chave,
                        k -> new ItemAcumulado(item.getProdutoId(), item.getNomeProduto()));
                acumulado.quantidade += item.getQuantidade();
                acumulado.faturamento = acumulado.faturamento.add(valor);
                totalItens += item.getQuantidade();
                faturamentoTotal = faturamentoTotal.add(valor);
            }
        }

        Map<String, Produto> produtos = new HashMap<>();
        List<Produto> todosProdutos = produtoRepository.findAll();
        if (todosProdutos != null) {
            for (Produto produto : todosProdutos) {
                if (produto != null && produto.getId() != null) {
                    produtos.put(produto.getId(), produto);
                }
            }
        }

        List<ItemAcumulado> ordenados = new ArrayList<>(acumulados.values());
        ordenados.sort(Comparator
                .comparingLong((ItemAcumulado i) -> i.quantidade).reversed()
                .thenComparing(i -> i.faturamento, Comparator.reverseOrder())
                .thenComparing(i -> i.nome == null ? "" : i.nome, String.CASE_INSENSITIVE_ORDER));

        List<ProdutoRanking> ranking = new ArrayList<>();
        int posicao = 1;
        for (ItemAcumulado item : ordenados) {
            if (ranking.size() >= limite) {
                break;
            }
            Produto produto = item.produtoId == null ? null : produtos.get(item.produtoId);
            String categoria = produto == null || produto.getTipo() == null || produto.getTipo().isBlank()
                    ? "Não informada"
                    : produto.getTipo();
            ranking.add(ProdutoRanking.builder()
                    .posicao(posicao++)
                    .produtoId(item.produtoId)
                    .nome(item.nome == null || item.nome.isBlank() ? "Produto removido" : item.nome)
                    .categoria(categoria)
                    .quantidade(item.quantidade)
                    .faturamento(escala(item.faturamento))
                    .participacaoPercentual(percentual(item.quantidade, totalItens))
                    .build());
        }

        return RankingProdutosResponse.builder()
                .totalItensVendidos(totalItens)
                .faturamentoTotal(escala(faturamentoTotal))
                .produtos(ranking)
                .build();
    }

    // ------------------------------------------------------------------
    // RF05 — Apoio aos filtros e lista operacional
    // ------------------------------------------------------------------

    public FiltrosResponse filtros() {
        Optional<Pedido> primeiro = pedidoRepository.findFirstByOrderByDataEntradaAsc();
        Optional<Pedido> ultimo = pedidoRepository.findFirstByOrderByDataEntradaDesc();

        List<OpcaoFiltro> status = new ArrayList<>();
        for (StatusPedido s : StatusPedido.values()) {
            status.add(new OpcaoFiltro(s.name(), descricao(s)));
        }

        List<OpcaoFiltro> agrupamentos = List.of(
                new OpcaoFiltro(Agrupamento.DIA.name(), "Diário"),
                new OpcaoFiltro(Agrupamento.SEMANA.name(), "Semanal"),
                new OpcaoFiltro(Agrupamento.MES.name(), "Mensal"));

        List<OpcaoFiltro> periodos = List.of(
                new OpcaoFiltro("HOJE", "Hoje"),
                new OpcaoFiltro("7", "Últimos 7 dias"),
                new OpcaoFiltro("30", "Últimos 30 dias"),
                new OpcaoFiltro("MES", "Este mês"));

        return FiltrosResponse.builder()
                .dataMinima(primeiro.map(p -> p.getDataEntrada().toLocalDate().format(ISO_DATE)).orElse(null))
                .dataMaxima(ultimo.map(p -> p.getDataEntrada().toLocalDate().format(ISO_DATE)).orElse(null))
                .periodoPadraoDias(PERIODO_PADRAO_DIAS)
                .status(status)
                .agrupamentos(agrupamentos)
                .periodosRapidos(periodos)
                .build();
    }

    public List<PedidoRecenteResponse> pedidosRecentes(DashboardFiltro filtro) {
        DashboardFiltro f = filtro == null ? new DashboardFiltro() : filtro;
        int limite = validarLimite(f.limiteOuPadrao(8));
        Periodo periodo = resolverPeriodo(f);
        List<Pedido> pedidos = new ArrayList<>(carregar(periodo, f.statusSelecionados()));

        pedidos.sort(Comparator.comparing(Pedido::getDataEntrada,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, String> clientes = new HashMap<>();
        List<Cliente> todosClientes = clienteRepository.findAll();
        if (todosClientes != null) {
            for (Cliente cliente : todosClientes) {
                if (cliente != null && cliente.getId() != null) {
                    clientes.put(cliente.getId(), cliente.getNome());
                }
            }
        }

        List<PedidoRecenteResponse> recentes = new ArrayList<>();
        for (Pedido pedido : pedidos) {
            if (recentes.size() >= limite) {
                break;
            }
            int itens = pedido.getItens() == null ? 0 : pedido.getItens().stream()
                    .filter(i -> i != null)
                    .mapToInt(ItemPedido::getQuantidade)
                    .sum();
            String nomeCliente = clientes.get(pedido.getClienteId());
            if (nomeCliente == null || nomeCliente.isBlank()) {
                nomeCliente = pedido.getClienteId() == null ? "Não informado" : "Cliente " + pedido.getClienteId();
            }
            recentes.add(PedidoRecenteResponse.builder()
                    .id(pedido.getId())
                    .dataEntrada(pedido.getDataEntrada() == null ? null : pedido.getDataEntrada().format(LABEL_DIA_HORA))
                    .clienteId(pedido.getClienteId())
                    .clienteNome(nomeCliente)
                    .status(pedido.getStatus() == null ? null : pedido.getStatus().name())
                    .statusDescricao(descricao(pedido.getStatus()))
                    .quantidadeItens(itens)
                    .total(escala(nuloParaZero(pedido.getTotalFinal())))
                    .responsavel(pedido.getUsuarioResponsavel())
                    .build());
        }
        return recentes;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Periodo resolverPeriodo(DashboardFiltro filtro) {
        LocalDate hoje = LocalDate.now();
        LocalDate fim = filtro.getDataFim() == null ? hoje : filtro.getDataFim();
        LocalDate inicio = filtro.getDataInicio() == null
                ? fim.minusDays(PERIODO_PADRAO_DIAS - 1L)
                : filtro.getDataInicio();

        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final.");
        }
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (dias > PERIODO_MAXIMO_DIAS) {
            throw new IllegalArgumentException(
                    "O período máximo permitido é de " + PERIODO_MAXIMO_DIAS + " dias.");
        }
        return new Periodo(inicio, fim);
    }

    private List<Pedido> carregar(Periodo periodo, Set<StatusPedido> status) {
        List<Pedido> pedidos = pedidoRepository.findByDataEntradaBetween(
                periodo.inicio().atStartOfDay(),
                periodo.fim().atTime(LocalTime.MAX));
        if (pedidos == null) {
            return List.of();
        }
        return pedidos.stream()
                .filter(p -> p != null && p.getDataEntrada() != null)
                .filter(p -> status == null || status.isEmpty() || status.contains(p.getStatus()))
                .toList();
    }

    private List<Pedido> apenasVendas(List<Pedido> pedidos) {
        return pedidos.stream()
                .filter(p -> p.getStatus() != StatusPedido.CANCELADO)
                .toList();
    }

    private int validarLimite(int limite) {
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("O limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return limite;
    }

    private List<Bucket> criarBuckets(Periodo periodo, Agrupamento agrupamento) {
        List<Bucket> buckets = new ArrayList<>();
        LocalDate cursor = inicioDoBucket(periodo.inicio(), agrupamento);
        while (!cursor.isAfter(periodo.fim())) {
            LocalDate inicioEfetivo = cursor.isBefore(periodo.inicio()) ? periodo.inicio() : cursor;
            LocalDate fimBucket = fimDoBucket(cursor, agrupamento);
            LocalDate fimEfetivo = fimBucket.isAfter(periodo.fim()) ? periodo.fim() : fimBucket;
            buckets.add(new Bucket(cursor, inicioEfetivo, fimEfetivo, rotulo(cursor, agrupamento)));
            cursor = proximoBucket(cursor, agrupamento);
        }
        return buckets;
    }

    private LocalDate inicioDoBucket(LocalDate data, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> data;
            case SEMANA -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> data.withDayOfMonth(1);
        };
    }

    private LocalDate fimDoBucket(LocalDate inicio, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> inicio;
            case SEMANA -> inicio.plusDays(6);
            case MES -> inicio.with(TemporalAdjusters.lastDayOfMonth());
        };
    }

    private LocalDate proximoBucket(LocalDate inicio, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> inicio.plusDays(1);
            case SEMANA -> inicio.plusWeeks(1);
            case MES -> inicio.plusMonths(1);
        };
    }

    private String rotulo(LocalDate inicio, Agrupamento agrupamento) {
        return switch (agrupamento) {
            case DIA -> inicio.format(LABEL_DIA);
            case SEMANA -> "Semana " + inicio.format(LABEL_DIA);
            case MES -> inicio.format(LABEL_MES);
        };
    }

    private BigDecimal somarTotal(List<Pedido> pedidos) {
        return pedidos.stream()
                .map(p -> nuloParaZero(p.getTotalFinal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long somarItens(List<Pedido> pedidos) {
        long total = 0;
        for (Pedido pedido : pedidos) {
            if (pedido.getItens() == null) {
                continue;
            }
            for (ItemPedido item : pedido.getItens()) {
                if (item != null && item.getQuantidade() > 0) {
                    total += item.getQuantidade();
                }
            }
        }
        return total;
    }

    private BigDecimal calcularVariacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.compareTo(BigDecimal.ZERO) == 0) {
            return atual != null && atual.compareTo(BigDecimal.ZERO) > 0
                    ? new BigDecimal("100.00")
                    : BigDecimal.ZERO.setScale(2);
        }
        return atual.subtract(anterior)
                .divide(anterior, 4, RoundingMode.HALF_UP)
                .multiply(CEM)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentual(long parte, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(parte)
                .multiply(CEM)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal escala(BigDecimal valor) {
        return nuloParaZero(valor).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nuloParaZero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private String descricao(StatusPedido status) {
        if (status == null) {
            return "Não informado";
        }
        return DESCRICOES_STATUS.getOrDefault(status, status.name());
    }

    private record Periodo(LocalDate inicio, LocalDate fim) {
    }

    private record Bucket(LocalDate chave, LocalDate inicio, LocalDate fim, String rotulo) {
    }

    private static final class Acumulador {
        private BigDecimal faturamento = BigDecimal.ZERO;
        private long pedidos;
        private long itens;

        private void adicionar(Pedido pedido) {
            faturamento = faturamento.add(pedido.getTotalFinal() == null ? BigDecimal.ZERO : pedido.getTotalFinal());
            pedidos++;
            if (pedido.getItens() != null) {
                for (ItemPedido item : pedido.getItens()) {
                    if (item != null && item.getQuantidade() > 0) {
                        itens += item.getQuantidade();
                    }
                }
            }
        }
    }

    private static final class ItemAcumulado {
        private final String produtoId;
        private final String nome;
        private long quantidade;
        private BigDecimal faturamento = BigDecimal.ZERO;

        private ItemAcumulado(String produtoId, String nome) {
            this.produtoId = produtoId;
            this.nome = nome;
        }
    }
}
