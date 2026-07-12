param(
    [switch]$RunInstrumented,
    [switch]$BuildRelease,
    [switch]$SkipLint,
    [string[]]$Flavors = @("zh", "en", "unified")
)

$rootDir = Split-Path -Parent $PSScriptRoot
$startTime = Get-Date
$script:hasErrors = $false

function Write-Step($msg) {
    Write-Host "`n=== $msg ===" -ForegroundColor Cyan
}

function Invoke-Gradle($task) {
    $null = & $rootDir\gradlew.bat $task --no-daemon -q 2>$null
    return $LASTEXITCODE -eq 0
}

function Write-Result($name, $ok) {
    if ($ok) { Write-Host "  PASS: $name" -ForegroundColor Green }
    else { Write-Host "  FAIL: $name" -ForegroundColor Red; $script:hasErrors = $true }
}

function Get-TestSummary($flavor) {
    $dir = "$rootDir\app\build\test-results\test${flavor}DebugUnitTest"
    if (Test-Path $dir) {
        $total = 0; $failures = 0
        Get-ChildItem "$dir\TEST-*.xml" | ForEach-Object {
            $xml = [xml](Get-Content $_.FullName)
            $total += [int]$xml.testsuite.tests
            $failures += [int]$xml.testsuite.failures + [int]$xml.testsuite.errors
        }
        if ($failures -eq 0) { Write-Host "  [$flavor] $total tests, 0 failures" -ForegroundColor Green }
        else { Write-Host "  [$flavor] $total tests, $failures failures" -ForegroundColor Red }
    }
}

# Detect device
$hasDevice = $false
if ($RunInstrumented) {
    $devices = & adb devices 2>$null | Select-String -Pattern "^[a-fA-F0-9]+\s+device$"
    $hasDevice = $devices.Count -gt 0
    if (-not $hasDevice) {
        Write-Host "WARNING: --RunInstrumented but no device. Skipping." -ForegroundColor Yellow
    }
}

# Step 0: Clean
Write-Step "0/5 Clean"
Write-Result "clean" (Invoke-Gradle "clean")

# Step 1: Lint
if (-not $SkipLint) {
    Write-Step "1/5 Lint"
    $lintOk = Invoke-Gradle "lintZhDebug"
    Write-Result "lint (zh flavor, covers all code)" $lintOk
    if (-not $lintOk) { $script:hasErrors = $true }
}

# Step 2: Unit Tests
Write-Step "2/5 Unit Tests"
$tasks = ($Flavors | ForEach-Object { "test${_}DebugUnitTest" }) -join " "
$ok = Invoke-Gradle $tasks
Write-Result "$($Flavors.Count) flavor(s) unit tests" $ok
foreach ($f in $Flavors) { Get-TestSummary $f }

# Step 3: Instrumented Tests
if ($RunInstrumented -and $hasDevice) {
    Write-Step "3/5 Instrumented Tests"
    $tasks = ($Flavors | ForEach-Object { "connected${_}DebugAndroidTest" }) -join " "
    $ok = Invoke-Gradle $tasks
    Write-Result "$($Flavors.Count) flavor(s) instrumented tests" $ok
}
elseif ($RunInstrumented) { Write-Step "3/5 Instrumented — SKIPPED (no device)" }

# Step 4: Build Release
if ($BuildRelease) {
    Write-Step "4/5 Build Release APKs"
    $tasks = ($Flavors | ForEach-Object { "assemble${_}Release" }) -join " "
    $ok = Invoke-Gradle $tasks
    Write-Result "$($Flavors.Count) flavor(s) release build" $ok
}

# Summary
$elapsed = (Get-Date) - $startTime
Write-Step "Done in $($elapsed.Minutes)m $($elapsed.Seconds)s"
if ($script:hasErrors) {
    Write-Host "SOME CHECKS FAILED" -ForegroundColor Red
    exit 1
} else {
    Write-Host "ALL CHECKS PASSED" -ForegroundColor Green
    exit 0
}
