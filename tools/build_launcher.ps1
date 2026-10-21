# Build and sign dist\keting-home.apk. Proxy variables are cleared for this process only.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:USERPROFILE "android-sdk" }
if (-not (Test-Path $sdk)) {
    throw "ANDROID_HOME is missing. Install platform android-34 and build-tools 34.0.0."
}
if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME is missing. JDK 17 or newer is required."
}

$sdkDir = ($sdk -replace '\\', '/')
Set-Content -Path "launcher\local.properties" -Value "sdk.dir=$sdkDir" -Encoding ascii

$env:HTTP_PROXY = ""
$env:HTTPS_PROXY = ""
$env:http_proxy = ""
$env:https_proxy = ""

Push-Location launcher
& .\gradlew.bat :app:assembleRelease --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Gradle assembleRelease failed." }
Pop-Location

$unsigned = "launcher\app\build\outputs\apk\release\app-release-unsigned.apk"
if (-not (Test-Path $unsigned)) {
    $unsigned = "launcher\app\build\outputs\apk\release\app-release.apk"
}
$keystore = "launcher\debug.keystore"
if (-not (Test-Path $keystore)) {
    $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    & $keytool -genkeypair -keystore $keystore -storepass android -keypass android `
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=Keting Debug, OU=Home, O=Keting, C=CN"
}
New-Item -ItemType Directory -Force -Path dist | Out-Null
$tools = Get-ChildItem "$sdk\build-tools" -Directory | Sort-Object Name | Select-Object -Last 1
& "$($tools.FullName)\zipalign.exe" -f -p 4 $unsigned "dist\keting-home.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed." }
& "$($tools.FullName)\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android "dist\keting-home.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed." }
& "$($tools.FullName)\apksigner.bat" verify "dist\keting-home.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed." }
Write-Output "APK: dist\keting-home.apk"
