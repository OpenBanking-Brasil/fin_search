# ARCÁDIA OS - Script de Setup (projeto fin_search)
# Configura o frontend em tools/Fabric/web. A API de IA usa Gemini nas rotas SvelteKit (/api/*).

param(
    [string]$SupabaseUrl = "",
    [string]$SupabaseKey = "",
    [switch]$Help = $false
)

if ($Help) {
    Write-Host @"
ARCÁDIA OS - Setup Script

Uso:
  .\setup_arcadia_os.ps1 -SupabaseUrl "https://xxx.supabase.co" -SupabaseKey "your-key"

Parâmetros:
  -SupabaseUrl     URL do projeto Supabase
  -SupabaseKey     Chave pública/anon do Supabase
  -Help            Mostra esta ajuda

Nota: a chave Google Gemini (ARCADIA_GEMINI_API_KEY) deve ir no .env do servidor — não use prefixo VITE_.

Exemplo:
  .\setup_arcadia_os.ps1 -SupabaseUrl "https://abc123.supabase.co" -SupabaseKey "eyJ..."

"@
    exit 0
}

Write-Host "ARCÁDIA OS - Setup" -ForegroundColor Cyan
Write-Host ""

$currentDir = Get-Location
$webDir = Join-Path $currentDir "tools\Fabric\web"

if (!(Test-Path $webDir)) {
    Write-Host "Erro: execute na raiz do repositório (pasta tools/Fabric/web em falta)." -ForegroundColor Red
    exit 1
}

Write-Host "Diretório web: $webDir" -ForegroundColor Green
Write-Host ""

Write-Host "Configurando frontend SvelteKit..." -ForegroundColor Yellow
Set-Location $webDir

try {
    $nodeVersion = node --version
    Write-Host "Node.js: $nodeVersion" -ForegroundColor Green
} catch {
    Write-Host "Node.js não encontrado. Instale Node.js 18+." -ForegroundColor Red
    exit 1
}

Write-Host "npm install..." -ForegroundColor Blue
npm install
if ($LASTEXITCODE -ne 0) {
    Write-Host "Erro ao instalar dependências." -ForegroundColor Red
    exit 1
}

$envFile = Join-Path $webDir ".env"
$envExampleFile = Join-Path $webDir "env.example"

if (Test-Path $envFile) {
    Write-Host ".env já existe — backup..." -ForegroundColor Yellow
    Copy-Item $envFile "$envFile.backup.$(Get-Date -Format 'yyyyMMdd-HHmmss')"
}

if (Test-Path $envExampleFile) {
    Copy-Item $envExampleFile $envFile
    Write-Host ".env criado a partir de env.example" -ForegroundColor Green
} else {
    $envContent = @"
# ARCÁDIA OS
VITE_SUPABASE_URL=https://your-project.supabase.co
VITE_SUPABASE_PUBLISHABLE_KEY=your-publishable-key
ARCADIA_GEMINI_API_KEY=
"@
    Set-Content -Path $envFile -Value $envContent
    Write-Host ".env básico criado" -ForegroundColor Green
}

if ($SupabaseUrl -and $SupabaseKey) {
    Write-Host "A atualizar credenciais Supabase..." -ForegroundColor Blue
    $envContent = Get-Content $envFile -Raw
    $envContent = $envContent -replace "(?m)^VITE_SUPABASE_URL=.*", "VITE_SUPABASE_URL=$SupabaseUrl"
    $envContent = $envContent -replace "(?m)^VITE_SUPABASE_PUBLISHABLE_KEY=.*", "VITE_SUPABASE_PUBLISHABLE_KEY=$SupabaseKey"
    $envContent = $envContent -replace "(?m)^VITE_SUPABASE_ANON_KEY=.*", "VITE_SUPABASE_ANON_KEY=$SupabaseKey"
    Set-Content -Path $envFile -Value $envContent.TrimEnd()
    Write-Host "Supabase atualizado no .env" -ForegroundColor Green
} else {
    Write-Host "Edite .env com Supabase e ARCADIA_GEMINI_API_KEY (servidor)." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Scripts úteis:" -ForegroundColor Cyan
Write-Host "  .\tools\start_fabric_local_app.ps1   # inicia o Vite (frontend + /api)" -ForegroundColor Gray
Write-Host ""
Write-Host "Setup concluído." -ForegroundColor Green

Set-Location $currentDir
