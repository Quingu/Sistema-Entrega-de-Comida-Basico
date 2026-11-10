# API do Dashboard — Pediu Patrão

## 1. Convenções

- **Base URL local:** `http://localhost:8080`
- **Prefixo:** `/api/dashboard`
- **Formato:** `application/json; charset=UTF-8`
- **Autenticação:** sessão de formulário (`JSESSIONID`). É necessário estar autenticado com perfil **ADMIN** ou **GERENTE**.
- **CSRF:** os endpoints são `GET` (somente leitura), portanto não exigem token CSRF.
- **Datas:** `yyyy-MM-dd` para filtros; datas/horas de resposta em ISO-8601 local (`yyyy-MM-ddTHH:mm:ss`).
- **Valores monetários:** `BigDecimal` serializado como número JSON com duas casas.
- **Período padrão:** últimos 30 dias, incluindo hoje.
- **Período máximo:** 731 dias.
- **Pedidos cancelados:** não entram em faturamento, ticket médio, série de vendas nem ranking. Permanecem em situação e cancelamentos.

### Matriz de acesso

| Perfil | Dashboard | API |
|---|---|---|
| ADMIN | ✅ | ✅ |
| GERENTE | ✅ | ✅ |
| ATENDENTE | ❌ (403) | ❌ (403) |
| Anônimo | redireciona para `/login` | 302 para `/login` |

## 2. Parâmetros comuns

| Parâmetro | Tipo | Obrigatório | Descrição | Exemplo |
|---|---|---|---|---|
| `dataInicio` | date | Não | Início do período inclusivo | `2026-11-01` |
| `dataFim` | date | Não | Fim do período inclusivo | `2026-11-10` |
| `status` | string CSV | Não | Um ou mais status separados por vírgula | `FINALIZADO,CANCELADO` |
| `agrupamento` | enum | Não | `DIA`, `SEMANA` ou `MES` (usado em `/vendas`) | `SEMANA` |
| `limite` | int | Não | 1–50; usado em ranking e pedidos recentes | `10` |

Sem filtros, o servidor usa `dataFim = hoje` e `dataInicio = hoje - 29 dias`.

## 3. Formato de erro

```json
{
  "erro": "Requisição inválida.",
  "detalhe": "A data inicial não pode ser posterior à data final."
}
```

Erros de conversão de parâmetro:

```json
{
  "erro": "Parâmetro inválido.",
  "detalhe": "O parâmetro 'dataInicio' deve ser um LocalDate."
}
```

Acesso negado (perfil ATENDENTE ou sem sessão válida para o recurso):

```json
{
  "erro": "Acesso negado.",
  "detalhe": "O perfil autenticado não tem permissão para acessar este recurso."
}
```

| HTTP | Situação |
|---|---|
| 200 | Consulta realizada |
| 302 | Não autenticado (redireciona para `/login`) |
| 400 | Data/status/limite inválido, período > 731 dias |
| 403 | Perfil sem permissão (ATENDENTE) |
| 500 | Erro inesperado no servidor |

---

## 4. `GET /api/dashboard/resumo`

**Descrição:** indicadores gerais de vendas (RF01).

**Parâmetros:** comuns, exceto `agrupamento` e `limite` (aceitos, mas ignorados).

**Requisição:**

```http
GET /api/dashboard/resumo?dataInicio=2026-11-01&dataFim=2026-11-10 HTTP/1.1
Host: localhost:8080
Accept: application/json
Cookie: JSESSIONID=...
```

**Resposta 200:**

```json
{
  "periodoInicio": "2026-11-01",
  "periodoFim": "2026-11-10",
  "faturamentoTotal": 130.00,
  "faturamentoFinalizado": 100.00,
  "descontoTotal": 0.00,
  "ticketMedio": 65.00,
  "totalPedidos": 3,
  "pedidosNaoCancelados": 2,
  "pedidosFinalizados": 1,
  "pedidosEmAndamento": 1,
  "pedidosCancelados": 1,
  "taxaCancelamento": 33.33,
  "itensVendidos": 3,
  "faturamentoPeriodoAnterior": 80.00,
  "variacaoFaturamentoPercentual": 62.50,
  "atualizadoEm": "2026-11-10T14:30:00"
}
```

**Campos:**

| Campo | Descrição |
|---|---|
| `faturamentoTotal` | Soma de `totalFinal` dos pedidos não cancelados |
| `faturamentoFinalizado` | Soma apenas de pedidos `FINALIZADO` |
| `descontoTotal` | Soma de `valorDesconto` dos pedidos não cancelados |
| `ticketMedio` | Faturamento ÷ pedidos não cancelados |
| `totalPedidos` | Todos os pedidos do período, inclusive cancelados |
| `pedidosNaoCancelados` | Denominação usada no ticket médio |
| `pedidosEmAndamento` | Todos menos finalizados e cancelados |
| `taxaCancelamento` | Percentual de cancelados sobre o total |
| `itensVendidos` | Soma das quantidades dos itens não cancelados |
| `variacaoFaturamentoPercentual` | Variação contra o período anterior de mesmo tamanho; `100.00` se não havia base e agora há; `0.00` se ambos zerados |

