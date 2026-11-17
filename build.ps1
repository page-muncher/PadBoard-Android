param(
    [string]$Sdk = $env:ANDROID_SDK_ROOT,
    [string]$Platform = 'android-36',
    [string]$BuildTools = '35.0.0',
    [string]$SigningKey,
    [string]$OutputApk
)

$ErrorActionPreference = 'Stop'
if (!$Sdk) { $Sdk = $env:ANDROID_HOME }
if (!$Sdk) { throw 'Pass -Sdk or set ANDROID_SDK_ROOT to your Android SDK directory.' }
$tools = Join-Path $Sdk "build-tools\$BuildTools"
$androidJar = Join-Path $Sdk "platforms\$Platform\android.jar"
foreach ($required in @($androidJar, (Join-Path $tools 'aapt2.exe'), (Join-Path $tools 'd8.bat'),
        (Join-Path $tools 'zipalign.exe'), (Join-Path $tools 'apksigner.bat'))) {
    if (!(Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing $required" }
}

$build = Join-Path $PSScriptRoot 'build'
$symbols = Join-Path $build 'symbols'
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
New-Item -ItemType Directory -Force $build, $symbols, $classes, $dex | Out-Null
if (!$SigningKey) { $SigningKey = Join-Path $build 'padboard-local.p12' }
if (!$OutputApk) { $OutputApk = Join-Path $build 'PadBoard-1.0.7.apk' }
$OutputApk = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputApk)
$SigningKey = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($SigningKey)
New-Item -ItemType Directory -Force (Split-Path -Parent $OutputApk), (Split-Path -Parent $SigningKey) | Out-Null
$flat = Join-Path $build 'resources.zip'
$resources = Join-Path $build 'resources.apk'
$classJar = Join-Path $build 'classes.jar'
$unsigned = Join-Path $build 'unsigned.apk'
$aligned = Join-Path $build 'aligned.apk'

function Run-Tool($Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable exited with code $LASTEXITCODE" }
}

$aapt = Join-Path $tools 'aapt2.exe'
Run-Tool $aapt @('compile', '--dir', (Join-Path $PSScriptRoot 'res'), '-o', $flat)
Run-Tool $aapt @('link', '-o', $resources, '--manifest', (Join-Path $PSScriptRoot 'AndroidManifest.xml'),
    '-I', $androidJar, '--java', $symbols, $flat)

$javaFiles = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src'), $symbols -Recurse -Filter '*.java' |
    ForEach-Object FullName
Get-ChildItem -LiteralPath $classes -Recurse -Filter '*.class' -File | Remove-Item -Force
Run-Tool 'javac.exe' (@('--release', '8', '-encoding', 'UTF-8', '-classpath', $androidJar, '-d', $classes) + $javaFiles)
Run-Tool 'jar.exe' @('cf', $classJar, '-C', $classes, '.')
Run-Tool (Join-Path $tools 'd8.bat') @('--release', '--min-api', '28', '--lib', $androidJar, '--output', $dex, $classJar)

Copy-Item -LiteralPath $resources -Destination $unsigned -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
try {
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $dex 'classes.dex'), 'classes.dex') |
        Out-Null
} finally {
    $archive.Dispose()
}
Run-Tool (Join-Path $tools 'zipalign.exe') @('-f', '4', $unsigned, $aligned)

# A local distribution key with a fixed tool password. Keep the private file for upgrades.
$password = 'padboard-local-build'
if (!(Test-Path -LiteralPath $SigningKey)) {
    Run-Tool 'keytool.exe' @('-genkeypair', '-storetype', 'PKCS12', '-keystore', $SigningKey,
        '-storepass', $password, '-alias', 'padboard', '-keyalg', 'RSA', '-keysize', '3072',
        '-validity', '10000', '-dname', 'CN=PadBoard Android')
}
$signer = Join-Path $tools 'apksigner.bat'
Run-Tool $signer @('sign', '--ks', $SigningKey, '--ks-key-alias', 'padboard', '--ks-pass', "pass:$password",
    '--v4-signing-enabled', 'false', '--out', $OutputApk, $aligned)
Run-Tool $signer @('verify', '--verbose', '--print-certs', $OutputApk)
Write-Output "Built $OutputApk"
