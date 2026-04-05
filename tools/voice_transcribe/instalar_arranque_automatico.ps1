# Atalho na pasta Inicializar do utilizador atual (arranca ao iniciar sessao).
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$bat = Join-Path $dir "iniciar_transcricao.bat"
if (-not (Test-Path -LiteralPath $bat)) {
    Write-Error "Nao encontrado: $bat"
    exit 1
}
$startup = [Environment]::GetFolderPath("Startup")
$lnk = Join-Path $startup "TranscricaoPTT.lnk"
$w = New-Object -ComObject WScript.Shell
$s = $w.CreateShortcut($lnk)
$s.TargetPath = $bat
$s.WorkingDirectory = $dir
$s.WindowStyle = 7
$s.Description = "Transcricao PTT (Whisper)"
$s.Save()
Write-Host "Arranque automatico instalado: $lnk"