**Erros:** `400` para período invertido, data inválida, status inválido ou período > 731 dias.

---

## 5. `GET /api/dashboard/vendas`

**Descrição:** série temporal para o gráfico de vendas (RF02).

**Parâmetros:** comuns + `agrupamento`.

**Requisição:**

```http
GET /api/dashboard/vendas?dataInicio=2026-11-01&dataFim=2026-11-10&agrupamento=DIA
```

**Resposta 200 (exemplo reduzido):**

```json
{
  "agrupamento": "DIA",
  "periodoInicio": "2026-11-01",
  "periodoFim": "2026-11-10",
  "faturamentoTotal": 130.00,
  "totalPedidos": 2,
  "totalItens": 3,
  "pontos": [
    {
      "rotulo": "01/11",
      "inicio": "2026-11-01",
      "fim": "2026-11-01",
      "faturamento": 100.00,
      "pedidos": 1,
      "itens": 2
    },
    {
      "rotulo": "02/11",
      "inicio": "2026-11-02",
      "fim": "2026-11-02",
      "faturamento": 0.00,
      "pedidos": 0,
      "itens": 0
    },
    {
      "rotulo": "08/11",
      "inicio": "2026-11-08",
      "fim": "2026-11-08",
      "faturamento": 30.00,
      "pedidos": 1,
      "itens": 1
    }
  ]
}
```

> O exemplo acima é reduzido. A resposta real contém um ponto para **cada** dia/semana/mês do período, inclusive os zerados.

**Regras:**

- `DIA`: um ponto por data.
- `SEMANA`: um ponto por semana iniciada na segunda-feira; `rotulo` = `Semana dd/MM`.
- `MES`: um ponto por mês; `rotulo` = `MM/yyyy`.
- Datas de `inicio`/`fim` são limitadas ao período solicitado.
- Pedidos cancelados são excluídos.

**Erros:** `400` para agrupamento inválido (`MethodArgumentTypeMismatch`), datas inválidas, período invertido/excessivo.

---

## 6. `GET /api/dashboard/status`

**Descrição:** distribuição dos pedidos por situação (RF03).

**Requisição:**

```http
GET /api/dashboard/status?dataInicio=2026-11-01&dataFim=2026-11-10
```

**Resposta 200:**

```json
{
  "total": 3,
  "emAndamento": 1,
  "finalizados": 1,
  "cancelados": 1,
  "status": [
    { "status": "RECEBIDO", "descricao": "Recebido", "quantidade": 0, "percentual": 0.00, "valor": 0.00 },
    { "status": "EM_PREPARACAO", "descricao": "Em preparação", "quantidade": 1, "percentual": 33.33, "valor": 30.00 },
    { "status": "PRONTO", "descricao": "Pronto", "quantidade": 0, "percentual": 0.00, "valor": 0.00 },
    { "status": "SAIU_PARA_ENTREGA", "descricao": "Saiu para entrega", "quantidade": 0, "percentual": 0.00, "valor": 0.00 },
    { "status": "RETIRADO", "descricao": "Retirado", "quantidade": 0, "percentual": 0.00, "valor": 0.00 },
    { "status": "FINALIZADO", "descricao": "Finalizado", "quantidade": 1, "percentual": 33.33, "valor": 100.00 },
    { "status": "CANCELADO", "descricao": "Cancelado", "quantidade": 1, "percentual": 33.33, "valor": 50.00 }
  ]
}
```

**Observações:**

- Todos os sete status são sempre retornados, mesmo com quantidade zero.
- `valor` de cancelados representa o valor cancelado, não faturamento.
- A soma de `quantidade` é igual a `total`.

---

## 7. `GET /api/dashboard/produtos-mais-vendidos`

**Descrição:** ranking de produtos (RF04).

**Parâmetros:** comuns + `limite` (1–50, padrão 10).

**Requisição:**

```http
GET /api/dashboard/produtos-mais-vendidos?dataInicio=2026-11-01&dataFim=2026-11-10&limite=10
```

**Resposta 200:**

```json
{
  "totalItensVendidos": 3,
  "faturamentoTotal": 130.00,
  "produtos": [
    {
      "posicao": 1,
      "produtoId": "p1",
      "nome": "Pizza Calabresa",
      "categoria": "Pizza",
      "quantidade": 2,
      "faturamento": 100.00,
      "participacaoPercentual": 66.67
    },
    {
      "posicao": 2,
      "produtoId": "p2",
      "nome": "Refrigerante 2L",
      "categoria": "Bebida",
      "quantidade": 1,
      "faturamento": 30.00,
      "participacaoPercentual": 33.33
    }
  ]
}
```

**Regras:**

