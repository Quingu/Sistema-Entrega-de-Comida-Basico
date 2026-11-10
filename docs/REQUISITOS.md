# Requisitos — Dashboard Administrativo Pediu Patrão

## 1. Visão geral

O dashboard administrativo é uma visão somente leitura do sistema de pedidos **Pediu Patrão**. Ele permite que ADMIN e GERENTE acompanhem faturamento, volume de pedidos, situação operacional, produtos mais vendidos e tendências por período, usando os pedidos persistidos no MongoDB.

A página consome uma API REST própria (`/api/dashboard/**`), implementada neste projeto. Não há cálculo de indicadores no navegador: o servidor é a fonte da verdade.

## 2. Requisitos funcionais

### RF01 — Indicadores gerais de vendas

**Descrição:** exibir cartões com os principais números do período filtrado.

**Campos obrigatórios:**

| Indicador | Regra |
|---|---|
| Faturamento total | Soma de `totalFinal` dos pedidos não cancelados no período |
| Faturamento finalizado | Soma de `totalFinal` apenas de pedidos `FINALIZADO` |
| Ticket médio | Faturamento total ÷ pedidos não cancelados |
| Total de pedidos | Todos os pedidos do período, inclusive cancelados |
| Pedidos em andamento | Pedidos que não estão `FINALIZADO` nem `CANCELADO` |
| Pedidos finalizados | Pedidos com status `FINALIZADO` |
| Pedidos cancelados | Pedidos com status `CANCELADO` |
| Taxa de cancelamento | Cancelados ÷ total de pedidos × 100 |
| Itens vendidos | Soma das quantidades dos itens de pedidos não cancelados |
| Variação do faturamento | Comparação com o período anterior de mesmo tamanho |

**Critérios de aceite:**

- CA01.1 — Cancelados não entram no faturamento, ticket médio, itens vendidos nem ranking.
- CA01.2 — A variação é calculada comparando o período atual com o período imediatamente anterior de mesma duração.
- CA01.3 — Valores monetários são retornados com duas casas decimais e formatados em BRL na interface.

### RF02 — Análise de vendas (gráficos)

**Descrição:** apresentar a evolução das vendas em gráfico, com granularidade selecionável.

**Regras:**

- Agrupamentos: `DIA`, `SEMANA` (segunda a domingo) e `MES`.
- Cada ponto contém faturamento, quantidade de pedidos e itens vendidos.
- Dias/semanas/meses sem venda são preenchidos com zero para manter a série contínua.
- O usuário pode alternar a métrica do gráfico entre faturamento e pedidos.
- A série exclui pedidos cancelados.

**Critérios de aceite:**

- CA02.1 — O gráfico é atualizado ao mudar o período, o status ou o agrupamento.
- CA02.2 — O eixo de faturamento usa formatação monetária compacta; o de pedidos usa números inteiros.
- CA02.3 — Períodos sem dados exibem estado vazio amigável, sem quebrar a tela.

### RF03 — Situação dos pedidos

**Descrição:** exibir a distribuição dos pedidos por status.

**Regras:**

- Contar todos os status: `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO`, `CANCELADO`.
- Para cada status, retornar quantidade, percentual sobre o total e valor total dos pedidos.
- Exibir totais de em andamento, finalizados e cancelados.
- Gráfico de rosca + legenda com valores.

**Critérios de aceite:**

- CA03.1 — A soma das quantidades por status é igual ao total de pedidos do filtro.
- CA03.2 — Status sem pedidos aparecem com zero na API e são omitidos da legenda visual.
- CA03.3 — Clicar em um status na legenda não é obrigatório; o filtro de status fica no painel de filtros.

### RF04 — Produtos mais vendidos

**Descrição:** ranking dos produtos com maior volume de vendas no período.

**Regras:**

- Agregar itens por `produtoId` (fallback para nome quando o id não existe).
- Ordenar por quantidade decrescente, depois por faturamento decrescente.
- Retornar posição, nome, categoria, quantidade, faturamento e participação percentual.
- Limite configurável entre 1 e 50 (padrão 10).
- Exibir tabela com barra de participação e gráfico de barras horizontais.

**Critérios de aceite:**

- CA04.1 — Pedidos cancelados não entram no ranking.
- CA04.2 — A participação é calculada sobre o total de itens vendidos no período.
- CA04.3 — Produtos removidos do cadastro continuam aparecendo pelo nome congelado no item do pedido.

### RF05 — Filtros de consulta

**Descrição:** permitir recortes da operação.

**Filtros disponíveis:**

| Filtro | Formato | Padrão |
|---|---|---|
| Data inicial | `yyyy-MM-dd` | Hoje − 29 dias |
| Data final | `yyyy-MM-dd` | Hoje |
| Status | CSV (`FINALIZADO,CANCELADO`) | Todos |
| Agrupamento | `DIA`, `SEMANA`, `MES` | `DIA` |
| Limite do ranking | Inteiro 1–50 | 10 |

**Regras adicionais:**

