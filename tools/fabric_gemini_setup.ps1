$ErrorActionPreference = "Stop"

$envFile = Join-Path $HOME ".config\fabric\.env"
$envDir = Split-Path $envFile -Parent

New-Item -ItemType Directory -Path $envDir -Force | Out-Null
if (-not (Test-Path $envFile)) {
    New-Item -ItemType File -Path $envFile -Force | Out-Null
}

$geminiKey = Read-Host "Cole sua chave Gemini (GEMINI_API_KEY) e pressione Enter"
if ([string]::IsNullOrWhiteSpace($geminiKey)) {
    Write-Host "Chave vazia. Encerrando sem alterar configuracao." -ForegroundColor Yellow
    exit 1
}

$lines = Get-Content $envFile -ErrorAction SilentlyContinue
if ($null -eq $lines) {
    $lines = @()
}

$filtered = @($lines | Where-Object { $_ -notmatch "^GEMINI_API_KEY=" })
$filtered += "GEMINI_API_KEY=$geminiKey"

Set-Content -Path $envFile -Value $filtered -Encoding UTF8

"" | fabric --listpatterns | Out-Null
"teste local" | fabric --dry-run -V Gemini -m gemini-2.5-flash -p summarize | Out-Null

Write-Host ""
Write-Host "Configuracao concluida com sucesso." -ForegroundColor Green
Write-Host "Teste real agora com:"
Write-Host '"Explique juros compostos em 3 linhas" | fabric -V Gemini -m gemini-2.5-flash -p summarize -s'
Write-Host ""
Pause
