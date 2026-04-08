# ARCÁDIA OS - Script de Setup Completo
# Este script configura todo o ambiente do ARCÁDIA OS

param(
    [string]$SupabaseUrl = "",
    [string]$SupabaseKey = "",
    [switch]$SkipFabric = $false,
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
  -SkipFabric      Pula a configuração do Fabric backend
  -Help            Mostra esta ajuda

Exemplo:
  .\setup_arcadia_os.ps1 -SupabaseUrl "https://abc123.supabase.co" -SupabaseKey "eyJ..."

"@
    exit 0
}

Write-Host "🏛️  ARCÁDIA OS - Setup Completo" -ForegroundColor Cyan
Write-Host "Sistema Operacional de Decisão Autônoma" -ForegroundColor Gray
Write-Host ""

# Verificar se estamos no diretório correto
$currentDir = Get-Location
$webDir = Join-Path $currentDir "tools\Fabric\web"

if (!(Test-Path $webDir)) {
    Write-Host "❌ Erro: Execute este script na raiz do projeto (onde está a pasta tools/Fabric/web)" -ForegroundColor Red
    exit 1
}

Write-Host "📁 Diretório do projeto: $currentDir" -ForegroundColor Green
Write-Host "📁 Diretório web: $webDir" -ForegroundColor Green
Write-Host ""

# 1. Configurar Frontend
Write-Host "🔧 Configurando Frontend SvelteKit..." -ForegroundColor Yellow

Set-Location $webDir

# Verificar se Node.js está instalado
try {
    $nodeVersion = node --version
    Write-Host "✅ Node.js encontrado: $nodeVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Node.js não encontrado. Instale Node.js 18+ antes de continuar." -ForegroundColor Red
    exit 1
}

# Instalar dependências
Write-Host "📦 Instalando dependências npm..." -ForegroundColor Blue
npm install
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Erro ao instalar dependências npm" -ForegroundColor Red
    exit 1
}

# Configurar arquivo .env
Write-Host "⚙️  Configurando arquivo .env..." -ForegroundColor Blue

$envFile = Join-Path $webDir ".env"
$envExampleFile = Join-Path $webDir ".env.example"

if (Test-Path $envFile) {
    Write-Host "⚠️  Arquivo .env já existe. Fazendo backup..." -ForegroundColor Yellow
    Copy-Item $envFile "$envFile.backup.$(Get-Date -Format 'yyyyMMdd-HHmmss')"
}

# Criar .env baseado no exemplo
if (Test-Path $envExampleFile) {
    Copy-Item $envExampleFile $envFile
    Write-Host "✅ Arquivo .env criado baseado no .env.example" -ForegroundColor Green
} else {
    # Criar .env básico
    $envContent = @"
# ARCÁDIA OS - Configuração de Ambiente
VITE_SUPABASE_URL=https://your-project.supabase.co
VITE_SUPABASE_PUBLISHABLE_KEY=your-publishable-key
VITE_SUPABASE_ANON_KEY=your-anon-key
VITE_FABRIC_BASE_URL=http://127.0.0.1:18080
"@
    Set-Content -Path $envFile -Value $envContent
    Write-Host "✅ Arquivo .env básico criado" -ForegroundColor Green
}

# Atualizar .env com parâmetros fornecidos
if ($SupabaseUrl -and $SupabaseKey) {
    Write-Host "🔑 Atualizando credenciais do Supabase..." -ForegroundColor Blue
    
    $envContent = Get-Content $envFile
    $envContent = $envContent -replace "VITE_SUPABASE_URL=.*", "VITE_SUPABASE_URL=$SupabaseUrl"
    $envContent = $envContent -replace "VITE_SUPABASE_PUBLISHABLE_KEY=.*", "VITE_SUPABASE_PUBLISHABLE_KEY=$SupabaseKey"
    $envContent = $envContent -replace "VITE_SUPABASE_ANON_KEY=.*", "VITE_SUPABASE_ANON_KEY=$SupabaseKey"
    
    Set-Content -Path $envFile -Value $envContent
    Write-Host "✅ Credenciais do Supabase atualizadas" -ForegroundColor Green
} else {
    Write-Host "⚠️  Lembre-se de editar o arquivo .env com suas credenciais do Supabase" -ForegroundColor Yellow
}

# 2. Configurar Fabric Backend (se não for pulado)
if (!$SkipFabric) {
    Write-Host ""
    Write-Host "🤖 Configurando Fabric Backend..." -ForegroundColor Yellow
    
    Set-Location $currentDir
    
    # Verificar se o Fabric está instalado
    try {
        $fabricVersion = fabric --version
        Write-Host "✅ Fabric encontrado: $fabricVersion" -ForegroundColor Green
    } catch {
        Write-Host "⚠️  Fabric não encontrado. Tentando instalar..." -ForegroundColor Yellow
        
        # Tentar instalar via winget
        try {
            winget install danielmiessler.Fabric
            Write-Host "✅ Fabric instalado via winget" -ForegroundColor Green
        } catch {
            Write-Host "❌ Erro ao instalar Fabric. Instale manualmente: https://github.com/danielmiessler/fabric" -ForegroundColor Red
        }
    }
    
    # Configurar Fabric
    Write-Host "⚙️  Configurando Fabric..." -ForegroundColor Blue
    try {
        fabric --setup
        Write-Host "✅ Fabric configurado" -ForegroundColor Green
    } catch {
        Write-Host "⚠️  Execute 'fabric --setup' manualmente para configurar" -ForegroundColor Yellow
    }
}

# 3. Verificar scripts auxiliares
Write-Host ""
Write-Host "📜 Verificando scripts auxiliares..." -ForegroundColor Yellow

$scriptsToCheck = @(
    "tools\start_fabric_local_app.ps1",
    "tools\fabric_aliases.ps1",
    "tools\fabric_update.ps1"
)

foreach ($script in $scriptsToCheck) {
    $scriptPath = Join-Path $currentDir $script
    if (Test-Path $scriptPath) {
        Write-Host "✅ $script encontrado" -ForegroundColor Green
    } else {
        Write-Host "⚠️  $script não encontrado" -ForegroundColor Yellow
    }
}

# 4. Instruções finais
Write-Host ""
Write-Host "🎉 Setup do ARCÁDIA OS concluído!" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Próximos passos:" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Configure seu projeto Supabase:" -ForegroundColor White
Write-Host "   - Crie um projeto em https://supabase.com" -ForegroundColor Gray
Write-Host "   - Execute as migrações SQL da pasta supabase/migrations/" -ForegroundColor Gray
Write-Host "   - Atualize o arquivo .env com suas credenciais" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Inicie o backend Fabric:" -ForegroundColor White
Write-Host "   .\tools\start_fabric_local_app.ps1" -ForegroundColor Gray
Write-Host ""
Write-Host "3. Inicie o frontend:" -ForegroundColor White
Write-Host "   cd tools\Fabric\web" -ForegroundColor Gray
Write-Host "   npm run dev" -ForegroundColor Gray
Write-Host ""
Write-Host "4. Acesse http://localhost:5173 e faça login!" -ForegroundColor White
Write-Host ""
Write-Host "📚 Documentação completa: ARCADIA_README.md" -ForegroundColor Cyan
Write-Host ""
Write-Host "🏛️  Bem-vindo ao ARCÁDIA OS!" -ForegroundColor Magenta

# Voltar ao diretório original
Set-Location $currentDir