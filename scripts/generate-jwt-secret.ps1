Write-Host "Gerando JWT Secret seguro..." -ForegroundColor Green
Write-Host ""

$bytes = New-Object byte[] 64
$rng = [System.Security.Cryptography.RNGCryptoServiceProvider]::new()
$rng.GetBytes($bytes)
$rng.Dispose()
$jwtSecret = [System.Convert]::ToBase64String($bytes)

Write-Host "JWT_SECRET gerado:" -ForegroundColor Yellow
Write-Host $jwtSecret -ForegroundColor Cyan
Write-Host ""
Write-Host "Configure como variavel de ambiente JWT_SECRET no Koyeb Dashboard." -ForegroundColor Green
Write-Host ""
Write-Host "Para testar localmente, adicione ao seu .env ou application-local.yaml:" -ForegroundColor Blue
Write-Host "JWT_SECRET=$jwtSecret" -ForegroundColor Gray

try {
    Set-Clipboard -Value $jwtSecret
    Write-Host ""
    Write-Host "JWT Secret copiado para a area de transferencia!" -ForegroundColor Green
} catch {
    Write-Host ""
    Write-Host "Nao foi possivel copiar automaticamente. Copie o valor manualmente." -ForegroundColor Yellow
}