param(
    [string]$JarPath = (Join-Path $PSScriptRoot "..\runtime\cost-lite-server-1.0.0.jar")
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
    throw "Cost Lite Jar not found: $JarPath"
}

$required = @(
    "COST_LITE_DB_HOST",
    "COST_LITE_DB_PORT",
    "COST_LITE_DB_NAME",
    "COST_LITE_DB_USERNAME",
    "COST_LITE_DB_PASSWORD"
)

foreach ($name in $required) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required environment variable is missing: $name"
    }
}

$serverPort = if ([string]::IsNullOrWhiteSpace($env:COST_LITE_SERVER_PORT)) { "18080" } else { $env:COST_LITE_SERVER_PORT }
$env:SPRING_PROFILES_ACTIVE = "mysql"
$env:COST_LITE_SERVER_PORT = $serverPort
$env:COST_LITE_DB_DRIVER = "com.mysql.cj.jdbc.Driver"
$env:COST_LITE_DB_URL = "jdbc:mysql://$($env:COST_LITE_DB_HOST):$($env:COST_LITE_DB_PORT)/$($env:COST_LITE_DB_NAME)?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"

$java = (Get-Command java -ErrorAction Stop).Source
$resolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
if ([string]::IsNullOrWhiteSpace($env:COST_LITE_LOG_PATH)) {
    $env:COST_LITE_LOG_PATH = Join-Path (Split-Path -Parent $resolvedJar) "logs"
}
Write-Output "Starting Cost Lite on port $serverPort with MySQL database $($env:COST_LITE_DB_NAME)..."
& $java -jar $resolvedJar
exit $LASTEXITCODE
