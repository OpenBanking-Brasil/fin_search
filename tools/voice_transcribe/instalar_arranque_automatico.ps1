# Instala arranque com o Windows: atalho em Inicializar + tarefa agendada ao iniciar sessão.
# Executar uma vez (duplo clique no .bat). Requer PowerShell.
$ErrorActionPreference = "Stop"
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$bat = Join-Path $dir "iniciar_transcricao.bat"
if (-not (Test-Path -LiteralPath $bat)) {
    Write-Error "Nao encontrado: $bat"
    exit 1
}

# 1) Atalho na pasta Inicializar do utilizador
$startup = [Environment]::GetFolderPath("Startup")
$lnk = Join-Path $startup "TranscricaoPTT.lnk"
$w = New-Object -ComObject WScript.Shell
$s = $w.CreateShortcut($lnk)
$s.TargetPath = $bat
$s.WorkingDirectory = $dir
$s.WindowStyle = 7
$s.Description = "Transcricao PTT (Whisper) — mesmo serviço que o asterisco"
$s.Save()
Write-Host "Atalho criado: $lnk"

# 2) Tarefa agendada (reforço: corre ao iniciar sessão, mesmo se a pasta Inicializar falhar)
$taskName = "FinSearch-TranscricaoPTT"
try {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue | Out-Null
} catch {}

$action = New-ScheduledTaskAction -Execute $bat -WorkingDirectory $dir
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited
try {
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
    Write-Host "Tarefa agendada registada: $taskName (ao iniciar sessao)"
} catch {
    Write-Warning "Tarefa agendada nao registada (o atalho em Inicializar pode bastar): $_"
}
Write-Host "Concluido. A transcricao ficara ativa apos reiniciar ou ao proximo inicio de sessao."
Write-Host "O programa corre em segundo plano (icone na bandeja); o asterisco continua a funcionar."
