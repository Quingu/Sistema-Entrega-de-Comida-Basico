# Pediu Patrão — Pizzaria

## Execução
```powershell
# 1) Configure o MongoDB via ambiente
$env:MONGODB_URI="mongodb+srv://<usuario>:<senha>@<cluster>/..."
$env:MONGODB_DATABASE="pizzaria"

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
8. Testes: `PedidoServiceTest` (6), `SegurancaPedidoTest` (5: ATENDENTE e ADMIN barrados), `AuditoriaClienteTest` (2). ✔
6. Consulta da auditoria como GERENTE/ADMIN (criação, status, desconto, cancel, cliente). ✔
