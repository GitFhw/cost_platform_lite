[CmdletBinding()]
param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot),
    [switch]$SkipExample
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-ZipEntryText {
    param(
        [System.IO.Compression.ZipArchiveEntry]$Entry
    )

    $stream = $Entry.Open()
    $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8, $true)
    try {
        return $reader.ReadToEnd()
    } finally {
        $reader.Dispose()
        $stream.Dispose()
    }
}

function Get-ClassMajorVersion {
    param(
        [System.IO.Compression.ZipArchiveEntry]$Entry
    )

    $stream = $Entry.Open()
    try {
        $header = New-Object byte[] 8
        $read = $stream.Read($header, 0, $header.Length)
        if (($read -lt 8) -or ($header[0] -ne 0xCA) -or ($header[1] -ne 0xFE) -or
                ($header[2] -ne 0xBA) -or ($header[3] -ne 0xBE)) {
            return $null
        }
        return [int]$header[6] * 256 + [int]$header[7]
    } finally {
        $stream.Dispose()
    }
}

function Test-IsolatedJar {
    param(
        [string]$JarPath,
        [string]$Description
    )

    if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
        throw "$Description 不存在：$JarPath。请先执行对应数据库目录的 Maven package。"
    }

    $archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entries = @($archive.Entries)
        $classEntries = @($entries | Where-Object { $_.FullName.EndsWith(".class") })
        $originalClasses = @($classEntries | Where-Object { $_.FullName.StartsWith("com/ruoyi/") })
        $internalClasses = @($classEntries | Where-Object {
                $_.FullName.StartsWith("com/costplatform/lite/internal/ruoyi/")
            })

        if ($originalClasses.Count -gt 0) {
            throw "$Description 仍包含未隔离的 com/ruoyi 类：$($originalClasses[0].FullName)"
        }
        if ($internalClasses.Count -eq 0) {
            throw "$Description 未找到隔离后的计费核心类。"
        }

        $mapperEntries = @($entries | Where-Object {
                $_.FullName.StartsWith("cost-lite/mapper/") -and $_.FullName.EndsWith(".xml")
            })
        foreach ($mapper in $mapperEntries) {
            $content = Get-ZipEntryText $mapper
            if ($content.Contains("com.ruoyi.")) {
                throw "$Description 的 Mapper 仍引用原始命名空间：$($mapper.FullName)"
            }
        }

        $maxMajor = 0
        foreach ($class in $classEntries) {
            $major = Get-ClassMajorVersion $class
            if ($null -ne $major -and $major -gt $maxMajor) {
                $maxMajor = $major
            }
        }
        if ($maxMajor -gt 52) {
            throw "$Description 包含 Java $maxMajor 字节码，不能在 Java 8 运行。"
        }

        Write-Host ("通过：{0}；隔离类 {1} 个；Mapper {2} 个；最高 class major {3}" -f
            $Description, $internalClasses.Count, $mapperEntries.Count, $maxMajor)
    } finally {
        $archive.Dispose()
    }
}

function Test-StarterJar {
    param(
        [string]$JarPath,
        [string]$Description
    )

    if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
        throw "$Description 不存在：$JarPath。请先执行对应数据库目录的 Maven package。"
    }

    $archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $classEntries = @($archive.Entries | Where-Object { $_.FullName.EndsWith(".class") })
        $originalClasses = @($classEntries | Where-Object { $_.FullName.StartsWith("com/ruoyi/") })
        if ($originalClasses.Count -gt 0) {
            throw "$Description 仍包含未隔离的 com/ruoyi 类：$($originalClasses[0].FullName)"
        }
        $maxMajor = 0
        foreach ($class in $classEntries) {
            $major = Get-ClassMajorVersion $class
            if ($null -ne $major -and $major -gt $maxMajor) {
                $maxMajor = $major
            }
        }
        if ($maxMajor -gt 52) {
            throw "$Description 包含 Java $maxMajor 字节码，不能在 Java 8 运行。"
        }
        Write-Host ("通过：{0}；class {1} 个；最高 class major {2}" -f
            $Description, $classEntries.Count, $maxMajor)
    } finally {
        $archive.Dispose()
    }
}

