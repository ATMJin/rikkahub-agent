param(
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$JavaPath = 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe'
)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
if (!(Test-Path -LiteralPath $JavaPath)) { throw "Java not found: $JavaPath" }
if (!(Test-Path -LiteralPath $SdkPath)) { throw "Android SDK not found: $SdkPath" }
$env:ANDROID_HOME = $SdkPath
Push-Location $projectPath
try {
    # Invoke Java directly: no gradlew.bat/cmd.exe, no registry or UAC changes.
    & $JavaPath -classpath '.\gradle\wrapper\gradle-wrapper.jar' org.gradle.wrapper.GradleWrapperMain :app:testDebugUnitTest :ai:testDebugUnitTest :local-llm:testDebugUnitTest :common:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
