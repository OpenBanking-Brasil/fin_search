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

$repoRoot = "D:\Users\caioc\Documents\GitHub\fin_search"
$webDir = Join-Path $repoRoot "tools\Fabric\web"

if (-not (Test-PortOpen -HostName "127.0.0.1" -Port 18080)) {
    Start-Process powershell -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", "`"`" | fabric --serve --address :18080"
    )
}

if (-not (Test-PortOpen -HostName "127.0.0.1" -Port 5183)) {
    $frontendCmd = @"
Set-Location "$webDir"
New-Item -ItemType Directory -Force "static/data" | Out-Null
Copy-Item "..\scripts\pattern_descriptions\pattern_descriptions.json" "static/data\pattern_descriptions.json" -Force
`$env:FABRIC_BASE_URL = "http://127.0.0.1:18080"
npx vite dev --host 127.0.0.1 --port 5183
"@
    Start-Process powershell -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", $frontendCmd
    )
}

Start-Sleep -Seconds 2
Start-Process "http://127.0.0.1:5183"
