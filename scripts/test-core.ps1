$ErrorActionPreference = 'Stop'
$workspaceDirectory = Split-Path -Parent $PSScriptRoot
$testOutputDirectory = Join-Path $workspaceDirectory 'build/core-tests'
New-Item -ItemType Directory -Force -Path $testOutputDirectory | Out-Null
$javaExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
$compilerExecutable = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { 'javac' }
& $compilerExecutable -encoding UTF-8 -d $testOutputDirectory `
    (Join-Path $workspaceDirectory 'app/src/main/java/cn/local/jiyibi/LedgerEntry.java') `
    (Join-Path $workspaceDirectory 'app/src/main/java/cn/local/jiyibi/LedgerFilter.java') `
    (Join-Path $workspaceDirectory 'app/src/main/java/cn/local/jiyibi/ReceiptParser.java') `
    (Join-Path $workspaceDirectory 'app/src/main/java/cn/local/jiyibi/ReceiptLine.java') `
    (Join-Path $workspaceDirectory 'app/src/main/java/cn/local/jiyibi/ReceiptLayoutParser.java') `
    (Join-Path $workspaceDirectory 'app/src/main/java/cn/local/jiyibi/CaptureSourcePolicy.java') `
    (Join-Path $workspaceDirectory 'app/src/main/java/cn/local/jiyibi/SourceSelectionSession.java') `
    (Join-Path $workspaceDirectory 'tests/SourceSelectionSessionTest.java') `
    (Join-Path $workspaceDirectory 'tests/ReceiptParserTest.java') `
    (Join-Path $workspaceDirectory 'tests/LedgerFilterTest.java')
if ($LASTEXITCODE -ne 0) { throw '业务测试编译失败' }
& $javaExecutable -cp $testOutputDirectory cn.local.jiyibi.ReceiptParserTest
if ($LASTEXITCODE -ne 0) { throw '业务测试失败' }

& $javaExecutable -cp $testOutputDirectory cn.local.jiyibi.SourceSelectionSessionTest
if ($LASTEXITCODE -ne 0) { throw 'Source selection tests failed' }

& $javaExecutable -cp $testOutputDirectory cn.local.jiyibi.LedgerFilterTest
if ($LASTEXITCODE -ne 0) { throw 'Ledger filter tests failed' }
