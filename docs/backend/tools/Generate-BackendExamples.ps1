param(
    [Parameter(Mandatory = $true)][string]$JdkDirectory,
    [string]$ProjectDirectory = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path,
    [string]$GradleCacheDirectory = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($GradleCacheDirectory)) {
    $gradleUserDirectory = if ([string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) {
        Join-Path $env:USERPROFILE '.gradle'
    } else { $env:GRADLE_USER_HOME }
    $GradleCacheDirectory = Join-Path $gradleUserDirectory 'caches/modules-2/files-2.1'
}
$project = (Resolve-Path -LiteralPath $ProjectDirectory).Path
$javac = Join-Path $JdkDirectory 'bin/javac.exe'
$java = Join-Path $JdkDirectory 'bin/java.exe'
if (!(Test-Path -LiteralPath $javac) -or !(Test-Path -LiteralPath $java)) {
    throw 'JdkDirectory must contain a JDK 17 or newer (bin/java.exe and bin/javac.exe).'
}
$coreJar = Join-Path $project 'core/game/build/libs/game.jar'
if (!(Test-Path -LiteralPath $coreJar)) { throw 'Compile :core:game:jar before generating documentation.' }
$catalog = Get-Content -Raw -LiteralPath (Join-Path $project 'gradle/libs.versions.toml')
function Read-Version([string]$key) {
    $match = [regex]::Match($catalog, '(?m)^' + [regex]::Escape($key) + '\s*=\s*"([^"]+)"')
    if (!$match.Success) { throw "Missing version catalog entry: $key" }
    return $match.Groups[1].Value
}
function Cached-Jar([string]$group, [string]$artifact, [string]$version) {
    $directory = Join-Path $GradleCacheDirectory "$group/$artifact/$version"
    $jar = Get-ChildItem -LiteralPath $directory -Recurse -File -Filter "$artifact-$version.jar" |
        Select-Object -First 1
    if (!$jar) { throw "Compile :core:game:jar to resolve $group`:$artifact`:$version first." }
    return $jar.FullName
}
$jars = @(
    $coreJar
    (Cached-Jar 'org.jetbrains.kotlin' 'kotlin-stdlib' (Read-Version 'kotlin'))
    (Cached-Jar 'org.jetbrains.kotlinx' 'kotlinx-serialization-core-jvm' (Read-Version 'kotlinxSerialization'))
    (Cached-Jar 'org.jetbrains.kotlinx' 'kotlinx-serialization-json-jvm' (Read-Version 'kotlinxSerialization'))
    (Cached-Jar 'org.jetbrains.kotlinx' 'kotlinx-coroutines-core-jvm' (Read-Version 'kotlinxCoroutines'))
)
$classpath = $jars -join [System.IO.Path]::PathSeparator
$compiled = Join-Path $project 'core/game/build/backend-example-generator'
New-Item -ItemType Directory -Force -Path $compiled | Out-Null
& $javac -encoding UTF-8 --release 17 -cp $classpath -d $compiled (Join-Path $PSScriptRoot 'GenerateBackendExamples.java')
if ($LASTEXITCODE -ne 0) { throw 'Documentation generator compilation failed.' }
& $java -cp ($compiled + [System.IO.Path]::PathSeparator + $classpath) GenerateBackendExamples (Join-Path $project 'docs/backend/examples')
if ($LASTEXITCODE -ne 0) { throw 'Documentation generation failed.' }
