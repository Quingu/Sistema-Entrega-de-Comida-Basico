# Evidências da API do dashboard
# Uso: .\scripts\evidencias-api.ps1 [-BaseUrl http://localhost:8080]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Usuario = "gerente",
    [string]$Senha = "gerente123"
)

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
$saida = Join-Path $raiz "docs\evidencias"
New-Item -ItemType Directory -Force -Path $saida | Out-Null

$utf8SemBom = New-Object System.Text.UTF8Encoding($false)

function Salvar-Texto([string]$caminho, [string]$texto) {
    [System.IO.File]::WriteAllText($caminho, $texto, $utf8SemBom)
}

function Ler-Corpo($resposta) {
    # Lê os bytes crus e decodifica como UTF-8, evitando o ISO-8859-1 padrão do PS 5.1.
    $bytes = $resposta.RawContentStream.ToArray()
    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

function Ler-CorpoErro($erro) {
    # No PS 5.1 o Invoke-WebRequest já consome o stream de erro; ErrorDetails traz o corpo decodificado.
    if ($null -ne $erro.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($erro.ErrorDetails.Message)) {
        return $erro.ErrorDetails.Message
    }
    try {
        $resposta = $erro.Exception.Response
        if ($null -eq $resposta) { return $erro.Exception.Message }
        $stream = $resposta.GetResponseStream()
        if ($null -eq $stream) { return $erro.Exception.Message }
        $leitor = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
        $texto = $leitor.ReadToEnd()
        $leitor.Dispose()
        if ([string]::IsNullOrWhiteSpace($texto)) { return $erro.Exception.Message }
        return $texto
    } catch {
        return $erro.Exception.Message
    }
}

function Login([string]$usuario, [string]$senha) {
    $loginPage = Invoke-WebRequest -Uri "$BaseUrl/login" -SessionVariable session -UseBasicParsing
    $match = [regex]::Match((Ler-Corpo $loginPage), 'name="_csrf"[^>]*value="([^"]+)"')
    if (-not $match.Success) {
        throw "Token CSRF não encontrado na página de login."
    }
    $body = @{
        username = $usuario
        password = $senha
        _csrf    = $match.Groups[1].Value
    }
    $resposta = Invoke-WebRequest -Uri "$BaseUrl/login" -Method Post -Body $body -WebSession $session -UseBasicParsing
    if ($resposta.BaseResponse.ResponseUri.AbsolutePath -eq "/login") {
        throw "Login falhou para o usuário $usuario."
    }
    return $session
}

function Consultar([string]$nome, [string]$caminho, $session) {
    $url = "$BaseUrl$caminho"
    $inicio = Get-Date
    $resposta = Invoke-WebRequest -Uri $url -WebSession $session -UseBasicParsing
    $duracao = [Math]::Round(((Get-Date) - $inicio).TotalMilliseconds, 0)
    $conteudo = Ler-Corpo $resposta
    $json = $conteudo | ConvertFrom-Json
    $arquivo = Join-Path $saida ("{0}.json" -f $nome)
    Salvar-Texto $arquivo $conteudo
    Write-Host ("[200] {0} ({1} ms) -> {2}" -f $caminho, $duracao, $arquivo)
    return [pscustomobject]@{
        Endpoint  = $caminho
        Status    = 200
        DuracaoMs = $duracao
        Arquivo   = $arquivo
        Resumo    = $json
    }
}

Write-Host "Autenticando como $Usuario..."
$session = Login $Usuario $Senha

$consultas = @(
    @{ Nome = "01-resumo"; Caminho = "/api/dashboard/resumo?dataInicio=2026-09-01&dataFim=2026-11-10" },
    @{ Nome = "02-vendas-semana"; Caminho = "/api/dashboard/vendas?dataInicio=2026-09-01&dataFim=2026-11-10&agrupamento=SEMANA" },
    @{ Nome = "03-status"; Caminho = "/api/dashboard/status?dataInicio=2026-09-01&dataFim=2026-11-10" },
    @{ Nome = "04-produtos-mais-vendidos"; Caminho = "/api/dashboard/produtos-mais-vendidos?dataInicio=2026-09-01&dataFim=2026-11-10&limite=10" },
    @{ Nome = "05-pedidos-recentes"; Caminho = "/api/dashboard/pedidos-recentes?dataInicio=2026-09-01&dataFim=2026-11-10&limite=8" },
    @{ Nome = "06-filtros"; Caminho = "/api/dashboard/filtros" }
)

$resultados = @()
foreach ($consulta in $consultas) {
    $resultados += Consultar $consulta.Nome $consulta.Caminho $session
}

Write-Host "Testando erro de período invertido..."
$erroInvertido = $null
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/dashboard/resumo?dataInicio=2026-11-10&dataFim=2026-11-01" -WebSession $session -UseBasicParsing | Out-Null
    $erroInvertido = [pscustomobject]@{
        Endpoint = "/api/dashboard/resumo?dataInicio=2026-11-10&dataFim=2026-11-01"
        Status   = 200
        Arquivo  = $null
    }
    Write-Host "[200] período invertido NÃO foi rejeitado (falha de validação)"
} catch {
    $corpoErro = Ler-CorpoErro $_
    $arquivoErro = Join-Path $saida "07-erro-periodo-invertido.json"
    Salvar-Texto $arquivoErro $corpoErro
    $erroInvertido = [pscustomobject]@{
        Endpoint = "/api/dashboard/resumo?dataInicio=2026-11-10&dataFim=2026-11-01"
        Status   = [int]$_.Exception.Response.StatusCode
        Arquivo  = $arquivoErro
    }
    Write-Host ("[{0}] período invertido" -f $erroInvertido.Status)
}

