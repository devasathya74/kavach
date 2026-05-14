# Kavach Environment Check Script
# Verifies the deterministic toolchain configuration

$ErrorActionPreference = "Stop"

Write-Host "--- Kavach Environment Validation ---" -ForegroundColor Cyan

# 1. Check JAVA_HOME
$java_home = $env:JAVA_HOME
if ($null -eq $java_home -or !(Test-Path $java_home)) {
    Write-Error "JAVA_HOME is not set or path does not exist: $java_home"
} else {
    Write-Host "✅ JAVA_HOME: $java_home"
}

# 2. Check ANDROID_HOME
$android_home = $env:ANDROID_HOME
if ($null -eq $android_home -or !(Test-Path $android_home)) {
    Write-Error "ANDROID_HOME is not set or path does not exist: $android_home"
} else {
    Write-Host "✅ ANDROID_HOME: $android_home"
}

# 3. Verify java version
try {
    $java_version = & java -version 2>&1 | Out-String
    if ($java_version -match "17.0.2") {
        Write-Host "✅ Java Version: 17.0.2 detected"
    } else {
        Write-Warning "Unexpected Java version detected:`n$java_version"
    }
} catch {
    Write-Error "Java command not found in PATH."
}

# 4. Verify adb
try {
    $adb_version = & adb version 2>&1 | Out-String
    Write-Host "✅ adb: Detected"
} catch {
    Write-Warning "adb command not found in PATH. Check ANDROID_HOME\platform-tools"
}

# 5. Verify sdkmanager
$sdkmanager_path = Join-Path $android_home "cmdline-tools\latest\bin\sdkmanager.bat"
if (Test-Path $sdkmanager_path) {
    Write-Host "✅ sdkmanager: Found at $sdkmanager_path"
} else {
    Write-Error "sdkmanager not found at expected path: $sdkmanager_path"
}

# 6. Verify gradlew
if (Test-Path ".\gradlew.bat") {
    Write-Host "✅ gradlew: Found in current directory"
} else {
    Write-Error "gradlew.bat not found in current directory."
}

Write-Host "--- Validation Complete ---" -ForegroundColor Cyan
