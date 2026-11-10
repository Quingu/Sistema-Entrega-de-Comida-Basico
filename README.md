# Pediu Patrão — Pizzaria

## Execução
```powershell
# 1) Configure o MongoDB via ambiente (veja application.properties.example)
$env:MONGODB_URI="mongodb+srv://<usuario>:<senha>@<cluster>/..."
$env:MONGODB_DATABASE="pizzaria"
$env:DEMO_DATA="true"   # opcional: popula dados demo se o banco estiver vazio

# 2) Rode com JDK 21+
$env:JAVA_HOME="C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr"
.\mvnw.cmd spring-boot:run
# ou: .\mvnw.cmd test
```
App: `http://localhost:8080` · Login em `/login` · Logout via POST `/logout` (com CSRF).

## Perfis de demonstração
| usuário | senha | perfil |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `gerente` | `gerente123` | GERENTE |
| `atendente` | `atendente123` | ATENDENTE |

`USER` foi removido. `UsuarioDetailsService` usa o perfil realmente salvo (`ROLE_ADMIN/GERENTE/ATENDENTE`).

## Dashboard administrativo

Painel somente leitura para **ADMIN** e **GERENTE** em `http://localhost:8080/dashboard`, com dados
agregados no servidor a partir dos pedidos do MongoDB.

| Requisito | Entrega |
|---|---|
| RF01 indicadores | Faturamento total/finalizado, ticket médio, pedidos por situação, taxa de cancelamento, itens vendidos e variação vs. período anterior |
| RF02 gráficos | Série por `DIA`, `SEMANA` ou `MES`, com alternância entre faturamento e pedidos |
| RF03 situação | Distribuição por status (rosca + legenda com quantidade e valor) |
| RF04 ranking | Top produtos por quantidade, com faturamento e participação |
| RF05 filtros | Período, status, agrupamento e limite; atalhos Hoje/7/30/Este mês; estado na URL |
| RF06 API | Seis endpoints `GET /api/dashboard/**` consumidos via `fetch` |
| RF07 atualização | Botão Atualizar, auto-refresh de 60 s (pausa em aba oculta) e estados de erro/vazio/sessão expirada |

