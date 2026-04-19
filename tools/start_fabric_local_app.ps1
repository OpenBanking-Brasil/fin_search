<#
.SYNOPSIS
    Inicia o frontend ARCÁDIA OS (SvelteKit) localmente.
    A API de chat e padrões ficam nas rotas /api/* do próprio servidor de desenvolvimento (Gemini no servidor).

.PARAMETER FrontendPort
    Porta do Vite/SvelteKit (padrão: 5199)

.PARAMETER NoOpen
    Não abrir o navegador automaticamente.

.PARAMETER Watch
    Mantém o script a verificar se o processo na porta do frontend caiu e regista aviso (não reinicia automaticamente o Vite).

.EXAMPLE
    .\start_fabric_local_app.ps1
    .\start_fabric_local_app.ps1 -FrontendPort 5173
#>

param(
    [int]    $FrontendPort = 5199,
    [switch] $NoOpen,
    [switch] $Watch
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$webDir   = Join-Path $repoRoot "tools\Fabric\web"
$appUrl   = "http://127.0.0.1:$FrontendPort/login"

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

function Start-ArcadiaFrontend {
    $frontendCmd = @"
Set-Location "$webDir"
`$null = New-Item -ItemType Directory -Force "static/data"
`$patternDesc = "..\scripts\pattern_descriptions\pattern_descriptions.json"
if (Test-Path `$patternDesc) {
    Copy-Item `$patternDesc "static/data\pattern_descriptions.json" -Force
}
npx vite dev --host 127.0.0.1 --port $FrontendPort
"@

    Start-Process powershell -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", $frontendCmd
    ) -WorkingDirectory $repoRoot | Out-Null

    Write-Status "Frontend ARCÁDIA OS (Vite) iniciado na porta $FrontendPort." "Green"
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
    param([int]$Port, [int]$MaxSeconds = 40)
    $elapsed = 0
    while ($elapsed -lt $MaxSeconds) {
        if (Test-PortOpen -HostName "127.0.0.1" -Port $Port) { return $true }
        Start-Sleep -Seconds 1
        $elapsed++
    }
    return $false
}

Write-Status "Iniciando ARCÁDIA OS (apenas frontend; API em /api no Vite)..." "White"

if (-not (Test-Path $webDir)) {
    Write-Status "Pasta não encontrada: $webDir" "Red"
    exit 1
}

if (-not (Test-PortOpen -HostName "127.0.0.1" -Port $FrontendPort)) {
    Start-ArcadiaFrontend
    Write-Status "A aguardar o servidor de desenvolvimento..." "DarkGray"
    $ok = Wait-ForPort -Port $FrontendPort -MaxSeconds 45
    if (-not $ok) {
        Write-Status "AVISO: Frontend não respondeu a tempo. Verifique a janela do Vite." "Yellow"
    }
} else {
    Write-Status "Já existe um serviço na porta $FrontendPort." "DarkGray"
}

if (-not $NoOpen) {
    Open-AppWindow
}

if ($Watch) {
    Write-Status "Modo Watch: a verificar a porta $FrontendPort a cada 15s (Ctrl+C para sair)." "Magenta"
    while ($true) {
        Start-Sleep -Seconds 15
        $frontendOk = Test-PortOpen -HostName "127.0.0.1" -Port $FrontendPort
        if (-not $frontendOk) {
            Write-Status "Frontend não responde na porta $FrontendPort." "Yellow"
        } else {
            Write-Status "Serviço OK — :$FrontendPort" "DarkGray"
        }
    }
} else {
    Write-Status "Pronto. URL: $appUrl" "Green"
    Write-Status "Defina ARCADIA_GEMINI_API_KEY no .env (tools/Fabric/web) para o chat." "DarkGray"
}
