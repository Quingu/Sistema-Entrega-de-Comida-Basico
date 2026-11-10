# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).
Versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [1.0.0] - 2026-11-10

### Adicionado

- Dashboard administrativo em `/dashboard` para `ADMIN` e `GERENTE`.
- API REST somente leitura em `/api/dashboard/**` com seis endpoints:
  `resumo`, `vendas`, `status`, `produtos-mais-vendidos`, `pedidos-recentes` e `filtros`.
- Agregações no servidor (`DashboardService`) a partir dos pedidos do MongoDB, com filtro
  por período, status, agrupamento e limite.
- Filtros persistentes na URL, atalhos de período, atualização manual e automática (60 s),
  estados de carregamento, erro, vazio e sessão expirada.
- Gráficos Chart.js para evolução das vendas, situação dos pedidos e volume por produto.
- Índices MongoDB em `Pedido.dataEntrada` e `Pedido.status`.
- Dados de demonstração opcionais (usuários, produtos, clientes e ~60 dias de pedidos).
- `application.properties.example` com todas as chaves de configuração.
- Testes de serviço, contrato HTTP e autorização por perfil (50 testes no total).
- Documentação de requisitos, API, arquitetura, testes e coleção Postman.
- Script `scripts/evidencias-api.ps1` e respostas reais versionadas em `docs/evidencias/`.

### Segurança

- Acesso ao dashboard e à API restrito a `ADMIN` e `GERENTE`.
- Requisições de API sem permissão retornam `403` em JSON; páginas exibem a tela de acesso negado.
- Endpoints são `GET` e não expõem dados sensíveis de usuários.

### Corrigido

- Página de erro passa a receber o status HTTP correto (antes exibia mensagem genérica).
- Evidências da API são gravadas em UTF-8, preservando acentuação.
