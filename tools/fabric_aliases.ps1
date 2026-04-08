<#
.SYNOPSIS
    Carrega aliases PowerShell para cada pattern do Fabric.
    Inclui função `yt` para transcrição de vídeos do YouTube.

.DESCRIPTION
    Execute este script no seu $PROFILE para ter todos os patterns
    disponíveis como comandos diretos na sessão PowerShell.

    Exemplo de uso após carregar:
        summarize "resumir este texto"
        Get-Clipboard | extract_wisdom
        yt https://youtu.be/xxxx | summarize

.NOTES
    Compatível com PowerShell 7+ e Windows PowerShell 5.1
    Baseado na documentação oficial do Fabric (danielmiessler/fabric)
#>

param(
    [string]$PatternsPath  = (Join-Path $HOME ".config/fabric/patterns"),
    [string]$AliasPrefix   = ($env:FABRIC_ALIAS_PREFIX ?? ""),
    [switch]$Quiet
)

if (-not (Test-Path $PatternsPath)) {
    Write-Warning "Diretório de patterns não encontrado: $PatternsPath"
    Write-Warning "Execute 'fabric --setup' primeiro para instalar os patterns."
    return
}

$count = 0

foreach ($patternDir in Get-ChildItem -Path $PatternsPath -Directory) {
    $patternName = $patternDir.Name
    $aliasName   = "$AliasPrefix$patternName"

    $functionDefinition = @"
function global:$aliasName {
    [CmdletBinding()]
    param(
        [Parameter(ValueFromPipeline = `$true)]
        [string] `$InputObject,

        [Parameter(ValueFromRemainingArguments = `$true)]
        [String[]] `$patternArgs
    )

    begin {
        `$collector = [System.Collections.Generic.List[string]]::new()
    }

    process {
        if (`$InputObject) {
            `$collector.Add(`$InputObject)
        }
    }

    end {
        `$pipelineContent = `$collector -join "\`n"
        if (`$pipelineContent) {
            `$pipelineContent | fabric --pattern $patternName `$patternArgs
        } else {
            fabric --pattern $patternName `$patternArgs
        }
    }
}
"@

    Invoke-Expression $functionDefinition
    $count++
}

# ── Função `yt`: transcrição de vídeos YouTube ────────────────────────────────
function global:yt {
    [CmdletBinding()]
    param(
        [Parameter(Position = 0)]
        [string] $VideoLink,

        [Alias("timestamps")]
        [switch] $t,

        [switch] $Comments,
        [switch] $Metadata,
        [string] $Pattern = ""
    )

    if (-not $VideoLink) {
        Write-Error "Uso: yt [-t] [-Comments] [-Metadata] [-Pattern <nome>] <url-youtube>"
        return
    }

    $flags = @("-y", $VideoLink, "--transcript")
    if ($t)        { $flags = @("-y", $VideoLink, "--transcript-with-timestamps") }
    if ($Comments) { $flags += "--comments" }
    if ($Metadata) { $flags += "--metadata" }
    if ($Pattern)  { $flags += @("--pattern", $Pattern) }

    fabric @flags
}

# ── Função `clip-fabric`: pipe da área de transferência para fabric ───────────
function global:clip-fabric {
    [CmdletBinding()]
    param(
        [Parameter(Position = 0)]
        [string] $Pattern = "summarize",

        [string] $Strategy = "",

        [switch] $Stream
    )
    $content = Get-Clipboard
    if (-not $content) { Write-Error "Área de transferência vazia."; return }

    $args = @("--pattern", $Pattern)
    if ($Strategy) { $args += @("--strategy", $Strategy) }
    if ($Stream)   { $args += "--stream" }

    $content | fabric @args
}

# ── Alias pbpaste (compatibilidade macOS) ─────────────────────────────────────
if (-not (Get-Alias pbpaste -ErrorAction SilentlyContinue)) {
    Set-Alias -Name pbpaste -Value Get-Clipboard -Scope Global
}

if (-not $Quiet) {
    Write-Host "✓ $count aliases de patterns Fabric carregados." -ForegroundColor Green
    Write-Host "  Funções extras: yt, clip-fabric, pbpaste" -ForegroundColor Cyan
    Write-Host "  Exemplo: Get-Clipboard | summarize" -ForegroundColor DarkGray
    Write-Host "  Exemplo: yt https://youtu.be/xxxx | extract_wisdom" -ForegroundColor DarkGray
}
