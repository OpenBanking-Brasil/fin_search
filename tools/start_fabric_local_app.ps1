<#
.SYNOPSIS
    Inicia o Fabric backend (REST API) e o frontend (SvelteKit) localmente.

.DESCRIPTION
    - Verifica se backend/frontend já estão rodando antes de iniciar novamente.
    - Suporta --api-key para proteger a API REST do Fabric.
    - Aguarda health-check real dos serviços antes de abrir o browser.
    - Auto-healing: reinicia backend e/ou frontend se pararem.
    - Abre a UI no modo "app" (sem barra de navegação do browser).

.PARAMETER BackendPort
    Porta do backend Fabric REST. Padrão: 18080.

.PARAMETER FrontendPort
    Porta do frontend SvelteKit. Padrão: 5199.

.PARAMETER ApiKey
    Chave de API para proteger o servidor Fabric (--api-key). Opcional.

.PARAMETER Watch
    Mantém o script rodando e reinicia serviços se pararem (auto-healing).

.PARAMETER SkipBrowser
    Não abre o browser após iniciar os serviços.

.EXAMPLE
    .\start_fabric_local_app.ps1
    .\start_fabric_local_app.ps1 -ApiKey "minha-chave-secreta"
    .\start_fabric_local_app.ps1 -Watch
#>
param(
    [int]$BackendPort  = 18080,
    [int]$FrontendPort = 5199,
    [string]$ApiKey    = "",
    [switch]$Watch,
    [switch]$SkipBrowser
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"

$RepoRoot = "D:\Users\caioc\Documents\GitHub\fin_search"
$WebDir   = Join-Path $RepoRoot "tools\Fabric\web"
$AppUrl   = "http://127.0.0.1:$FrontendPort/chat"

# ── Helpers ──────────────────────────────────────────────────────────────────
function Write-Step($msg) { Write-Host "  → $msg" -ForegroundColor Cyan }
function Write-Done($msg) { Write-Host "  ✓ $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "  ! $msg" -ForegroundColor Yellow }
function Write-Fail($msg) { Write-Host "  ✗ $msg" -ForegroundColor Red }

function Test-PortOpen([string]$h, [int]$p) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $a = $c.BeginConnect($h, $p, $null, $null)
        $ok = $a.AsyncWaitHandle.WaitOne(500)
        $c.Close()
        return $ok
    } catch { return $false }
}

function Wait-ForPort([string]$h, [int]$p, [int]$maxSeconds = 30, [string]$label = "") {
    $deadline = (Get-Date).AddSeconds($maxSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortOpen $h $p) { return $true }
        Write-Host "." -NoNewline
        Start-Sleep -Milliseconds 800
    }
    Write-Host ""
    return $false
}

