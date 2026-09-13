param(
    [string]$HistoryFile = 'config/ssq-history.json',
    [string]$OutputFile = 'target/prediction-backtest.json',
    [ValidateRange(1, 1000)][int]$DrawCount = 200,
    [ValidateRange(1, 20)][int]$SeedsPerDraw = 3
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    & mvn -B -ntp -pl lottery-dcb-service test-compile dependency:build-classpath '-Dskip.frontend=true' '-Dmdep.outputFile=target/backtest-classpath.txt' '-DincludeScope=test'
    if ($LASTEXITCODE -ne 0) {
        throw '回测编译失败'
    }
    $dependencies = (Get-Content -LiteralPath lottery-dcb-service/target/backtest-classpath.txt -Raw).Trim()
    $classpath = "lottery-dcb-service/target/test-classes;lottery-dcb-service/target/classes;$dependencies"
    & java '-Dfile.encoding=UTF-8' -Xmx512m -cp $classpath cn.lotterydcb.prediction.PredictionBacktest $HistoryFile $OutputFile $DrawCount $SeedsPerDraw
    if ($LASTEXITCODE -ne 0) {
        throw '回测执行失败'
    }
    Write-Output "回测报告：$([System.IO.Path]::GetFullPath($OutputFile))"
} finally {
    Pop-Location
}
