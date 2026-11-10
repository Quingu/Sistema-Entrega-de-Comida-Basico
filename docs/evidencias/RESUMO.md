# Evidências de execução da API

Gerado em: 10/11/2026 14:37:05
Base URL: http://localhost:8080
Usuário: gerente

| # | Endpoint | Status | Tempo | Arquivo |
|---|---|---|---|---|
| 1 | /api/dashboard/resumo?dataInicio=2026-09-01&dataFim=2026-11-10 | 200 | 315 ms | docs/evidencias/01-resumo.json |
| 2 | /api/dashboard/vendas?dataInicio=2026-09-01&dataFim=2026-11-10&agrupamento=SEMANA | 200 | 50 ms | docs/evidencias/02-vendas-semana.json |
| 3 | /api/dashboard/status?dataInicio=2026-09-01&dataFim=2026-11-10 | 200 | 36 ms | docs/evidencias/03-status.json |
| 4 | /api/dashboard/produtos-mais-vendidos?dataInicio=2026-09-01&dataFim=2026-11-10&limite=10 | 200 | 43 ms | docs/evidencias/04-produtos-mais-vendidos.json |
| 5 | /api/dashboard/pedidos-recentes?dataInicio=2026-09-01&dataFim=2026-11-10&limite=8 | 200 | 41 ms | docs/evidencias/05-pedidos-recentes.json |
| 6 | /api/dashboard/filtros | 200 | 78 ms | docs/evidencias/06-filtros.json |
| 7 | Validação de período invertido | 400 | — | docs/evidencias/07-erro-periodo-invertido.json |
| 8 | Acesso do ATENDENTE bloqueado | 403 | — | docs/evidencias/08-acesso-negado.json |

Consulte `docs/API.md` para o contrato completo de cada endpoint e `docs/TESTES.md` para a matriz de testes.