param(
    [string]$JarPath = (Join-Path $PSScriptRoot "..\runtime\cost-lite-server-1.0.0.jar")
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
    throw "Cost Lite Oracle Jar not found: $JarPath"
}

$username = $env:COST_LITE_DB_USERNAME
$password = $env:COST_LITE_DB_PASSWORD
if ([string]::IsNullOrWhiteSpace($username)) {
    throw "Required environment variable is missing: COST_LITE_DB_USERNAME"
}
if ([string]::IsNullOrWhiteSpace($password)) {
    throw "Required environment variable is missing: COST_LITE_DB_PASSWORD"
}

$serverPort = if ([string]::IsNullOrWhiteSpace($env:COST_LITE_SERVER_PORT)) {
    "18082"
} else {
    $env:COST_LITE_SERVER_PORT
}

$dbUrl = $env:COST_LITE_DB_URL
if ([string]::IsNullOrWhiteSpace($dbUrl)) {
    $dbHost = if ([string]::IsNullOrWhiteSpace($env:COST_LITE_DB_HOST)) { "127.0.0.1" } else { $env:COST_LITE_DB_HOST }
    $dbPort = if ([string]::IsNullOrWhiteSpace($env:COST_LITE_DB_PORT)) { "1521" } else { $env:COST_LITE_DB_PORT }
    $dbService = $env:COST_LITE_DB_SERVICE
    if ([string]::IsNullOrWhiteSpace($dbService)) {
        $dbService = $env:COST_LITE_DB_NAME
    }
    if ([string]::IsNullOrWhiteSpace($dbService)) {
        $dbService = "FREEPDB1"
    }
    $dbUrl = "jdbc:oracle:thin:@//$dbHost`:$dbPort/$dbService"
}

$env:SPRING_PROFILES_ACTIVE = "oracle"
$env:COST_LITE_SERVER_PORT = $serverPort
$env:COST_LITE_DB_DRIVER = if ([string]::IsNullOrWhiteSpace($env:COST_LITE_DB_DRIVER)) {
    "oracle.jdbc.OracleDriver"
} else {
    $env:COST_LITE_DB_DRIVER
}
$env:COST_LITE_DB_URL = $dbUrl

$java = (Get-Command java -ErrorAction Stop).Source
$resolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
Write-Output "Starting Cost Lite on port $serverPort with Oracle database URL $dbUrl..."
& $java -jar $resolvedJar
exit $LASTEXITCODE