- Período máximo de 731 dias.
- Data inicial não pode ser posterior à final.
- Períodos rápidos na interface: Hoje, 7 dias, 30 dias, Este mês.
- Os filtros são refletidos na URL (`?dataInicio=...&dataFim=...&status=...`) para permitir compartilhar/recarregar a visão.
- O status selecionado é aplicado a todos os endpoints.

**Critérios de aceite:**

- CA05.1 — Filtros inválidos retornam HTTP 400 com mensagem clara.
- CA05.2 — Limpar filtros volta para os últimos 30 dias e todos os status.
- CA05.3 — A página mantém o estado dos filtros ao atualizar os dados.

### RF06 — Integração com a API

**Descrição:** o front-end consome exclusivamente a API implementada, que por sua vez consulta o MongoDB via repositórios Spring Data.

**Endpoints:**

- `GET /api/dashboard/resumo`
- `GET /api/dashboard/vendas`
- `GET /api/dashboard/status`
- `GET /api/dashboard/produtos-mais-vendidos`
- `GET /api/dashboard/pedidos-recentes`
- `GET /api/dashboard/filtros`

**Critérios de aceite:**

- CA06.1 — Todas as chamadas usam `fetch` com `credentials: same-origin`.
- CA06.2 — A API exige sessão autenticada com perfil `ADMIN` ou `GERENTE`.
- CA06.3 — Respostas são JSON e os erros seguem o formato `{ "erro": "...", "detalhe": "..." }`.
- CA06.4 — A consulta ao banco é limitada pelo período (`findByDataEntradaBetween`), usando índice em `dataEntrada`.

### RF07 — Atualização e tratamento de dados

**Descrição:** manter a visão atualizada e tratar falhas/vazios.

**Regras:**

- Botão **Atualizar** executa nova carga manual.
- Atualização automática opcional a cada 60 segundos, persistida em `localStorage`; pausa quando a aba está oculta.
- As cinco consultas são executadas em paralelo (`Promise.all`).
- Estados de carregamento, erro, vazio e sessão expirada.
- Sessão expirada (401) redireciona para `/login`.
- Acesso negado (403) exibe mensagem de permissão.
- Dados ausentes ou nulos não quebram os cálculos (valores tratados como zero).

**Critérios de aceite:**

- CA07.1 — A data/hora da última atualização é exibida.
- CA07.2 — Erros da API são exibidos em alerta, sem apagar os últimos dados válidos da tela.
- CA07.3 — A tela é responsiva e funcional em desktop, tablet e celular.

## 3. Requisitos não funcionais

| Código | Requisito | Como foi atendido |
|---|---|---|
| RNF01 | Segurança e autorização | Rotas e métodos protegidos por `SecurityConfig` + `@PreAuthorize`; API somente leitura; dashboard restrito a ADMIN/GERENTE |
| RNF02 | Desempenho | Consulta filtrada por intervalo no MongoDB; índices em `dataEntrada` e `status`; limite de 731 dias e 50 itens no ranking; chamadas paralelas no front |
| RNF03 | Integridade dos dados | Nenhum endpoint de escrita; cálculos server-side; cancelados preservados e excluídos apenas das métricas de venda |
| RNF04 | Usabilidade | Layout responsivo, filtros rápidos, estados de carregamento/erro/vazio, formatação pt-BR e BRL |
| RNF05 | Acessibilidade | Labels associados aos campos, `aria-label` nos gráficos, botões com texto, contraste e navegação por teclado |
| RNF06 | Manutenibilidade | Separação controller/service/DTO, records para contratos, agregações isoladas e testáveis |
| RNF07 | Testabilidade | Testes de unidade das agregações, testes de contrato HTTP e testes de segurança por perfil |
| RNF08 | Portabilidade | Configuração por variáveis de ambiente (`MONGODB_URI`, `MONGODB_DATABASE`, `PORT`) |
| RNF09 | Compatibilidade | Java 21, Spring Boot 3.4, MongoDB 6+ e navegadores modernos |
| RNF10 | Confiabilidade | Endpoints idempotentes (GET), validação de parâmetros e tratamento centralizado de exceções |
| RNF11 | Privacidade | A API não expõe senhas, tokens ou dados sensíveis de usuários |
| RNF12 | Documentação | Requisitos, arquitetura, contrato da API, coleção Postman e evidências de teste no repositório |

## 4. Regras de negócio consolidadas

1. **Pedido cancelado é informação, não venda.** Aparece em situação/cancelamentos, mas não em faturamento, ticket médio, série de vendas nem ranking.
2. **Período é inclusivo** nas duas extremidades (`00:00:00` da data inicial até `23:59:59.999999999` da final).
3. **Status inválido** na query string retorna 400, nunca é ignorado silenciosamente.
4. **Faturamento** considera pedidos não cancelados, inclusive os que ainda estão em andamento; o card secundário mostra o valor efetivamente finalizado.
5. **Ticket médio** usa somente pedidos não cancelados como denominador.
6. **Variação** usa o período anterior de mesma duração; se não houve faturamento anterior, retorna 100% quando há faturamento atual e 0% quando ambos são zero.
7. **Ranking** usa o nome congelado no item do pedido, preservando o histórico mesmo se o produto for renomeado/excluído.
