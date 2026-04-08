<#
.SYNOPSIS
    Atualiza o Fabric (binário + patterns + strategies) para a versão mais recente.

.DESCRIPTION
    1. Atualiza o binário via winget ou go install
    2. Atualiza os patterns via fabric --updatepatterns
    3. Verifica se as strategies estão instaladas

.PARAMETER SkipBinary
    Pula a atualização do binário (apenas atualiza patterns/strategies)

.PARAMETER UseGoInstall
    Força atualização via `go install` mesmo que winget esteja disponível

.EXAMPLE
    .\fabric_update.ps1
    .\fabric_update.ps1 -SkipBinary
#>

param(
    [switch] $SkipBinary,
    [switch] $UseGoInstall
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "`n▶ $Message" -ForegroundColor Cyan
}

function Write-Ok {
    param([string]$Message)
    Write-Host "  ✓ $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "  ⚠ $Message" -ForegroundColor Yellow
}

# ── 1. Versão atual ───────────────────────────────────────────────────────────
Write-Step "Verificando versão atual do Fabric..."
try {
    $versionOutput = "" | fabric --version 2>&1
    Write-Ok "Versão atual: $($versionOutput -join ' ')"
} catch {
    Write-Warn "fabric não encontrado no PATH — instalando pela primeira vez."
}

# ── 2. Atualizar binário ──────────────────────────────────────────────────────
if (-not $SkipBinary) {
    Write-Step "Atualizando binário do Fabric..."

    $wingetAvailable = Get-Command winget -ErrorAction SilentlyContinue
    $goAvailable     = Get-Command go    -ErrorAction SilentlyContinue

    if ($wingetAvailable -and -not $UseGoInstall) {
        Write-Host "  Usando winget..." -ForegroundColor DarkGray
        winget install danielmiessler.Fabric --accept-source-agreements --accept-package-agreements
        Write-Ok "Atualizado via winget."
    } elseif ($goAvailable) {
        Write-Host "  Usando go install..." -ForegroundColor DarkGray
        go install github.com/danielmiessler/fabric/cmd/fabric@latest
        Write-Ok "Atualizado via go install."
    } else {
        Write-Warn "Nem winget nem go encontrados. Baixe manualmente em:"
        Write-Host "  https://github.com/danielmiessler/fabric/releases/latest" -ForegroundColor Blue
    }

    # Verificar nova versão
    try {
        $newVersion = "" | fabric --version 2>&1
        Write-Ok "Nova versão: $($newVersion -join ' ')"
    } catch {
        Write-Warn "Não foi possível verificar a nova versão."
    }
}

# ── 3. Atualizar patterns ─────────────────────────────────────────────────────
Write-Step "Atualizando patterns (fabric --updatepatterns)..."
try {
    "" | fabric --updatepatterns
    Write-Ok "Patterns atualizados com sucesso."
} catch {
    Write-Warn "Erro ao atualizar patterns: $_"
}

# ── 4. Verificar strategies ───────────────────────────────────────────────────
Write-Step "Verificando strategies instaladas..."
$strategiesPath = Join-Path $HOME ".config/fabric/strategies"

if (Test-Path $strategiesPath) {
    $strategies = Get-ChildItem -Path $strategiesPath -Filter "*.json" | Select-Object -ExpandProperty BaseName
    if ($strategies) {
        Write-Ok "Strategies disponíveis: $($strategies -join ', ')"
    } else {
        Write-Warn "Nenhuma strategy encontrada em $strategiesPath"
        Write-Host "  Execute 'fabric --setup' e selecione a opção de strategies." -ForegroundColor DarkGray
    }
} else {
    Write-Warn "Diretório de strategies não encontrado."
    Write-Host "  Execute 'fabric --setup' para instalar." -ForegroundColor DarkGray
}

# ── 5. Listar patterns atualizados ────────────────────────────────────────────
Write-Step "Patterns disponíveis após atualização..."
try {
    $patternList = "" | fabric --listpatterns 2>&1
    $patternCount = ($patternList | Where-Object { $_ -match '\S' }).Count
    Write-Ok "$patternCount patterns instalados."
} catch {
    Write-Warn "Não foi possível listar patterns."
}

# ── 6. Recarregar aliases se o script existir ─────────────────────────────────
$aliasScript = Join-Path $PSScriptRoot "fabric_aliases.ps1"
if (Test-Path $aliasScript) {
    Write-Step "Recarregando aliases Fabric..."
    . $aliasScript
    Write-Ok "Aliases recarregados."
}

Write-Host "`n✓ Atualização concluída!" -ForegroundColor Green