function Test-RuntimeJar {
    param(
        [string]$JarPath,
        [string]$ChecksumPath,
        [string]$Description
    )

    if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
        throw "$Description 不存在：$JarPath"
    }
    if (-not (Test-Path -LiteralPath $ChecksumPath -PathType Leaf)) {
        throw "$Description 校验文件不存在：$ChecksumPath"
    }

    $checksumText = Get-Content -LiteralPath $ChecksumPath -Raw -Encoding UTF8
    $checksumMatch = [regex]::Match(
        $checksumText,
        '(?im)^\s*([0-9a-f]{64})\s+cost-lite-server-1\.0\.0\.jar\s*$'
    )
    if (-not $checksumMatch.Success) {
        throw "$Description 校验文件格式错误：$ChecksumPath"
    }

    $expectedHash = $checksumMatch.Groups[1].Value.ToUpperInvariant()
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $JarPath).Hash.ToUpperInvariant()
    if ($expectedHash -ne $actualHash) {
        throw "$Description SHA-256 不匹配：期望 $expectedHash，实际 $actualHash"
    }

    $archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $classEntries = @($archive.Entries | Where-Object { $_.FullName.EndsWith('.class') })
        $maxMajor = 0
        foreach ($class in $classEntries) {
            $major = Get-ClassMajorVersion $class
            if ($null -ne $major -and $major -gt $maxMajor) {
                $maxMajor = $major
            }
        }
        if ($maxMajor -gt 52) {
            throw "$Description 包含 Java $maxMajor 字节码，不能在 Java 8 运行。"
        }
        Write-Host ("通过：{0}；SHA-256 {1}；class {2} 个；最高 class major {3}" -f
            $Description, $actualHash, $classEntries.Count, $maxMajor)
    } finally {
        $archive.Dispose()
    }
}

function Get-CreatedTableNames {
    param(
        [string]$SqlPath
    )

    if (-not (Test-Path -LiteralPath $SqlPath -PathType Leaf)) {
        throw "SQL 文件不存在：$SqlPath"
    }

    $content = Get-Content -LiteralPath $SqlPath -Raw -Encoding UTF8
    $matches = [regex]::Matches(
        $content,
        '(?im)^\s*create\s+table\s+(?:if\s+not\s+exists\s+)?[`\"]?([a-z0-9_]+)[`\"]?\s*\('
    )
    return @($matches | ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } | Sort-Object -Unique)
}

