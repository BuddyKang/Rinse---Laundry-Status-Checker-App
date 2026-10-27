$ErrorActionPreference = "Stop"

$GradleVersion = "9.5.0"
$Zip = Join-Path $env:TEMP "gradle-$GradleVersion-bin.zip"
$Dir = Join-Path $env:TEMP "rinse-gradle-$GradleVersion"

Write-Host "Downloading Gradle $GradleVersion..."
Invoke-WebRequest `
  -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" `
  -OutFile $Zip

if (Test-Path $Dir) {
    Remove-Item $Dir -Recurse -Force
}

New-Item -ItemType Directory -Path $Dir | Out-Null
Expand-Archive -Path $Zip -DestinationPath $Dir -Force

$GradleBat = Join-Path $Dir "gradle-$GradleVersion\bin\gradle.bat"

Write-Host "Creating Gradle Wrapper..."
& $GradleBat wrapper --gradle-version $GradleVersion

Write-Host ""
Write-Host "Gradle Wrapper created."
Write-Host "You can now run:"
Write-Host "  .\gradlew.bat :app:assembleDebug"