- Ordenação: quantidade desc → faturamento desc → nome asc.
- Itens de pedidos cancelados não entram.
- Se o produto não existir mais, `categoria` retorna `"Não informada"` e o nome vem do item histórico.
- `participacaoPercentual` usa o total de itens vendidos no período.

**Erros:** `400` se `limite` estiver fora de 1–50.

---

## 8. `GET /api/dashboard/pedidos-recentes`

**Descrição:** últimos pedidos para acompanhamento operacional (RF07 / RF05).

**Parâmetros:** comuns + `limite` (1–50, padrão 8).

**Requisição:**

```http
GET /api/dashboard/pedidos-recentes?dataInicio=2026-11-01&dataFim=2026-11-10&limite=8
```

**Resposta 200:**

```json
[
  {
    "id": "ped-3",
    "dataEntrada": "08/11/2026 20:00",
    "clienteId": "c1",
    "clienteNome": "Ana Souza",
    "status": "EM_PREPARACAO",
    "statusDescricao": "Em preparação",
    "quantidadeItens": 1,
    "total": 30.00,
    "responsavel": "atendente"
  },
  {
    "id": "ped-2",
    "dataEntrada": "05/11/2026 20:00",
    "clienteId": "c2",
    "clienteNome": "Bruno Carvalho",
    "status": "CANCELADO",
    "statusDescricao": "Cancelado",
    "quantidadeItens": 1,
    "total": 50.00,
    "responsavel": "atendente"
  }
]
```

**Observações:**

- Ordenado por `dataEntrada` decrescente.
- O nome do cliente é resolvido via coleção `clientes`; se não existir, retorna `Cliente <id>`.
- A interface usa o `id` para abrir `/pedidos/{id}`.

---

## 9. `GET /api/dashboard/filtros`

**Descrição:** metadados para montar os filtros na interface (RF05).

**Requisição:**

```http
GET /api/dashboard/filtros
```

**Resposta 200:**

```json
{
  "dataMinima": "2026-10-01",
  "dataMaxima": "2026-11-08",
  "periodoPadraoDias": 30,
  "status": [
    { "valor": "RECEBIDO", "rotulo": "Recebido" },
    { "valor": "EM_PREPARACAO", "rotulo": "Em preparação" },
    { "valor": "PRONTO", "rotulo": "Pronto" },
    { "valor": "SAIU_PARA_ENTREGA", "rotulo": "Saiu para entrega" },
    { "valor": "RETIRADO", "rotulo": "Retirado" },
    { "valor": "FINALIZADO", "rotulo": "Finalizado" },
    { "valor": "CANCELADO", "rotulo": "Cancelado" }
  ],
  "agrupamentos": [
    { "valor": "DIA", "rotulo": "Diário" },
    { "valor": "SEMANA", "rotulo": "Semanal" },
    { "valor": "MES", "rotulo": "Mensal" }
  ],
  "periodosRapidos": [
    { "valor": "HOJE", "rotulo": "Hoje" },
    { "valor": "7", "rotulo": "Últimos 7 dias" },
    { "valor": "30", "rotulo": "Últimos 30 dias" },
    { "valor": "MES", "rotulo": "Este mês" }
  ]
}
```

**Observações:**

- `dataMinima`/`dataMaxima` são `null` quando não há pedidos.
- A interface usa `periodoPadraoDias` para calcular o período inicial.

---

## 10. Exemplos de erros

### Data inicial posterior à final

```http
GET /api/dashboard/resumo?dataInicio=2026-11-10&dataFim=2026-11-01
```

```json
{
  "erro": "Requisição inválida.",
  "detalhe": "A data inicial não pode ser posterior à data final."
}
```

### Período maior que o limite

```json
{
  "erro": "Requisição inválida.",
  "detalhe": "O período máximo permitido é de 731 dias."
}
```

### Status inexistente

```json
{
  "erro": "Requisição inválida.",
  "detalhe": "Status inválido: 'ENTREGUE'. Valores aceitos: [RECEBIDO, EM_PREPARACAO, PRONTO, SAIU_PARA_ENTREGA, RETIRADO, FINALIZADO, CANCELADO]"
}
```

### Limite fora do intervalo

```json
{
  "erro": "Requisição inválida.",
  "detalhe": "O limite deve estar entre 1 e 50."
}
```

### Perfil sem permissão

```json
{
  "erro": "Acesso negado.",
  "detalhe": "Seu perfil não tem permissão para esta operação."
}
```

## 11. Teste rápido

1. Suba a aplicação: `.\mvnw.cmd spring-boot:run`.
2. Acesse `http://localhost:8080/login` e entre com `gerente` / `gerente123` (ou `admin` / `admin123`).
3. Abra `http://localhost:8080/dashboard`.
4. Para testar a API isoladamente, importe `docs/postman/PediuPatrao-Dashboard.postman_collection.json` no Postman e execute a pasta **Dashboard**. A coleção faz login e reutiliza o cookie de sessão.
5. Alternativamente, use o DevTools do navegador na aba **Network** enquanto o dashboard carrega e copie a requisição de qualquer endpoint.
