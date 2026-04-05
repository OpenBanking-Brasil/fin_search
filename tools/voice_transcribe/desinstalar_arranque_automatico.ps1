$startup = [Environment]::GetFolderPath("Startup")
$lnk = Join-Path $startup "TranscricaoPTT.lnk"
if (Test-Path -LiteralPath $lnk) {
    Remove-Item -LiteralPath $lnk -Force
    Write-Host "Removido: $lnk"
} else {
    Write-Host "Nada a remover (atalho nao existia)."
}
