# Remove atalho de Inicializar e tarefa agendada.
$startup = [Environment]::GetFolderPath("Startup")
$lnk = Join-Path $startup "TranscricaoPTT.lnk"
if (Test-Path -LiteralPath $lnk) {
    Remove-Item -LiteralPath $lnk -Force
    Write-Host "Removido atalho: $lnk"
} else {
    Write-Host "Atalho nao existia em Inicializar."
}

$taskName = "FinSearch-TranscricaoPTT"
try {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction Stop
    Write-Host "Removida tarefa agendada: $taskName"
} catch {
    Write-Host "Tarefa agendada nao existia ou ja foi removida."
}
