$ErrorActionPreference = 'Stop'
$workspaceDirectory = Split-Path -Parent $PSScriptRoot
$testOutputDirectory = Join-Path $workspaceDirectory 'build/excel-tests'
New-Item -ItemType Directory -Force -Path $testOutputDirectory | Out-Null
$androidJar = Join-Path $env:ANDROID_HOME 'platforms/android-37.0/android.jar'
$javaExecutable = Join-Path $env:JAVA_HOME 'bin/java.exe'
$compilerExecutable = Join-Path $env:JAVA_HOME 'bin/javac.exe'
& $compilerExecutable -encoding UTF-8 -cp $androidJar -d $testOutputDirectory `
    (Join-Path $workspaceDirectory 'tests/host/android/util/Xml.java') `
    (Join-Path $workspaceDirectory 'app/src/main/java/cn/local/jiyibi/LedgerEntry.java') `
    (Join-Path $workspaceDirectory 'app/src/main/java/cn/local/jiyibi/ExcelTransfer.java') `
    (Join-Path $workspaceDirectory 'tests/ExcelTransferTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Excel tests did not compile' }
& $javaExecutable -cp "$testOutputDirectory;$androidJar" cn.local.jiyibi.ExcelTransferTest
if ($LASTEXITCODE -ne 0) { throw 'Excel tests failed' }
