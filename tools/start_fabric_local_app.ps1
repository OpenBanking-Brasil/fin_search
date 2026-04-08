$ErrorActionPreference = "Stop"

function Test-PortOpen {
    param(
        [string]$HostName,
        [int]$Port
    )
    try {
        $conn = Test-NetConnection -ComputerName $HostName -Port $Port -WarningAction SilentlyContinue
        return [bool]$conn.TcpTestSucceeded
    } catch {
        return $false
    }
}

function Start-Frontend {
    param(
        [string]$RepoRoot,
        [string]$WebDir,
        [int]$Port,
        [int]$BackendPort
    )

    $frontendCmd = @"
Set-Location "$WebDir"
New-Item -ItemType Directory -Force "static/data" | Out-Null
Copy-Item "..\scripts\pattern_descriptions\pattern_descriptions.json" "static/data\pattern_descriptions.json" -Force
`$env:FABRIC_BASE_URL = "http://127.0.0.1:$BackendPort"
npx vite dev --host 127.0.0.1 --port $Port
"@

    Start-Process powershell -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", $frontendCmd
    ) -WorkingDirectory $RepoRoot | Out-Null
}

$repoRoot = "D:\Users\caioc\Documents\GitHub\fin_search"
$webDir = Join-Path $repoRoot "tools\Fabric\web"
$backendPort = 18080
$frontendPort = 5199
$appUrl = "http://127.0.0.1:$frontendPort/chat"

# Backend
if (-not (Test-PortOpen -HostName "127.0.0.1" -Port $backendPort)) {
    Start-Process powershell -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", "`"`" | fabric --serve --address :$backendPort"
    ) -WorkingDirectory $repoRoot | Out-Null
}

# Frontend
if (-not (Test-PortOpen -HostName "127.0.0.1" -Port $frontendPort)) {
    Start-Frontend -RepoRoot $repoRoot -WebDir $webDir -Port $frontendPort -BackendPort $backendPort
}

# Wait briefly for app startup
Start-Sleep -Seconds 3

# Launch as local app window
if (Get-Command msedge -ErrorAction SilentlyContinue) {
    Start-Process "msedge" "--app=$appUrl" | Out-Null
} elseif (Get-Command chrome -ErrorAction SilentlyContinue) {
    Start-Process "chrome" "--app=$appUrl" | Out-Null
} else {
    Start-Process $appUrl | Out-Null
}
