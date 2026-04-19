# Inicia só o frontend ARCÁDIA OS (Vite). API em /api/* (Gemini no servidor).
# Preferência: use start_fabric_local_app.ps1 (porta 5199 por defeito).

$ErrorActionPreference = "Stop"

function Test-PortOpen {
    param([string]$HostName, [int]$Port)
    try {
        $conn = Test-NetConnection -ComputerName $HostName -Port $Port -WarningAction SilentlyContinue
        return [bool]$conn.TcpTestSucceeded
    } catch {
        return $false
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$webDir = Join-Path $repoRoot "tools\Fabric\web"
$port = 5183

if (-not (Test-PortOpen -HostName "127.0.0.1" -Port $port)) {
    $frontendCmd = @"
Set-Location "$webDir"
New-Item -ItemType Directory -Force "static/data" | Out-Null
Copy-Item "..\scripts\pattern_descriptions\pattern_descriptions.json" "static/data\pattern_descriptions.json" -Force
npx vite dev --host 127.0.0.1 --port $port
"@
    Start-Process powershell -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", $frontendCmd
    )
}

Start-Sleep -Seconds 2
Start-Process "http://127.0.0.1:$port"
