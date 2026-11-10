/* =========================================================
   Dashboard administrativo — Pediu Patrão
   Consome /api/dashboard/** e renderiza KPIs, gráficos e tabelas.
   ========================================================= */
(function () {
    'use strict';

    const API = '/api/dashboard';
    const AUTO_REFRESH_MS = 60 * 1000;

    const STATUS_LABEL = {
        RECEBIDO: 'Recebido',
        EM_PREPARACAO: 'Em preparação',
        PRONTO: 'Pronto',
        SAIU_PARA_ENTREGA: 'Saiu para entrega',
        RETIRADO: 'Retirado',
        FINALIZADO: 'Finalizado',
        CANCELADO: 'Cancelado'
    };

    const STATUS_COR = {
        RECEBIDO: '#2563eb',
        EM_PREPARACAO: '#d97706',
        PRONTO: '#7c3aed',
        SAIU_PARA_ENTREGA: '#0891b2',
        RETIRADO: '#0f766e',
        FINALIZADO: '#16a34a',
        CANCELADO: '#dc2626'
    };

    const state = {
        dataInicio: '',
        dataFim: '',
        status: [],
        agrupamento: 'DIA',
        metrica: 'faturamento',
        periodoRapido: '30',
        auto: false,
        carregando: false,
        ultimaSerie: null,
        filtros: null
    };

    const charts = {};
    const el = {};

    // ------------------------------------------------------------------
    // Inicialização
    // ------------------------------------------------------------------

    document.addEventListener('DOMContentLoaded', init);

    async function init() {
        mapearElementos();
        if (!el.root) {
            return;
        }
        if (typeof Chart === 'undefined') {
            mostrarAlerta('Não foi possível carregar a biblioteca de gráficos. Verifique sua conexão e recarregue a página.', 'danger');
            return;
        }
        Chart.defaults.global.defaultFontFamily = "'Segoe UI', system-ui, -apple-system, sans-serif";
        Chart.defaults.global.defaultFontColor = '#6b7280';

        bindEventos();
        aplicarEstadoDaUrl();
        sincronizarInputs();
        atualizarResumoPeriodo();
        configurarAutoRefresh();

        try {
            state.filtros = await apiGet('/filtros');
        } catch (e) {
            state.filtros = filtrosFallback();
            mostrarAlerta(e.message, 'warning');
        }
        renderizarOpcoesStatus();
        renderizarAgrupamentos();
        await carregarTudo();
    }

    function mapearElementos() {
        el.root = document.getElementById('dashRoot');
        if (!el.root) {
            return;
        }
        el.alerta = document.getElementById('dashAlerta');
        el.dataInicio = document.getElementById('dashDataInicio');
        el.dataFim = document.getElementById('dashDataFim');
        el.agrupamento = document.getElementById('dashAgrupamento');
        el.auto = document.getElementById('dashAuto');
        el.atualizar = document.getElementById('dashAtualizar');
        el.aplicar = document.getElementById('dashAplicar');
        el.limpar = document.getElementById('dashLimpar');
        el.statusBotao = document.getElementById('dashStatusBotao');
        el.statusOpcoes = document.getElementById('dashStatusOpcoes');
        el.statusTodos = document.getElementById('dashStatusTodos');
        el.periodoResumo = document.getElementById('dashPeriodoResumo');
        el.atualizado = document.getElementById('dashAtualizado');
        el.vendasSubtitulo = document.getElementById('dashVendasSubtitulo');
    }

    function bindEventos() {
        el.aplicar.addEventListener('click', () => {
            state.periodoRapido = null;
            if (lerFiltrosDaTela()) {
                carregarTudo();
            }
        });
        el.atualizar.addEventListener('click', () => carregarTudo());
        el.limpar.addEventListener('click', limparFiltros);

        [el.dataInicio, el.dataFim].forEach(input => {
            input.addEventListener('keydown', event => {
                if (event.key === 'Enter') {
                    el.aplicar.click();
                }
            });
            input.addEventListener('change', () => {
                state.periodoRapido = null;
                marcarPeriodoAtivo();
            });
        });

        el.agrupamento.addEventListener('change', () => {
            state.agrupamento = el.agrupamento.value;
            if (state.ultimaSerie) {
                carregarTudo();
            }
        });

        document.querySelectorAll('.dash-chip[data-periodo]').forEach(botao => {
            botao.addEventListener('click', () => aplicarPeriodoRapido(botao.dataset.periodo));
        });

        document.querySelectorAll('.dash-segmented [data-metrica]').forEach(botao => {
            botao.addEventListener('click', () => {
                state.metrica = botao.dataset.metrica;
                document.querySelectorAll('.dash-segmented [data-metrica]').forEach(b => {
                    b.classList.toggle('is-active', b === botao);
                });
                if (state.ultimaSerie) {
                    renderizarGraficoVendas(state.ultimaSerie);
                }
            });
        });

        el.auto.addEventListener('change', () => {
            state.auto = el.auto.checked;
            configurarAutoRefresh();
        });

        el.statusTodos.addEventListener('click', event => {
            event.preventDefault();
            el.statusOpcoes.querySelectorAll('input[type="checkbox"]').forEach(c => {
                c.checked = false;
            });
            atualizarRotuloStatus();
        });
    }

    // ------------------------------------------------------------------
    // Filtros
    // ------------------------------------------------------------------

    function aplicarEstadoDaUrl() {
        const params = new URLSearchParams(window.location.search);
        const hoje = new Date();
        const inicioPadrao = somarDias(hoje, -(state.filtros ? state.filtros.periodoPadraoDias - 1 : 29));

        state.dataInicio = params.get('dataInicio') || isoLocal(inicioPadrao);
        state.dataFim = params.get('dataFim') || isoLocal(hoje);
        state.agrupamento = ['DIA', 'SEMANA', 'MES'].includes(params.get('agrupamento'))
            ? params.get('agrupamento')
            : 'DIA';
        state.status = (params.get('status') || '')
            .split(',')
            .map(s => s.trim().toUpperCase())
            .filter(s => STATUS_LABEL[s]);

        if (params.get('dataInicio') || params.get('dataFim')) {
            state.periodoRapido = detectarPeriodoRapido(state.dataInicio, state.dataFim);
        }
        state.auto = localStorage.getItem('dashAuto') === '1';
    }

    function sincronizarInputs() {
        el.dataInicio.value = state.dataInicio;
        el.dataFim.value = state.dataFim;
        el.agrupamento.value = state.agrupamento;
        el.auto.checked = state.auto;
        marcarPeriodoAtivo();
    }

    function lerFiltrosDaTela() {
        const inicio = el.dataInicio.value;
        const fim = el.dataFim.value;
        if (!inicio || !fim) {
            mostrarAlerta('Informe a data inicial e a data final para aplicar o filtro.', 'warning');
            return false;
        }
        if (inicio > fim) {
            mostrarAlerta('A data inicial não pode ser posterior à data final.', 'warning');
            return false;
        }
        state.dataInicio = inicio;
        state.dataFim = fim;
        state.agrupamento = el.agrupamento.value || 'DIA';
        state.status = Array.from(el.statusOpcoes.querySelectorAll('input[type="checkbox"]:checked'))
            .map(c => c.value);
        atualizarResumoPeriodo();
        sincronizarUrl();
        return true;
    }

    function aplicarPeriodoRapido(periodo) {
        const hoje = new Date();
        let inicio = new Date(hoje);
        if (periodo === 'HOJE') {
            inicio = new Date(hoje);
        } else if (periodo === '7') {
            inicio = somarDias(hoje, -6);
        } else if (periodo === '30') {
            inicio = somarDias(hoje, -29);
        } else if (periodo === 'MES') {
            inicio = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
        }
        state.periodoRapido = periodo;
        state.dataInicio = isoLocal(inicio);
        state.dataFim = isoLocal(hoje);
        sincronizarInputs();
        carregarTudo();
    }

    function limparFiltros() {
        const hoje = new Date();
        state.dataInicio = isoLocal(somarDias(hoje, -29));
        state.dataFim = isoLocal(hoje);
        state.status = [];
        state.agrupamento = 'DIA';
        state.metrica = 'faturamento';
        state.periodoRapido = '30';
        el.statusOpcoes.querySelectorAll('input[type="checkbox"]').forEach(c => {
            c.checked = false;
        });
        document.querySelectorAll('.dash-segmented [data-metrica]').forEach(b => {
            b.classList.toggle('is-active', b.dataset.metrica === 'faturamento');
        });
        sincronizarInputs();
        atualizarRotuloStatus();
        atualizarResumoPeriodo();
        carregarTudo();
    }

    function detectarPeriodoRapido(inicio, fim) {
        const hoje = isoLocal(new Date());
        if (fim !== hoje) {
            return null;
        }
        if (inicio === hoje) {
            return 'HOJE';
        }
        const diff = Math.round((new Date(fim + 'T12:00:00') - new Date(inicio + 'T12:00:00')) / 86400000) + 1;
        if (diff === 7) {
            return '7';
        }
        if (diff === 30) {
            return '30';
        }
        const primeiroDia = new Date();
        primeiroDia.setDate(1);
        if (inicio === isoLocal(primeiroDia)) {
            return 'MES';
        }
        return null;
    }

    function marcarPeriodoAtivo() {
        document.querySelectorAll('.dash-chip[data-periodo]').forEach(botao => {
            botao.classList.toggle('is-active', botao.dataset.periodo === state.periodoRapido);
        });
    }

    function renderizarOpcoesStatus() {
        const opcoes = (state.filtros && state.filtros.status) || Object.keys(STATUS_LABEL).map(v => ({
            valor: v,
            rotulo: STATUS_LABEL[v]
        }));
        el.statusOpcoes.innerHTML = '';
        opcoes.forEach(opcao => {
            const label = document.createElement('label');
            label.className = 'dash-status-opcao';
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.value = opcao.valor;
            checkbox.checked = state.status.includes(opcao.valor);
            checkbox.addEventListener('change', atualizarRotuloStatus);
            const texto = document.createElement('span');
            texto.textContent = opcao.rotulo || STATUS_LABEL[opcao.valor] || opcao.valor;
            label.appendChild(checkbox);
            label.appendChild(texto);
            el.statusOpcoes.appendChild(label);
        });
        atualizarRotuloStatus();
    }

    function atualizarRotuloStatus() {
        const marcados = Array.from(el.statusOpcoes.querySelectorAll('input[type="checkbox"]:checked'));
        if (marcados.length === 0) {
            el.statusBotao.textContent = 'Todos os status';
        } else if (marcados.length === 1) {
            el.statusBotao.textContent = STATUS_LABEL[marcados[0].value] || marcados[0].value;
        } else {
            el.statusBotao.textContent = marcados.length + ' status selecionados';
        }
    }

    function renderizarAgrupamentos() {
        const opcoes = (state.filtros && state.filtros.agrupamentos) || [];
        if (opcoes.length === 0) {
            el.agrupamento.value = state.agrupamento;
            return;
        }
        el.agrupamento.innerHTML = '';
        opcoes.forEach(opcao => {
            const option = document.createElement('option');
            option.value = opcao.valor;
            option.textContent = opcao.rotulo;
            el.agrupamento.appendChild(option);
        });
        el.agrupamento.value = state.agrupamento;
        if (!el.agrupamento.value) {
            el.agrupamento.value = 'DIA';
            state.agrupamento = 'DIA';
        }
    }

    function atualizarResumoPeriodo() {
        const dias = Math.round((new Date(state.dataFim + 'T12:00:00') - new Date(state.dataInicio + 'T12:00:00')) / 86400000) + 1;
        el.periodoResumo.textContent = formatarDataIso(state.dataInicio) + ' a ' + formatarDataIso(state.dataFim)
            + ' · ' + dias + (dias === 1 ? ' dia' : ' dias');
    }

    function sincronizarUrl() {
        const params = new URLSearchParams();
        params.set('dataInicio', state.dataInicio);
        params.set('dataFim', state.dataFim);
        params.set('agrupamento', state.agrupamento);
        if (state.status.length > 0) {
            params.set('status', state.status.join(','));
        }
        window.history.replaceState(null, '', window.location.pathname + '?' + params.toString());
    }

    // ------------------------------------------------------------------
    // Carga de dados
    // ------------------------------------------------------------------

    async function carregarTudo(opcoes) {
        if (state.carregando) {
            return;
        }
        if (!lerFiltrosDaTela()) {
            return;
        }
        state.carregando = true;
        definirCarregando(true);
        esconderAlerta();

        const params = new URLSearchParams({
            dataInicio: state.dataInicio,
            dataFim: state.dataFim,
            agrupamento: state.agrupamento
        });
        if (state.status.length > 0) {
            params.set('status', state.status.join(','));
        }
        const query = params.toString();

        try {
            const [resumo, serie, situacao, ranking, recentes] = await Promise.all([
                apiGet('/resumo?' + query),
                apiGet('/vendas?' + query),
                apiGet('/status?' + query),
                apiGet('/produtos-mais-vendidos?' + query + '&limite=10'),
                apiGet('/pedidos-recentes?' + query + '&limite=8')
            ]);

            renderizarResumo(resumo);
            state.ultimaSerie = serie;
            renderizarGraficoVendas(serie);
            renderizarSituacao(situacao);
            renderizarRanking(ranking);
            renderizarPedidosRecentes(recentes);
            el.atualizado.textContent = formatarDataHora(resumo.atualizadoEm) || formatarDataHora(new Date().toISOString());
        } catch (erro) {
            mostrarAlerta(erro.message || 'Não foi possível carregar os dados do dashboard.', 'danger');
        } finally {
            state.carregando = false;
            definirCarregando(false);
        }
    }

    async function apiGet(caminho) {
        const resposta = await fetch(API + caminho, {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin',
            cache: 'no-store'
        });

        if (resposta.status === 401) {
            window.location.href = '/login';
            throw new Error('Sessão expirada. Faça login novamente.');
        }
        if (resposta.status === 403) {
            throw new Error('Seu perfil não tem permissão para acessar o dashboard administrativo.');
        }
        if (!resposta.ok) {
            let detalhe = '';
            try {
                const corpo = await resposta.json();
                detalhe = corpo.detalhe || corpo.erro || '';
            } catch (ignorado) {
                detalhe = '';
            }
            throw new Error(detalhe || ('Falha na consulta à API (' + resposta.status + ').'));
        }
        return resposta.json();
    }

    // ------------------------------------------------------------------
    // Renderização — KPIs
    // ------------------------------------------------------------------

    function renderizarResumo(resumo) {
        texto('dashFaturamento', moeda(resumo.faturamentoTotal));
        texto('dashTicketMedio', moeda(resumo.ticketMedio));
        texto('dashPedidos', inteiro(resumo.totalPedidos));
        texto('dashItensVendidos', inteiro(resumo.itensVendidos));
        texto('dashCancelamentos', inteiro(resumo.pedidosCancelados));

        texto('dashTicketHint', 'por pedido não cancelado (' + inteiro(resumo.pedidosNaoCancelados) + ')');
        texto('dashPedidosDetalhe', inteiro(resumo.pedidosEmAndamento) + ' em andamento · '
            + inteiro(resumo.pedidosFinalizados) + ' finalizados');
        texto('dashItensHint', 'unidades em ' + inteiro(resumo.pedidosNaoCancelados) + ' pedidos válidos');
        texto('dashCancelamentosDetalhe', 'taxa de ' + percentual(resumo.taxaCancelamento));

        const variacao = el.root.querySelector('#dashFaturamentoVar');
        const valor = Number(resumo.variacaoFaturamentoPercentual);
        variacao.classList.remove('is-up', 'is-down', 'is-neutral');
        if (!Number.isFinite(valor) || valor === 0) {
            variacao.classList.add('is-neutral');
            variacao.textContent = '—';
        } else if (valor > 0) {
            variacao.classList.add('is-up');
            variacao.textContent = '▲ ' + percentual(valor);
        } else {
            variacao.classList.add('is-down');
            variacao.textContent = '▼ ' + percentual(Math.abs(valor));
        }

        el.vendasSubtitulo.textContent = 'Faturamento e volume de pedidos · '
            + formatarDataIso(resumo.periodoInicio) + ' a ' + formatarDataIso(resumo.periodoFim);
    }

    // ------------------------------------------------------------------
    // Renderização — gráfico de vendas
    // ------------------------------------------------------------------

    function renderizarGraficoVendas(serie) {
        const pontos = serie.pontos || [];
        const semDados = !serie.totalPedidos || serie.totalPedidos === 0;
        document.getElementById('dashVendasEmpty').classList.toggle('d-none', !semDados);

        const labels = pontos.map(p => p.rotulo);
        const financeiro = state.metrica === 'faturamento';
        const dados = pontos.map(p => financeiro ? Number(p.faturamento) : Number(p.pedidos));

        const config = {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: financeiro ? 'Faturamento (R$)' : 'Pedidos',
                    data: dados,
                    backgroundColor: financeiro ? 'rgba(232, 93, 4, 0.78)' : 'rgba(37, 99, 235, 0.72)',
                    hoverBackgroundColor: financeiro ? '#c2410c' : '#1d4ed8',
                    borderWidth: 0,
                    maxBarThickness: 38
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                legend: { display: false },
                tooltips: {
                    backgroundColor: '#111827',
                    displayColors: false,
                    callbacks: {
                        label: function (item) {
                            return financeiro
                                ? 'Faturamento: ' + moeda(item.yLabel)
                                : 'Pedidos: ' + inteiro(item.yLabel);
                        }
                    }
                },
                scales: {
                    xAxes: [{
                        gridLines: { display: false, drawBorder: false },
                        ticks: {
                            autoSkip: true,
                            maxTicksLimit: 14,
                            maxRotation: 0,
                            fontColor: '#6b7280',
                            fontSize: 10
                        }
                    }],
                    yAxes: [{
                        gridLines: { color: '#eef2f7', drawBorder: false, zeroLineColor: '#e5e7eb' },
                        ticks: {
                            beginAtZero: true,
                            precision: 0,
                            fontColor: '#6b7280',
                            fontSize: 10,
                            callback: function (valor) {
                                const numero = Number(valor);
                                return financeiro ? moedaCompacta(numero) : inteiro(numero);
                            }
                        }
                    }]
                }
            }
        };
        substituirChart('vendas', 'dashChartVendas', config);
    }

    // ------------------------------------------------------------------
    // Renderização — situação dos pedidos
    // ------------------------------------------------------------------

    function renderizarSituacao(situacao) {
        const todos = situacao.status || [];
        const comPedidos = todos.filter(s => Number(s.quantidade) > 0);
        const semDados = !situacao.total || situacao.total === 0;
        document.getElementById('dashStatusEmpty').classList.toggle('d-none', !semDados);
        texto('dashStatusTotal', inteiro(situacao.total) + ' pedidos');

        const config = {
            type: 'doughnut',
            data: {
                labels: comPedidos.map(s => s.descricao),
                datasets: [{
                    data: comPedidos.map(s => Number(s.quantidade)),
                    backgroundColor: comPedidos.map(s => STATUS_COR[s.status] || '#94a3b8'),
                    borderColor: '#ffffff',
                    borderWidth: 2,
                    hoverBorderColor: '#ffffff'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutoutPercentage: 68,
                legend: { display: false },
                tooltips: {
                    backgroundColor: '#111827',
                    callbacks: {
                        label: function (item) {
                            const status = comPedidos[item.index];
                            return ' ' + item.label + ': ' + inteiro(status.quantidade)
                                + ' (' + percentual(status.percentual) + ')';
                        }
                    }
                }
            }
        };
        substituirChart('status', 'dashChartStatus', config);

        const legenda = document.getElementById('dashStatusLegenda');
        legenda.innerHTML = '';
        if (comPedidos.length === 0) {
            const vazio = document.createElement('span');
            vazio.className = 'dash-kpi-hint';
            vazio.textContent = 'Nenhum pedido no período.';
            legenda.appendChild(vazio);
            return;
        }
        comPedidos.forEach(item => {
            const linha = document.createElement('div');
            linha.className = 'dash-legend-item';

            const nome = document.createElement('span');
            nome.className = 'dash-legend-nome';
            const dot = document.createElement('span');
            dot.className = 'dash-legend-dot';
            dot.style.background = STATUS_COR[item.status] || '#94a3b8';
            const nomeTexto = document.createElement('span');
            nomeTexto.textContent = item.descricao;
            nome.appendChild(dot);
            nome.appendChild(nomeTexto);

            const valores = document.createElement('span');
            valores.className = 'dash-legend-valores';
            const quantidade = document.createElement('strong');
            quantidade.textContent = inteiro(item.quantidade);
            const valor = document.createElement('span');
            valor.textContent = moeda(item.valor);
            valores.appendChild(quantidade);
            valores.appendChild(valor);

            linha.appendChild(nome);
            linha.appendChild(valores);
            legenda.appendChild(linha);
        });
    }

    // ------------------------------------------------------------------
    // Renderização — ranking e gráfico de produtos
    // ------------------------------------------------------------------

    function renderizarRanking(ranking) {
        const produtos = ranking.produtos || [];
        const corpo = document.getElementById('dashRankingCorpo');
        corpo.innerHTML = '';
        texto('dashRankingTotal', inteiro(ranking.totalItensVendidos) + ' itens');

        if (produtos.length === 0) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 5;
            td.className = 'dash-table-empty';
            td.textContent = 'Nenhum produto vendido no período selecionado.';
            tr.appendChild(td);
            corpo.appendChild(tr);
        } else {
            produtos.forEach(produto => {
                const tr = document.createElement('tr');

                const tdPos = document.createElement('td');
                const pos = document.createElement('span');
                pos.className = 'dash-posicao' + (produto.posicao <= 3 ? ' is-top' : '');
                pos.textContent = produto.posicao;
                tdPos.appendChild(pos);

                const tdProduto = document.createElement('td');
                const nome = document.createElement('span');
                nome.className = 'dash-produto-nome';
                nome.textContent = produto.nome;
                const categoria = document.createElement('span');
                categoria.className = 'dash-produto-categoria';
                categoria.textContent = produto.categoria || 'Não informada';
                tdProduto.appendChild(nome);
                tdProduto.appendChild(categoria);

                const tdQtd = document.createElement('td');
                tdQtd.className = 'text-end dash-valor';
                tdQtd.textContent = inteiro(produto.quantidade);

                const tdFaturamento = document.createElement('td');
                tdFaturamento.className = 'text-end dash-valor';
                tdFaturamento.textContent = moeda(produto.faturamento);

                const tdShare = document.createElement('td');
                tdShare.className = 'dash-col-share';
                tdShare.appendChild(criarBarraParticipacao(produto.participacaoPercentual));

                tr.appendChild(tdPos);
                tr.appendChild(tdProduto);
                tr.appendChild(tdQtd);
                tr.appendChild(tdFaturamento);
                tr.appendChild(tdShare);
                corpo.appendChild(tr);
            });
        }

        document.getElementById('dashProdutosEmpty').classList.toggle('d-none', produtos.length > 0);
        const top = produtos.slice(0, 8);
        const config = {
            type: 'horizontalBar',
            data: {
                labels: top.map(p => p.nome),
                datasets: [{
                    label: 'Unidades vendidas',
                    data: top.map(p => Number(p.quantidade)),
                    backgroundColor: top.map((p, indice) => indice < 3 ? 'rgba(232, 93, 4, 0.82)' : 'rgba(251, 146, 60, 0.45)'),
                    hoverBackgroundColor: '#c2410c',
                    borderWidth: 0,
                    maxBarThickness: 22
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                legend: { display: false },
                tooltips: {
                    backgroundColor: '#111827',
                    displayColors: false,
                    callbacks: {
                        label: function (item) {
                            return inteiro(item.xLabel) + ' unidades';
                        }
                    }
                },
                scales: {
                    xAxes: [{
                        gridLines: { color: '#eef2f7', drawBorder: false },
                        ticks: { beginAtZero: true, precision: 0, fontColor: '#6b7280', fontSize: 10 }
                    }],
                    yAxes: [{
                        gridLines: { display: false, drawBorder: false },
                        ticks: { fontColor: '#374151', fontSize: 11 }
                    }]
                }
            }
        };
        substituirChart('produtos', 'dashChartProdutos', config);
    }

    function criarBarraParticipacao(valor) {
        const wrapper = document.createElement('div');
        wrapper.className = 'dash-share';
        const barra = document.createElement('div');
        barra.className = 'dash-share-bar';
        const preenchimento = document.createElement('span');
        const numero = Math.max(0, Math.min(100, Number(valor) || 0));
        preenchimento.style.width = numero + '%';
        barra.appendChild(preenchimento);
        const textoPercentual = document.createElement('small');
        textoPercentual.textContent = percentual(numero);
        wrapper.appendChild(barra);
        wrapper.appendChild(textoPercentual);
        return wrapper;
    }

    // ------------------------------------------------------------------
    // Renderização — pedidos recentes
    // ------------------------------------------------------------------

    function renderizarPedidosRecentes(pedidos) {
        const corpo = document.getElementById('dashRecentesCorpo');
        corpo.innerHTML = '';
        if (!pedidos || pedidos.length === 0) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 7;
            td.className = 'dash-table-empty';
            td.textContent = 'Nenhum pedido encontrado para os filtros selecionados.';
            tr.appendChild(td);
            corpo.appendChild(tr);
            return;
        }

        pedidos.forEach(pedido => {
            const tr = document.createElement('tr');

            const tdId = document.createElement('td');
            const codigo = document.createElement('strong');
            codigo.textContent = '#' + String(pedido.id || '').slice(-8);
            codigo.title = pedido.id || '';
            tdId.appendChild(codigo);

            const tdData = document.createElement('td');
            tdData.textContent = pedido.dataEntrada || '—';

            const tdCliente = document.createElement('td');
            const cliente = document.createElement('span');
            cliente.className = 'dash-cliente';
            cliente.textContent = pedido.clienteNome || 'Não informado';
            cliente.title = pedido.clienteNome || '';
            tdCliente.appendChild(cliente);

            const tdStatus = document.createElement('td');
            const badge = document.createElement('span');
            const statusClasse = String(pedido.status || '').toLowerCase().replace(/_/g, '-');
            badge.className = 'dash-status-badge st-' + statusClasse;
            badge.textContent = pedido.statusDescricao || STATUS_LABEL[pedido.status] || pedido.status || '—';
            tdStatus.appendChild(badge);

            const tdItens = document.createElement('td');
            tdItens.className = 'text-end';
            tdItens.textContent = inteiro(pedido.quantidadeItens);

            const tdTotal = document.createElement('td');
            tdTotal.className = 'text-end dash-valor';
            tdTotal.textContent = moeda(pedido.total);

            const tdAcao = document.createElement('td');
            tdAcao.className = 'text-end';
            const link = document.createElement('a');
            link.className = 'btn btn-link';
            link.href = '/pedidos/' + encodeURIComponent(pedido.id || '');
            link.textContent = 'Abrir';
            tdAcao.appendChild(link);

            tr.appendChild(tdId);
            tr.appendChild(tdData);
            tr.appendChild(tdCliente);
            tr.appendChild(tdStatus);
            tr.appendChild(tdItens);
            tr.appendChild(tdTotal);
            tr.appendChild(tdAcao);
            corpo.appendChild(tr);
        });
    }

    // ------------------------------------------------------------------
    // Gráficos / utilidades
    // ------------------------------------------------------------------

    function substituirChart(chave, canvasId, config) {
        if (charts[chave]) {
            charts[chave].destroy();
        }
        const canvas = document.getElementById(canvasId);
        if (!canvas) {
            return;
        }
        charts[chave] = new Chart(canvas.getContext('2d'), config);
    }

    function configurarAutoRefresh() {
        if (window.__dashAutoTimer) {
            clearInterval(window.__dashAutoTimer);
            window.__dashAutoTimer = null;
        }
        localStorage.setItem('dashAuto', state.auto ? '1' : '0');
        el.auto.checked = state.auto;
        if (state.auto) {
            window.__dashAutoTimer = setInterval(() => {
                if (!document.hidden && !state.carregando) {
                    carregarTudo();
                }
            }, AUTO_REFRESH_MS);
        }
    }

    function definirCarregando(carregando) {
        el.root.classList.toggle('dash-loading', carregando);
        el.aplicar.disabled = carregando;
        el.atualizar.disabled = carregando;
        el.atualizar.setAttribute('aria-busy', String(carregando));
    }

    function mostrarAlerta(mensagem, tipo) {
        el.alerta.className = 'alert alert-' + (tipo || 'danger');
        el.alerta.textContent = mensagem;
        el.alerta.classList.remove('d-none');
    }

    function esconderAlerta() {
        el.alerta.classList.add('d-none');
    }

    function texto(id, valor) {
        const elemento = document.getElementById(id);
        if (elemento) {
            elemento.textContent = valor;
        }
    }

    function moeda(valor) {
        return new Intl.NumberFormat('pt-BR', {
            style: 'currency',
            currency: 'BRL'
        }).format(Number(valor) || 0);
    }

    function moedaCompacta(valor) {
        const numero = Number(valor) || 0;
        if (Math.abs(numero) >= 1000) {
            return 'R$ ' + (numero / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + ' mil';
        }
        return 'R$ ' + numero.toLocaleString('pt-BR', { maximumFractionDigits: 0 });
    }

    function inteiro(valor) {
        return Number(valor || 0).toLocaleString('pt-BR', { maximumFractionDigits: 0 });
    }

    function percentual(valor) {
        const numero = Number(valor) || 0;
        return numero.toLocaleString('pt-BR', {
            minimumFractionDigits: 0,
            maximumFractionDigits: 2
        }) + '%';
    }

    function isoLocal(data) {
        const ano = data.getFullYear();
        const mes = String(data.getMonth() + 1).padStart(2, '0');
        const dia = String(data.getDate()).padStart(2, '0');
        return ano + '-' + mes + '-' + dia;
    }

    function somarDias(data, dias) {
        const resultado = new Date(data);
        resultado.setDate(resultado.getDate() + dias);
        return resultado;
    }

    function formatarDataIso(valor) {
        if (!valor) {
            return '—';
        }
        const partes = String(valor).slice(0, 10).split('-');
        if (partes.length !== 3) {
            return valor;
        }
        return partes[2] + '/' + partes[1] + '/' + partes[0];
    }

    function formatarDataHora(valor) {
        if (!valor) {
            return '';
        }
        if (valor instanceof Date) {
            return valor.toLocaleString('pt-BR', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        }
        const textoValor = String(valor);
        const data = textoValor.slice(0, 10);
        const hora = textoValor.includes('T') ? textoValor.split('T')[1].slice(0, 5) : '';
        const dataFormatada = formatarDataIso(data);
        return hora ? dataFormatada + ' ' + hora : dataFormatada;
    }

    function filtrosFallback() {
        return {
            periodoPadraoDias: 30,
            status: Object.keys(STATUS_LABEL).map(v => ({ valor: v, rotulo: STATUS_LABEL[v] })),
            agrupamentos: [
                { valor: 'DIA', rotulo: 'Diário' },
                { valor: 'SEMANA', rotulo: 'Semanal' },
                { valor: 'MES', rotulo: 'Mensal' }
            ]
        };
    }
})();
