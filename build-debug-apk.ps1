$ErrorActionPreference = "Stop"

if (-not (Test-Path ".\gradlew.bat")) {
    Write-Host "Gradle Wrapper not found. Creating it first..."
    & ".\setup-gradle-wrapper.ps1"
}

Write-Host "Building Rinse debug APK..."
& ".\gradlew.bat" :app:assembleDebug

$Apk = ".\app\build\outputs\apk\debug\app-debug.apk"

if (Test-Path $Apk) {
    Write-Host ""
    Write-Host "Build complete:"
    Write-Host $Apk
} else {
    throw "APK was not found after build."
}
