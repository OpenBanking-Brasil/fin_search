<#
.SYNOPSIS
    Inicia o backend Fabric e o frontend SvelteKit localmente,
    com auto-healing (reinicia automaticamente se os processos caírem).

.PARAMETER ApiKey
    Chave de API para proteger as rotas do servidor Fabric.
    Se não informado, usa a variável de ambiente FABRIC_API_KEY.

.PARAMETER BackendPort
    Porta do backend Fabric (padrão: 18080)

.PARAMETER FrontendPort
    Porta do frontend SvelteKit (padrão: 5199)

.PARAMETER NoOpen
    Não abrir o navegador automaticamente.

.PARAMETER Watch
    Mantém o script rodando e reinicia processos caídos a cada 15s (auto-healing).

.EXAMPLE
    .\start_fabric_local_app.ps1
    .\start_fabric_local_app.ps1 -ApiKey "minha-chave-secreta"
    .\start_fabric_local_app.ps1 -Watch
#>

param(
    [string] $ApiKey      = $env:FABRIC_API_KEY,
    [int]    $BackendPort = 18080,
    [int]    $FrontendPort= 5199,
    [switch] $NoOpen,
    [switch] $Watch
)

$ErrorActionPreference = "Stop"

$repoRoot = "D:\Users\caioc\Documents\GitHub\fin_search"
$webDir   = Join-Path $repoRoot "tools\Fabric\web"
$appUrl   = "http://127.0.0.1:$FrontendPort/chat"

# ── Helpers ───────────────────────────────────────────────────────────────────
function Test-PortOpen {
    param([string]$HostName, [int]$Port)
    try {
        $conn = Test-NetConnection -ComputerName $HostName -Port $Port -WarningAction SilentlyContinue
        return [bool]$conn.TcpTestSucceeded
    } catch {
        return $false
    }
}

function Write-Status {
    param([string]$Msg, [string]$Color = "Cyan")
    $ts = Get-Date -Format "HH:mm:ss"
    Write-Host "[$ts] $Msg" -ForegroundColor $Color
}

function Start-FabricBackend {
    $serveCmd = "`"`" | fabric --serve --address :$BackendPort"
    if ($ApiKey) {
        $serveCmd = "`"`" | fabric --serve --address :$BackendPort --api-key `"$ApiKey`""
    }

    Start-Process powershell -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", $serveCmd
    ) -WorkingDirectory $repoRoot | Out-Null

    Write-Status "Backend Fabric iniciado na porta $BackendPort." "Green"
}

function Start-FabricFrontend {
    $frontendCmd = @"
Set-Location "$webDir"
`$null = New-Item -ItemType Directory -Force "static/data"
`$patternDesc = "..\scripts\pattern_descriptions\pattern_descriptions.json"
if (Test-Path `$patternDesc) {
    Copy-Item `$patternDesc "static/data\pattern_descriptions.json" -Force
}
`$env:FABRIC_BASE_URL = "http://127.0.0.1:$BackendPort"
npx vite dev --host 127.0.0.1 --port $FrontendPort
"@

    Start-Process powershell -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", $frontendCmd
    ) -WorkingDirectory $repoRoot | Out-Null

    Write-Status "Frontend SvelteKit iniciado na porta $FrontendPort." "Green"
}

function Open-AppWindow {
    if (Get-Command msedge -ErrorAction SilentlyContinue) {
        Start-Process "msedge" "--app=$appUrl" | Out-Null
    } elseif (Get-Command chrome -ErrorAction SilentlyContinue) {
        Start-Process "chrome" "--app=$appUrl" | Out-Null
    } else {
        Start-Process $appUrl | Out-Null
    }
    Write-Status "Janela do app aberta: $appUrl" "Cyan"
}

function Wait-ForPort {
    param([int]$Port, [int]$MaxSeconds = 30)
    $elapsed = 0
    while ($elapsed -lt $MaxSeconds) {
        if (Test-PortOpen -HostName "127.0.0.1" -Port $Port) { return $true }
        Start-Sleep -Seconds 1
        $elapsed++
    }
    return $false
}

# ── Inicialização ─────────────────────────────────────────────────────────────
Write-Status "Iniciando Fabric Local App..." "White"
if ($ApiKey) {
    Write-Status "Modo seguro: API key configurada." "Yellow"
}

# Backend
if (-not (Test-PortOpen -HostName "127.0.0.1" -Port $BackendPort)) {
    Start-FabricBackend
    Write-Status "Aguardando backend ficar disponível..." "DarkGray"
    $ok = Wait-ForPort -Port $BackendPort -MaxSeconds 20
    if (-not $ok) {
        Write-Status "AVISO: Backend não respondeu em 20s. Verifique erros acima." "Yellow"
    }
} else {
    Write-Status "Backend já está rodando na porta $BackendPort." "DarkGray"
}

# Frontend
if (-not (Test-PortOpen -HostName "127.0.0.1" -Port $FrontendPort)) {
    Start-FabricFrontend
    Write-Status "Aguardando frontend ficar disponível..." "DarkGray"
    $ok = Wait-ForPort -Port $FrontendPort -MaxSeconds 40
    if (-not $ok) {
        Write-Status "AVISO: Frontend não respondeu em 40s. Verifique erros acima." "Yellow"
    }
} else {
    Write-Status "Frontend já está rodando na porta $FrontendPort." "DarkGray"
}

# Abrir navegador
if (-not $NoOpen) {
    Open-AppWindow
}

# ── Auto-healing loop ─────────────────────────────────────────────────────────
if ($Watch) {
    Write-Status "Modo Watch ativo. Pressione Ctrl+C para sair." "Magenta"
    Write-Status "Verificando saúde a cada 15 segundos..." "DarkGray"

    while ($true) {
        Start-Sleep -Seconds 15

        $backendOk  = Test-PortOpen -HostName "127.0.0.1" -Port $BackendPort
        $frontendOk = Test-PortOpen -HostName "127.0.0.1" -Port $FrontendPort

        if (-not $backendOk) {
            Write-Status "Backend caiu! Reiniciando..." "Red"
            Start-FabricBackend
        }

        if (-not $frontendOk) {
            Write-Status "Frontend caiu! Reiniciando..." "Red"
            Start-FabricFrontend
        }

        if ($backendOk -and $frontendOk) {
            Write-Status "Serviços OK — backend :$BackendPort  frontend :$FrontendPort" "DarkGray"
        }
    }
} else {
    Write-Status "Pronto! Acesse: $appUrl" "Green"
    Write-Status "Dica: use -Watch para reinicialização automática se os serviços caírem." "DarkGray"
}
