# Testes e evidências — Dashboard administrativo

Este documento consolida a estratégia de testes, a matriz de casos automatizados e as
evidências de execução da API do dashboard. Os artefatos brutos estão em
[`docs/evidencias/`](evidencias/) e o contrato completo em [`docs/API.md`](API.md).

## 1. Como reproduzir

Pré-requisitos: JDK 21+, Maven Wrapper (já no projeto) e MongoDB acessível.

```powershell
# Testes automatizados
$env:JAVA_HOME="C:\caminho\para\jdk-21"
.\mvnw.cmd test

# Aplicação local (Mongo em 127.0.0.1:27017 e dados demo habilitados)
$env:MONGODB_URI="mongodb://127.0.0.1:27017/pizzaria_dashboard"
$env:DEMO_DATA="true"
.\mvnw.cmd spring-boot:run
```

```powershell
# Evidências HTTP (login real, chamadas reais e tentativas de erro)
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\evidencias-api.ps1
```

A página fica em `http://localhost:8080/dashboard` e exige `ADMIN` ou `GERENTE`
(use `gerente` / `gerente123` nos dados demo).

## 2. Estratégia

| Camada | Ferramenta | Objetivo |
|---|---|---|
| Unidade | JUnit 5 + Mockito | Validar agregações, regras de período e exclusão de cancelados |
| Contrato HTTP | `@WebMvcTest` + MockMvc | Garantir status, JSON e conversão de parâmetros |
| Segurança | `@WebMvcTest` + `spring-security-test` | Garantir a matriz ADMIN/GERENTE/ATENDENTE/anônimo |
| Integração manual | Script PowerShell + app real + MongoDB | Evidenciar respostas 200, 400 e 403 contra dados persistidos |

Os testes de unidade usam repositórios mockados: nenhum teste automatizado depende de
MongoDB em execução. A integração real é registrada em arquivos JSON versionados.

## 3. Resultado da execução automatizada

Execução de referência em **10/11/2026**, Java 21.0.13, Spring Boot 3.4.4, Maven Wrapper.