Write-Host "Testando acesso do perfil ATENDENTE..."
$negado = $null
try {
    $sessionAtendente = Login "atendente" "atendente123"
    Invoke-WebRequest -Uri "$BaseUrl/api/dashboard/resumo" -WebSession $sessionAtendente -UseBasicParsing | Out-Null
    $negado = [pscustomobject]@{ Status = 200; Arquivo = $null }
    Write-Host "[200] ATENDENTE acessou indevidamente (falha de autorização)"
} catch {
    $negado = [pscustomobject]@{
        Status  = [int]$_.Exception.Response.StatusCode
        Arquivo = Join-Path $saida "08-acesso-negado.json"
    }
    Salvar-Texto $negado.Arquivo (Ler-CorpoErro $_)
    Write-Host ("[{0}] acesso do ATENDENTE" -f $negado.Status)
}

$linhas = @(
    "# Evidências de execução da API",
    "",
    ("Gerado em: {0}" -f (Get-Date).ToString("dd/MM/yyyy HH:mm:ss")),
    ("Base URL: {0}" -f $BaseUrl),
    ("Usuário: {0}" -f $Usuario),
    "",
    "| # | Endpoint | Status | Tempo | Arquivo |",
    "|---|---|---|---|---|"
)
$i = 1
foreach ($r in $resultados) {
    $linhas += ("| {0} | {1} | {2} | {3} ms | docs/evidencias/{4} |" -f $i, $r.Endpoint, $r.Status, $r.DuracaoMs, (Split-Path -Leaf $r.Arquivo))
    $i++
}
if ($erroInvertido) {
    $linhas += ("| {0} | Validação de período invertido | {1} | — | docs/evidencias/07-erro-periodo-invertido.json |" -f $i, $erroInvertido.Status)
    $i++
}
if ($negado) {
    $linhas += ("| {0} | Acesso do ATENDENTE bloqueado | {1} | — | docs/evidencias/08-acesso-negado.json |" -f $i, $negado.Status)
}

$linhas += ""
$linhas += "Consulte ``docs/API.md`` para o contrato completo de cada endpoint e ``docs/TESTES.md`` para a matriz de testes."
Salvar-Texto (Join-Path $saida "RESUMO.md") ($linhas -join "`r`n")

Write-Host ""
Write-Host "Evidências salvas em $saida"