### Endpoints

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/dashboard/resumo` | Indicadores gerais |
| GET | `/api/dashboard/vendas` | Série de vendas (`agrupamento=DIA\|SEMANA\|MES`) |
| GET | `/api/dashboard/status` | Situação dos pedidos |
| GET | `/api/dashboard/produtos-mais-vendidos` | Ranking (`limite` 1–50) |
| GET | `/api/dashboard/pedidos-recentes` | Últimos pedidos (`limite` 1–50) |
| GET | `/api/dashboard/filtros` | Datas limites e opções para a interface |

Parâmetros comuns: `dataInicio`, `dataFim` (`yyyy-MM-dd`), `status` (CSV), `agrupamento` e `limite`.
O período padrão é de 30 dias e o máximo é de 731 dias. Pedidos `CANCELADO` não entram em
faturamento, ticket médio, série nem ranking, mas continuam visíveis na situação dos pedidos.
Contrato completo, exemplos e erros em [`docs/API.md`](docs/API.md).

### Configuração

```properties
# src/main/resources/application.properties (ignorado pelo Git; use o exemplo)
spring.data.mongodb.uri=${MONGODB_URI:mongodb://127.0.0.1:27017/pizzaria_dashboard}
app.demo-data.enabled=${DEMO_DATA:true}
```

- `application.properties.example` documenta todas as chaves; use `DEMO_DATA=false` para iniciar
  com o banco limpo.
- Com `DEMO_DATA=true`, usuários, produtos, clientes e ~60 dias de pedidos são criados **apenas
  quando as coleções estão vazias**.

### Documentação e evidências

- [`docs/REQUISITOS.md`](docs/REQUISITOS.md) — requisitos funcionais/não funcionais e critérios de aceite.
- [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) — arquitetura, fluxo de dados, índices e GitFlow.
- [`docs/API.md`](docs/API.md) — contrato dos endpoints.
- [`docs/TESTES.md`](docs/TESTES.md) — matriz de testes e resultados.
- [`docs/evidencias/`](docs/evidencias/) — respostas reais (200/400/403) geradas por
  `scripts/evidencias-api.ps1`.
- [`docs/postman/`](docs/postman/) — coleção para importar no Postman.

## Decisões de segurança
- **Cadastro público de usuários FECHADO**: `POST /api/usuarios` e `/usuarios/**` exigem `ADMIN`. Sem isso qualquer visitante virava admin.
- **CSRF REATIVADO** para Thymeleaf (`CookieCsrfTokenRepository` + `<input th:name="${_csrf...}">` em todo POST de tela). `/api/**` ignora CSRF só para testes automatizados com sessão — a **autorização por perfil continua valendo**.
- **Dupla trava**: `SecurityConfig` (regras de rota) **+** `@PreAuthorize` nos controllers **+** checagem no service (`exigirGerente()` lança `AccessDeniedException`). Esconder botão na UI é só complemento (`sec:authorize` no sidebar/detalhe).
- **Anti auto-elevação**: ADMIN não troca o próprio perfil nem exclui a própria conta; senha nunca volta em resposta nem vai para auditoria (`[OCULTO]`).
- **Mongo via env**: `spring.data.mongodb.uri=${MONGODB_URI:...}`. A URI antiga com senha foi removida do `application.properties`.

### Matriz (backend)
- **ADMIN**: usuários (`/api/usuarios`, `/usuarios/**`), produtos (escrita), consulta pedidos, auditoria (só leitura).
- **GERENTE**: transições (`PUT /api/pedidos/{id}/status`), desconto (`POST .../desconto`), cancelamento (`POST .../cancelar`), consulta clientes/pedidos/auditoria. Não cria usuário/produto.
- **ATENDENTE**: cria pedido (`POST /api/pedidos`), cadastra/altera cliente, consulta andamento. **Não** aplica desconto, **não** cancela, **não** muda status, **não** vê auditoria/usuários.

## Pedidos (servidor manda, navegador obedece)
- **Pedido**: `itens[{produtoId,nomeProduto,quantidade,precoUnitario,subtotal}]`, `subtotal`, `percentualDesconto`, `valorDesconto`, `totalFinal` (BigDecimal, 2 casas), `status`, `usuarioResponsavel`, `dataEntrada`, `dataSaida`, `responsavelSaida`, `justificativaCancelamento`.
- `PedidoCreateDTO` **não tem total** — `PedidoService.criar()` busca cada produto ativo e recalcula. Preço congelado no item (momento da venda).
- **DTOs**: `PedidoCreateDTO`, `ItemPedidoDTO`, `StatusDTO`, `DescontoDTO` (0–20%), `CancelamentoDTO` (justificativa 5–500).
- Erros claros via `GlobalExceptionHandler`: transição inválida (422), desconto acima do limite (422), cancelamento indevido (422), acesso negado (403), validação (400).

## Fluxo obrigatório (`StatusPedido`)
`RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA/RETIRADO → FINALIZADO`, mais `CANCELADO` (só antes da saída). Saltos, transição após cancelado/finalizado e finalização fora da sequência retornam 422. Saída/retirada carimba `dataSaida + responsavelSaida` obrigatoriamente.

## Desconto e cancelamento (só GERENTE)
- Desconto: 0–20%, só antes da saída, recálculo servidor.
- Cancelamento: justificativa obrigatória, só antes da saída, pedido **preservado** (nunca `delete`), sem mudanças posteriores (estado terminal).

## Auditoria (`auditorias`)
`Auditoria(info: dataHora, usuario, perfil, operacao, entidade, entidadeId, camposAlterados, valorAnterior, valorNovo, justificativa)` + `AuditoriaRepository` + `AuditoriaService`. Audita: criação/alteração de pedido, cada status, desconto, cancelamento, cliente (foco telefone/endereço), produto e usuário. `GET /api/auditoria` e `/auditoria` só ADMIN/GERENTE; **sem** PUT/DELETE. Senhas/tokens sanitizados.

## Requisitos atendidos (checklist da rubrica)
1. Base: Mongo por env, sem `USER` fixo, sem cadastro público, CSRF ligado. ✔
2. Autorização: ADMIN/GERENTE/ATENDENTE com rota + `@PreAuthorize` + anti-elevação. ✔
3. Pedido com itens, BigDecimal, DTOs, cálculo servidor. ✔
4. Enum + máquina centralizada + hora/responsável na entrada e saída. ✔
5. Desconto/cancel só GERENTE, limites e justificativa, pedido preservado. ✔
6. Auditoria persistente, só consulta ADMIN/GERENTE, sem credenciais. ✔
7. Telas novas (`pedidos/novo`, `pedidos/detalhe`, `auditoria/lista`), CSRF em cada POST, erros claros, `sec:authorize` só como complemento. ✔
8. Testes: `PedidoServiceTest` (15), `SegurancaPedidoTest` (8), `AuditoriaClienteTest` (4), `DashboardServiceTest` (14), `DashboardControllerTest` (5), `DashboardSecurityTest` (4) — 50 testes, todos passando. ✔
9. Consulta da auditoria como GERENTE/ADMIN (criação, status, desconto, cancel, cliente). ✔
10. Dashboard administrativo (RF01–RF07): API própria, agregações no servidor, filtros, gráficos, auto-refresh e evidências versionadas. ✔