```
[INFO] Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| Classe de teste | Testes | Foco |
|---|---:|---|
| `DashboardServiceTest` | 14 | Agregações e validações do serviço |
| `DashboardControllerTest` | 5 | Contrato HTTP da API |
| `DashboardSecurityTest` | 4 | Autorização por perfil |
| `PedidoServiceTest` | 15 | Regras de pedido (regressão do núcleo) |
| `SegurancaPedidoTest` | 8 | Autorização de pedido (regressão) |
| `AuditoriaClienteTest` | 4 | Auditoria de cliente (regressão) |
| **Total** | **50** | **100% de sucesso** |

## 4. Matriz de casos — dashboard

### 4.1 `DashboardServiceTest` (14)

| Caso | O que verifica |
|---|---|
| `resumoCalculaIndicadoresEDesconsideraCanceladosNoFaturamento` | Faturamento, ticket médio, itens e taxa de cancelamento ignoram `CANCELADO`; contagens por situação permanecem corretas |
| `filtroDeStatusRestringeTodosOsIndicadores` | Filtro CSV de status aplica-se a resumo, série, ranking e recentes |
| `serieDiariaPreencheDiasSemVendaComZeroEIgnoraCancelados` | Série diária contínua, dias vazios com zero e cancelados fora do faturamento |
| `serieMensalAgrupaPeriodoLongo` | Agrupamento `MES` consolida corretamente períodos longos |
| `situacaoContaTodosOsStatusEPercentuais` | Todos os status aparecem, com quantidade, valor e percentual somando 100% |
| `rankingAgregaItensPorProdutoEOrdenaPorQuantidade` | Agregação por produto, ordenação por quantidade e desempate por faturamento |
| `rankingRespeitaLimite` | Limite do ranking é aplicado após a ordenação |
| `pedidosRecentesOrdenaPorDataENomeiaCliente` | Ordenação decrescente por data e resolução do nome do cliente |
| `filtrosRetornaDatasLimitesEOpcoes` | Datas mínima/máxima, opções de status, agrupamentos e períodos rápidos |
| `periodoInvertidoGeraErroDeValidacao` | `dataInicio > dataFim` → erro de negócio (400) |
| `periodoAcimaDoLimiteGeraErroDeValidacao` | Período acima de 731 dias → erro de negócio (400) |
| `statusInvalidoGeraErroDeValidacao` | Status fora do enum → erro de negócio (400) |
| `limiteInvalidoGeraErroDeValidacao` | Limite fora de 1–50 → erro de negócio (400) |
| `bancoVazioRetornaIndicadoresZeradosSemErro` | Ausência de pedidos retorna zeros e listas vazias, sem exceção |

### 4.2 `DashboardControllerTest` (5)

| Caso | O que verifica |
|---|---|
| `resumoDevolveJsonComIndicadores` | `GET /resumo` retorna 200 e o JSON do DTO |
| `filtrosDevolveOpcoesParaInterface` | `GET /filtros` retorna 200 com opções para a tela |
| `agrupamentoEhConvertidoParaEnum` | `agrupamento=SEMANA` é convertido para o enum |
| `dataInvalidaRetornaErro400ComMensagemClara` | Data malformada → 400 com mensagem objetiva |
| `erroDeRegraDeNegocioRetorna400` | Exceção de negócio → 400 no formato `{erro, detalhe}` |

### 4.3 `DashboardSecurityTest` (4)

| Caso | O que verifica |
|---|---|
| `gerenteAcessaApiDoDashboard` | GERENTE recebe 200 e `application/json` |
| `adminAcessaApiDoDashboard` | ADMIN recebe 200 |
| `atendenteNaoAcessaApiDoDashboard` | ATENDENTE recebe 403 (corpo JSON, sem HTML) |
| `anonimoEhRedirecionadoParaLogin` | Sem sessão, a API redireciona para `/login` |

## 5. Evidências de integração real

Geradas por [`scripts/evidencias-api.ps1`](../scripts/evidencias-api.ps1) contra a aplicação
em execução e o MongoDB local com dados demo (79 pedidos, 71 não cancelados).

| # | Requisição | Status | Arquivo |
|---|---|---|---|
| 1 | `GET /api/dashboard/resumo?dataInicio=2026-09-01&dataFim=2026-11-10` | 200 | [`01-resumo.json`](evidencias/01-resumo.json) |
| 2 | `GET /api/dashboard/vendas?...&agrupamento=SEMANA` | 200 | [`02-vendas-semana.json`](evidencias/02-vendas-semana.json) |
| 3 | `GET /api/dashboard/status?...` | 200 | [`03-status.json`](evidencias/03-status.json) |
| 4 | `GET /api/dashboard/produtos-mais-vendidos?...&limite=10` | 200 | [`04-produtos-mais-vendidos.json`](evidencias/04-produtos-mais-vendidos.json) |
| 5 | `GET /api/dashboard/pedidos-recentes?...&limite=8` | 200 | [`05-pedidos-recentes.json`](evidencias/05-pedidos-recentes.json) |
| 6 | `GET /api/dashboard/filtros` | 200 | [`06-filtros.json`](evidencias/06-filtros.json) |
| 7 | `GET /api/dashboard/resumo?dataInicio=2026-11-10&dataFim=2026-11-01` | 400 | [`07-erro-periodo-invertido.json`](evidencias/07-erro-periodo-invertido.json) |
| 8 | `GET /api/dashboard/resumo` como `atendente` | 403 | [`08-acesso-negado.json`](evidencias/08-acesso-negado.json) |

Resumo consolidado: [`docs/evidencias/RESUMO.md`](evidencias/RESUMO.md).

### 5.1 Números conferidos na evidência

- 79 pedidos no período, 71 não cancelados, 63 finalizados, 8 cancelados.
- Faturamento total `9.843,87`; faturamento finalizado `8.689,17`; ticket médio `138,65`.
- Taxa de cancelamento `10,13%`; 294 itens vendidos.
- Produto líder: **Porção de Batata**, 50 unidades e `17,01%` dos itens.
- Série semanal contínua, incluindo a primeira semana sem vendas (`0,00`).
- Erro 400 com `{"erro":"Requisição inválida.","detalhe":"A data inicial não pode ser posterior à data final."}`.
- Erro 403 com corpo JSON `{"erro":"Acesso negado.", ...}`, sem redirecionar para HTML.

## 6. Verificação manual da interface

| Verificação | Resultado |
|---|---|
| Login como `gerente` e acesso a `/dashboard` | OK |
| Cartões de indicadores com dados do período padrão (30 dias) | OK |
| Gráfico de linha com alternância faturamento/pedidos | OK |
| Gráfico de rosca por status e legenda com valores | OK |
| Ranking com tabela, barras de participação e gráfico | OK |
| Filtros de data, status, agrupamento e limite | OK, com persistência na URL |
| Períodos rápidos (Hoje, 7, 30, Este mês) e botão Limpar | OK |
| Atualização manual e automática (60 s) | OK |
| Acesso como `atendente` | Bloqueado com mensagem de permissão |
| Layout responsivo (desktop/tablet/celular) | OK |

## 7. Limitações conhecidas

- Os testes automatizados mockam o MongoDB; a validação de índices e planos de consulta é
  manual (`db.pedidos.getIndexes()`).
- A captura de tela da interface não é versionada para não inflar o repositório; a
  verificação é descrita na seção 6 e reproduzível com a aplicação local.
- A evidência considera dados demo determinísticos apenas quanto ao volume e período; os
  identificadores do MongoDB mudam a cada recarga do banco.