function Test-SchemaBoundary {
    param(
        [string]$DatabaseDirectory,
        [string]$DatabaseName
    )

    $defaultPath = Join-Path $RepositoryRoot "$DatabaseDirectory\sql\cost-lite-schema.sql"
    $formalPath = Join-Path $RepositoryRoot "$DatabaseDirectory\sql\cost-lite-formal-schema.sql"
    $defaultTables = @(Get-CreatedTableNames $defaultPath)
    $formalTables = @(Get-CreatedTableNames $formalPath)

    $baseCostTables = @(
        "cost_scene",
        "cost_fee_item",
        "cost_variable_group",
        "cost_variable",
        "cost_fee_variable_rel",
        "cost_rule",
        "cost_rule_condition",
        "cost_rule_tier",
        "cost_formula",
        "cost_formula_version",
        "cost_publish_version",
        "cost_publish_snapshot",
        "cost_simulation_record",
        "cost_audit_log"
    )
    $dictionaryTables = @("sys_dict_type", "sys_dict_data")
    $formalCostTables = @(
        "cost_bill_period",
        "cost_recalc_order",
        "cost_alarm_record",
        "cost_access_profile",
        "cost_calc_input_batch",
        "cost_calc_input_batch_item",
        "cost_calc_task",
        "cost_calc_task_detail",
        "cost_calc_task_partition",
        "cost_result_ledger",
        "cost_result_trace",
        "cost_open_app"
    )

    $requiredDefault = @($baseCostTables + $dictionaryTables)
    $missingDefault = @($requiredDefault | Where-Object { $_ -notin $defaultTables })
    if ($missingDefault.Count -gt 0) {
        throw "$DatabaseName 默认轻量 SQL 缺少表：$($missingDefault -join ', ')"
    }

    $unexpectedDefaultCost = @($defaultTables | Where-Object {
            $_.StartsWith("cost_") -and $_ -notin $baseCostTables
        })
    if ($unexpectedDefaultCost.Count -gt 0) {
        throw "$DatabaseName 默认轻量 SQL 不应创建正式/开放表：$($unexpectedDefaultCost -join ', ')"
    }

    $missingFormal = @($formalCostTables | Where-Object { $_ -notin $formalTables })
    if ($missingFormal.Count -gt 0) {
        throw "$DatabaseName 可选正式/开放 SQL 缺少表：$($missingFormal -join ', ')"
    }

    $formalDictionaryReferences = @($formalTables | Where-Object { $_ -in $dictionaryTables })
    if ($formalDictionaryReferences.Count -gt 0) {
        throw "$DatabaseName 可选正式/开放 SQL 不应重复创建字典表：$($formalDictionaryReferences -join ', ')"
    }

    Write-Host ("通过：{0} SQL 边界；默认轻量表 {1} 张，可选正式/开放表 {2} 张，字典表 {3} 张" -f
        $DatabaseName, $baseCostTables.Count, $formalCostTables.Count, $dictionaryTables.Count)
}

Test-IsolatedJar (Join-Path $RepositoryRoot "Mysql\isolated\target\cost-lite-core-mysql-isolated-1.0.0.jar") `
    "MySQL 隔离核心 Jar"
Test-StarterJar (Join-Path $RepositoryRoot "Mysql\starter\target\cost-lite-starter-mysql-1.0.0.jar") `
    "MySQL Starter Jar"
Test-IsolatedJar (Join-Path $RepositoryRoot "Oracle\isolated\target\cost-lite-core-oracle-isolated-1.0.0.jar") `
    "Oracle 隔离核心 Jar"
Test-StarterJar (Join-Path $RepositoryRoot "Oracle\starter\target\cost-lite-starter-oracle-1.0.0.jar") `
    "Oracle Starter Jar"
Test-RuntimeJar (Join-Path $RepositoryRoot "Mysql\runtime\cost-lite-server-1.0.0.jar") `
    (Join-Path $RepositoryRoot "Mysql\runtime\SHA256SUMS") "MySQL 运行 Jar"
Test-RuntimeJar (Join-Path $RepositoryRoot "Oracle\runtime\cost-lite-server-1.0.0.jar") `
    (Join-Path $RepositoryRoot "Oracle\runtime\SHA256SUMS") "Oracle 运行 Jar"
Test-SchemaBoundary "Mysql" "MySQL"
Test-SchemaBoundary "Oracle" "Oracle"

if (-not $SkipExample) {
    foreach ($example in @(
            @{ Name = "MySQL 嵌入示例"; Path = "Mysql\example\target\cost-lite-embedded-example-1.0.0.jar" },
            @{ Name = "Oracle 嵌入示例"; Path = "Oracle\example\target\cost-lite-embedded-example-1.0.0.jar" }
        )) {
        if (-not (Test-Path -LiteralPath (Join-Path $RepositoryRoot $example.Path) -PathType Leaf)) {
            throw "$($example.Name)不存在：$($example.Path)。请先执行对应数据库目录的 Maven package。"
        }
        Write-Host "存在：$($example.Name)"
    }
}

Write-Host "嵌入制品校验完成。"