function Start-BackendProcess {
    $apiKeyFlag = if ($ApiKey) { "--api-key `"$ApiKey`"" } else { "" }
    $cmd = "`"`" | fabric --serve --address :$BackendPort $apiKeyFlag"
    Write-Step "Iniciando backend Fabric na porta $BackendPort..."
    $proc = Start-Process powershell -ArgumentList @(
        "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $cmd
    ) -WorkingDirectory $RepoRoot -PassThru
    return $proc
}

function Start-FrontendProcess {
    $cmd = @"
Set-Location "$WebDir"
if (-not (Test-Path "static\data")) { New-Item -ItemType Directory -Force "static\data" | Out-Null }
`$src = "..\scripts\pattern_descriptions\pattern_descriptions.json"
if (Test-Path `$src) { Copy-Item `$src "static\data\pattern_descriptions.json" -Force }
`$env:FABRIC_BASE_URL = "http://127.0.0.1:$BackendPort"
`$env:VITE_FABRIC_BASE_URL = "http://127.0.0.1:$BackendPort"
npx vite dev --host 127.0.0.1 --port $FrontendPort
"@
    Write-Step "Iniciando frontend SvelteKit na porta $FrontendPort..."
    $proc = Start-Process powershell -ArgumentList @(
        "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $cmd
    ) -WorkingDirectory $RepoRoot -PassThru
    return $proc
}

function Open-AppBrowser {
    if ($SkipBrowser) { return }
    $msedge   = (Get-Command "msedge"   -ErrorAction SilentlyContinue)?.Source
    $chrome   = (Get-Command "chrome"   -ErrorAction SilentlyContinue)?.Source
    $chromium = (Get-Command "chromium" -ErrorAction SilentlyContinue)?.Source
    $edge64   = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
    $edgeAlt  = "C:\Program Files\Microsoft\Edge\Application\msedge.exe"

    if     ($msedge)                  { Start-Process $msedge   "--app=$AppUrl" }
    elseif (Test-Path $edge64)        { Start-Process $edge64   "--app=$AppUrl" }
    elseif (Test-Path $edgeAlt)       { Start-Process $edgeAlt  "--app=$AppUrl" }
    elseif ($chrome)                  { Start-Process $chrome   "--app=$AppUrl" }
    elseif ($chromium)                { Start-Process $chromium "--app=$AppUrl" }
    else                              { Start-Process $AppUrl }
}

# ── Início ────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "  Fabric Local App" -ForegroundColor Magenta
Write-Host "  Backend : http://127.0.0.1:$BackendPort" -ForegroundColor DarkGray
Write-Host "  Frontend: $AppUrl" -ForegroundColor DarkGray
if ($ApiKey) { Write-Host "  API Key : ***" -ForegroundColor DarkGray }
Write-Host ""

# ── Backend ───────────────────────────────────────────────────────────────────
$backendProc = $null
if (Test-PortOpen "127.0.0.1" $BackendPort) {
    Write-Done "Backend já está rodando na porta $BackendPort."
} else {
    $backendProc = Start-BackendProcess
    Write-Host "  Aguardando backend" -NoNewline
    if (Wait-ForPort "127.0.0.1" $BackendPort 40 "backend") {
        Write-Done " Backend pronto!"
    } else {
        Write-Fail " Timeout aguardando backend. Verifique a janela do PowerShell."
    }
}

# ── Frontend ──────────────────────────────────────────────────────────────────
$frontendProc = $null
if (Test-PortOpen "127.0.0.1" $FrontendPort) {
    Write-Done "Frontend já está rodando na porta $FrontendPort."
} else {
    $frontendProc = Start-FrontendProcess
    Write-Host "  Aguardando frontend" -NoNewline
    if (Wait-ForPort "127.0.0.1" $FrontendPort 60 "frontend") {
        Write-Done " Frontend pronto!"
    } else {
        Write-Warn " Timeout aguardando frontend. Tentando abrir mesmo assim..."
    }
}

# ── Abrir browser ─────────────────────────────────────────────────────────────
Open-AppBrowser
Write-Done "App aberto em: $AppUrl"

# ── Auto-healing ──────────────────────────────────────────────────────────────
if (-not $Watch) {
    Write-Host ""
    Write-Host "  Dica: Use -Watch para manter o script rodando e reiniciar serviços automaticamente." -ForegroundColor DarkGray
    exit 0
}

Write-Host ""
Write-Host "  Modo Watch ativo — reiniciará serviços automaticamente se pararem." -ForegroundColor Yellow
Write-Host "  Pressione Ctrl+C para encerrar.`n" -ForegroundColor DarkGray

$healCooldown = 15  # segundos entre tentativas de reinício
$lastHealBackend  = [datetime]::MinValue
$lastHealFrontend = [datetime]::MinValue

while ($true) {
    Start-Sleep -Seconds 5

    # ── Verificar backend ─────────────────────────────────────────────────────
    if (-not (Test-PortOpen "127.0.0.1" $BackendPort)) {
        $now = Get-Date
        if (($now - $lastHealBackend).TotalSeconds -ge $healCooldown) {
            Write-Warn "Backend caiu! Reiniciando..."
            $lastHealBackend = $now
            $backendProc = Start-BackendProcess
            Write-Host "  Aguardando backend" -NoNewline
            if (Wait-ForPort "127.0.0.1" $BackendPort 40) {
                Write-Done " Backend recuperado!"
            } else {
                Write-Fail " Falha ao recuperar backend."
            }
        }
    }

    # ── Verificar frontend ────────────────────────────────────────────────────
    if (-not (Test-PortOpen "127.0.0.1" $FrontendPort)) {
        $now = Get-Date
        if (($now - $lastHealFrontend).TotalSeconds -ge $healCooldown) {
            Write-Warn "Frontend caiu! Reiniciando..."
            $lastHealFrontend = $now
            $frontendProc = Start-FrontendProcess
            Write-Host "  Aguardando frontend" -NoNewline
            if (Wait-ForPort "127.0.0.1" $FrontendPort 60) {
                Write-Done " Frontend recuperado!"
            } else {
                Write-Fail " Falha ao recuperar frontend."
            }
        }
    }
}
