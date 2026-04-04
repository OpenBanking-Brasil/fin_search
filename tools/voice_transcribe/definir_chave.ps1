# Grava a chave OpenAI em .openai_key (local, não vai para o Git).
# Uso interactivo:  powershell -ExecutionPolicy Bypass -File .\definir_chave.ps1
# Uso com parâmetro: powershell -File .\definir_chave.ps1 -Key "sk-..."
param(
    [string]$Key = ""
)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$path = Join-Path $here ".openai_key"

if (-not $Key) {
    Write-Host "Cole a chave (começa por sk-). A entrada fica oculta."
    $sec = Read-Host -AsSecureString
    if ($null -eq $sec -or $sec.Length -eq 0) {
        Write-Error "Nenhuma chave introduzida."
        exit 1
    }
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
    try {
        # PtrToStringUni funciona em Windows PowerShell 5.1 e PowerShell 7+
        $Key = [Runtime.InteropServices.Marshal]::PtrToStringUni($ptr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

$Key = $Key.Trim()
if (-not $Key.StartsWith("sk-")) {
    Write-Error "Chave inválida: deve começar por sk-"
    exit 1
}

$enc = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($path, $Key, $enc)

Write-Host "Chave guardada. Ficheiro: $path"
