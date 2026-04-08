<#
.SYNOPSIS
    Configura recursos avançados do Fabric no perfil PowerShell:
    aliases automáticos de patterns, mapeamento de modelo por pattern,
    aliases de clipboard, alias 'yt' para YouTube, e notificação desktop.

.DESCRIPTION
    Execute uma vez para persistir configurações no $PROFILE do PowerShell.
    Idempotente: só adiciona blocos que ainda não existam no perfil.

.EXAMPLE
    .\setup_fabric_advanced.ps1
    .\setup_fabric_advanced.ps1 -Force   # sobrescreve blocos existentes
#>
param(
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── Helpers ──────────────────────────────────────────────────────────────────
function Write-Step($msg) { Write-Host "  → $msg" -ForegroundColor Cyan }
function Write-Done($msg) { Write-Host "  ✓ $msg" -ForegroundColor Green }
function Write-Skip($msg) { Write-Host "  ~ $msg" -ForegroundColor DarkGray }

function Add-ProfileBlock {
    param(
        [string]$Marker,
        [string]$Block,
        [string]$ProfilePath,
        [switch]$Force
    )
    $content = if (Test-Path $ProfilePath) { Get-Content $ProfilePath -Raw } else { "" }
    if ($content -match [regex]::Escape($Marker)) {
        if ($Force) {
            # Remove bloco existente para reescrever
            $content = $content -replace "(?s)# <<$Marker>>.*?# <</$Marker>>", ""
            Write-Step "Sobrescrevendo bloco '$Marker'..."
        } else {
            Write-Skip "Bloco '$Marker' já existe no perfil. Use -Force para sobrescrever."
            return
        }
    }
    $fullBlock = @"

# <<$Marker>>
$Block
# <</$Marker>>
"@
    Add-Content -Path $ProfilePath -Value $fullBlock -Encoding UTF8
    Write-Done "Bloco '$Marker' adicionado ao perfil."
}

# ── Detectar perfil ───────────────────────────────────────────────────────────
if (-not $PROFILE) {
    Write-Error "Variável `$PROFILE não definida. Execute no PowerShell 5+ ou Core."
    exit 1
}
$profileDir = Split-Path $PROFILE -Parent
if (-not (Test-Path $profileDir)) {
    New-Item -ItemType Directory -Path $profileDir -Force | Out-Null
}
if (-not (Test-Path $PROFILE)) {
    New-Item -ItemType File -Path $PROFILE -Force | Out-Null
    Write-Step "Criado novo perfil em: $PROFILE"
}
Write-Host "`nConfiguração avançada do Fabric" -ForegroundColor Magenta
Write-Host "Perfil PowerShell: $PROFILE`n" -ForegroundColor DarkGray

# ── 1. Aliases automáticos de patterns ───────────────────────────────────────
$aliasBlock = @'
# Aliases automáticos para todos os patterns do Fabric
# Use: summarize "texto"  ou  Get-Clipboard | summarize
$_fabricPatternsPath = Join-Path $HOME ".config\fabric\patterns"
if (Test-Path $_fabricPatternsPath) {
    foreach ($patDir in Get-ChildItem -Path $_fabricPatternsPath -Directory) {
        $patName  = $patDir.Name
        $prefix   = if ($env:FABRIC_ALIAS_PREFIX) { $env:FABRIC_ALIAS_PREFIX } else { "" }
        $aliasName = "$prefix$patName"
        $funcDef = @"
function global:$aliasName {
    [CmdletBinding()]
    param(
        [Parameter(ValueFromPipeline=`$true)][string]`$InputObject,
        [Parameter(ValueFromRemainingArguments=`$true)][string[]]`$ExtraArgs
    )
    begin   { `$buf = @() }
    process { if (`$InputObject) { `$buf += `$InputObject } }
    end {
        `$pipe = `$buf -join "`n"
        if (`$pipe) { `$pipe | fabric --pattern $patName @`$ExtraArgs }
        else        { fabric --pattern $patName @`$ExtraArgs }
    }
}
"@
        Invoke-Expression $funcDef
    }
}
'@

Add-ProfileBlock -Marker "fabric-pattern-aliases" -Block $aliasBlock -ProfilePath $PROFILE -Force:$Force

# ── 2. Alias pbpaste (equivalente macOS) ─────────────────────────────────────
$pbpasteBlock = @'
# pbpaste — equivalente ao macOS para colar conteúdo da área de transferência
function global:pbpaste { Get-Clipboard }
function global:pbcopy  { param([Parameter(ValueFromPipeline=$true)][string]$text)
    process { $text | Set-Clipboard } }
'@
Add-ProfileBlock -Marker "fabric-pbpaste" -Block $pbpasteBlock -ProfilePath $PROFILE -Force:$Force

# ── 3. Alias 'yt' para YouTube ────────────────────────────────────────────────
$ytBlock = @'
# yt — extrai transcript/comentários de vídeos do YouTube via Fabric
function global:yt {
    [CmdletBinding()]
    param(
        [Alias("timestamps")][switch]$t,
        [Parameter(Position=0, ValueFromPipeline=$true)][string]$VideoLink
    )
    begin   { $flag = if ($t) { "--transcript-with-timestamps" } else { "--transcript" } }
    process {}
    end {
        if (-not $VideoLink) { Write-Error "Uso: yt [-t] <youtube-url>"; return }
        fabric -y $VideoLink $flag
    }
}
'@
Add-ProfileBlock -Marker "fabric-yt-alias" -Block $ytBlock -ProfilePath $PROFILE -Force:$Force

# ── 4. Variáveis FABRIC_MODEL_PATTERN_NAME ────────────────────────────────────
$modelMapBlock = @'
# Mapeamento de modelo por pattern: FABRIC_MODEL_PATTERN_<PATTERN>=vendor|model
# Exemplos:
#   $env:FABRIC_MODEL_PATTERN_EXPLAIN_CODE = "google|gemini-1.5-pro"
#   $env:FABRIC_MODEL_PATTERN_SUMMARIZE    = "google|gemini-2.0-flash"
# Descomente e ajuste conforme seus providers:
# $env:FABRIC_MODEL_PATTERN_EXPLAIN_CODE   = "google|gemini-2.0-flash"
# $env:FABRIC_MODEL_PATTERN_EXTRACT_WISDOM = "google|gemini-1.5-pro"
# $env:FABRIC_MODEL_PATTERN_REVIEW_CODE    = "anthropic|claude-opus-4-5"
# $env:FABRIC_MODEL_PATTERN_ANALYZE_CLAIMS = "openai|gpt-4o"
'@
Add-ProfileBlock -Marker "fabric-model-mapping" -Block $modelMapBlock -ProfilePath $PROFILE -Force:$Force

# ── 5. Notificação desktop ao concluir comando ────────────────────────────────
$notifyBlock = @'
# fabric-notify: envolve fabric com notificação desktop ao finalizar
# Uso: fabric-notify --pattern summarize --stream
function global:fabric-notify {
    [CmdletBinding()]
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$FabricArgs)
    begin { $in = @() }
    process { $in += $input }
    end {
        try {
            if ($in) { $in -join "`n" | fabric @FabricArgs }
            else      { fabric @FabricArgs }
        } finally {
            try {
                [Windows.UI.Notifications.ToastNotificationManager,Windows.UI.Notifications,ContentType=WindowsRuntime] | Out-Null
                $xml = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent(
                    [Windows.UI.Notifications.ToastTemplateType]::ToastText01)
                $xml.GetElementsByTagName("text")[0].InnerText = "Fabric: comando concluído"
                $toast = [Windows.UI.Notifications.ToastNotification]::new($xml)
                [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier("Fabric").Show($toast)
            } catch { <# notificação não disponível, ignorar #> }
        }
    }
}
'@
Add-ProfileBlock -Marker "fabric-notification" -Block $notifyBlock -ProfilePath $PROFILE -Force:$Force

# ── 6. Dry-run helper ─────────────────────────────────────────────────────────
$dryRunBlock = @'
# fabric-preview: modo dry-run — mostra o prompt sem chamar a API
# Uso: echo "teste" | fabric-preview -p summarize
function global:fabric-preview {
    [CmdletBinding()]
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$FabricArgs)
    process { $input | fabric --dry-run @FabricArgs }
}
'@
Add-ProfileBlock -Marker "fabric-dryrun" -Block $dryRunBlock -ProfilePath $PROFILE -Force:$Force

# ── 7. Search helper ─────────────────────────────────────────────────────────
$searchBlock = @'
# fabric-search: ativa busca web integrada (--search)
# Uso: fabric-search -p analyze_claims "o que é open finance?"
function global:fabric-search {
    [CmdletBinding()]
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$FabricArgs)
    process { $input | fabric --search @FabricArgs }
}
'@
Add-ProfileBlock -Marker "fabric-search-helper" -Block $searchBlock -ProfilePath $PROFILE -Force:$Force

# ── Resumo ────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "Configuração concluída!" -ForegroundColor Green
Write-Host "Reinicie o PowerShell ou execute: . `$PROFILE" -ForegroundColor Yellow
Write-Host ""
Write-Host "Funções adicionadas:" -ForegroundColor Cyan
Write-Host "  <pattern>          Aliases de todos os patterns (ex: summarize, extract_wisdom)"
Write-Host "  yt                 Extrair transcript do YouTube"
Write-Host "  pbpaste / pbcopy   Clipboard helpers"
Write-Host "  fabric-notify      fabric com notificação desktop"
Write-Host "  fabric-preview     Dry-run (ver prompt sem chamar API)"
Write-Host "  fabric-search      fabric com busca web integrada"
Write-Host ""
Write-Host "Exemplo de uso:" -ForegroundColor DarkCyan
Write-Host '  pbpaste | summarize'
Write-Host '  yt https://youtube.com/watch?v=xxx | extract_wisdom'
Write-Host '  fabric-search -p analyze_claims "open finance brasil"'
Write-Host '  fabric-preview -p review_code < meu_codigo.py'
