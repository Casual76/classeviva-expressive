[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string]$Serial,

  [string]$AdbPath,
  [string]$AppApk,
  [string]$TestApk,
  [string]$OutputRoot,

  [int[]]$RefreshRates = @(60, 120),
  [double[]]$AnimatorScales = @(1.0),

  [switch]$SkipInstall,
  [switch]$SkipAnimatorSmoke
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$targetPackage = "dev.antigravity.classevivaexpressive"
$testPackage = "dev.antigravity.classevivaexpressive.macrobenchmark"
$instrumentation = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
$motionBenchmarkClass = "$testPackage.MotionMacrobenchmark"
$animatorSmokeClass = "$testPackage.AnimatorScaleSmokeTest"
$repoRoot = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($AppApk)) {
  $AppApk = Join-Path $repoRoot "android\app\build\outputs\apk\benchmark\app-benchmark.apk"
}
if ([string]::IsNullOrWhiteSpace($TestApk)) {
  $TestApk = Join-Path $repoRoot "android\macrobenchmark\build\outputs\apk\benchmark\macrobenchmark-benchmark.apk"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
  $OutputRoot = Join-Path $repoRoot "artifacts\motion-device-qa"
}

function Resolve-AdbExecutable {
  param([string]$ConfiguredPath)

  if (-not [string]::IsNullOrWhiteSpace($ConfiguredPath)) {
    return (Resolve-Path -LiteralPath $ConfiguredPath).Path
  }

  foreach ($sdkRoot in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
    if (-not [string]::IsNullOrWhiteSpace($sdkRoot)) {
      $candidate = Join-Path $sdkRoot "platform-tools\adb.exe"
      if (Test-Path -LiteralPath $candidate -PathType Leaf) {
        return (Resolve-Path -LiteralPath $candidate).Path
      }
    }
  }

  $command = Get-Command adb.exe -ErrorAction SilentlyContinue
  if ($null -ne $command) {
    return $command.Source
  }

  throw "adb.exe not found. Pass -AdbPath explicitly."
}

$adbExecutable = Resolve-AdbExecutable -ConfiguredPath $AdbPath
$resolvedAppApk = (Resolve-Path -LiteralPath $AppApk).Path
$resolvedTestApk = (Resolve-Path -LiteralPath $TestApk).Path
$resolvedOutputRoot = [System.IO.Path]::GetFullPath($OutputRoot)

foreach ($refreshRate in $RefreshRates) {
  if ($refreshRate -notin @(60, 120)) {
    throw "Unsupported refresh rate '$refreshRate'. This QA runner accepts only 60 or 120 Hz."
  }
}
foreach ($animatorScale in $AnimatorScales) {
  if ($animatorScale -notin @(0.0, 0.5, 1.0, 2.0)) {
    throw "Unsupported animator scale '$animatorScale'. Use 0, 0.5, 1 or 2."
  }
}

function Invoke-AdbCapture {
  param(
    [Parameter(Mandatory = $true)]
    [string[]]$AdbArguments,
    [switch]$AllowFailure
  )

  $commandLine = $AdbArguments -join " "
  if ($commandLine -match "(?i)(^|\\s)uninstall(\\s|$)" -or
      $commandLine -match "(?i)(^|\\s)pm\\s+(clear|uninstall)(\\s|$)" -or
      $commandLine -match "(?i)(^|\\s)cmd\\s+package\\s+uninstall(\\s|$)") {
    throw "Blocked destructive adb command: $commandLine"
  }

  $output = & $adbExecutable -s $Serial @AdbArguments 2>&1
  $exitCode = $LASTEXITCODE
  $textOutput = ($output | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
  if ($exitCode -ne 0 -and -not $AllowFailure) {
    throw "adb failed ($exitCode): adb -s $Serial $($AdbArguments -join ' ')`n$textOutput"
  }
  return $textOutput
}

function Test-AuthenticatedHomeVisible {
  $null = Invoke-AdbCapture -AdbArguments @("shell", "am", "force-stop", $targetPackage)
  $null = Invoke-AdbCapture -AdbArguments @("shell", "am", "start", "-W", "-n", "$targetPackage/.MainActivity")

  foreach ($attempt in 1..12) {
    $hierarchy = Invoke-AdbCapture `
      -AdbArguments @("exec-out", "uiautomator", "dump", "/dev/tty") `
      -AllowFailure
    if ($hierarchy -match 'text="Home"' -or $hierarchy -match 'content-desc="[^"]*Home[^"]*"') {
      return $true
    }
    if ($attempt -lt 12) {
      Start-Sleep -Milliseconds 750
    }
  }
  return $false
}

function Get-DeviceSetting {
  param([string]$Namespace, [string]$Key)

  $value = (Invoke-AdbCapture -AdbArguments @("shell", "settings", "get", $Namespace, $Key)).Trim()
  [pscustomobject]@{
    Namespace = $Namespace
    Key = $Key
    Exists = -not ([string]::IsNullOrWhiteSpace($value) -or $value -eq "null")
    Value = $value
  }
}

function Set-DeviceSetting {
  param([string]$Namespace, [string]$Key, [string]$Value)
  $null = Invoke-AdbCapture -AdbArguments @("shell", "settings", "put", $Namespace, $Key, $Value)
}

function Restore-DeviceSetting {
  param($Snapshot)

  if ($Snapshot.Exists) {
    Set-DeviceSetting -Namespace $Snapshot.Namespace -Key $Snapshot.Key -Value $Snapshot.Value
  } else {
    $null = Invoke-AdbCapture -AdbArguments @("shell", "settings", "delete", $Snapshot.Namespace, $Snapshot.Key)
  }
}

function Get-ObservedRefreshRate {
  $latencyOutput = Invoke-AdbCapture `
    -AdbArguments @("shell", "dumpsys", "SurfaceFlinger", "--latency") `
    -AllowFailure
  $periodLine = $latencyOutput -split "`r?`n" |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_ -match "^[0-9]+$" } |
    Select-Object -First 1
  if ([string]::IsNullOrWhiteSpace($periodLine)) {
    return $null
  }

  $periodNs = [double]$periodLine
  if ($periodNs -le 0) {
    return $null
  }
  return [Math]::Round(1000000000.0 / $periodNs, 2)
}

function Get-PackageField {
  param([string]$PackageDump, [string]$FieldName)

  $match = [regex]::Match($PackageDump, "(?m)^\\s*$([regex]::Escape($FieldName))=(.+)$")
  if (-not $match.Success) {
    return $null
  }
  return $match.Groups[1].Value.Trim()
}

function Assert-InstrumentationSucceeded {
  param([string]$InstrumentationOutput)

  if ($InstrumentationOutput -match "(?m)^(FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED)" -or
      $InstrumentationOutput -match "shortMsg=Process crashed" -or
      $InstrumentationOutput -notmatch "(?m)^OK \(") {
    throw "Instrumentation did not complete successfully.`n$InstrumentationOutput"
  }
}

function Invoke-InstrumentationClass {
  param(
    [string]$ClassName,
    [string]$RemoteOutputDirectory,
    [string]$LocalOutputDirectory
  )

  $null = Invoke-AdbCapture -AdbArguments @("shell", "mkdir", "-p", $RemoteOutputDirectory)
  $instrumentOutput = Invoke-AdbCapture -AdbArguments @(
    "shell", "am", "instrument", "-w", "-r",
    "-e", "class", $ClassName,
    "-e", "androidx.benchmark.compilation.enabled", "false",
    "-e", "additionalTestOutputDir", $RemoteOutputDirectory,
    "-e", "listener", "androidx.benchmark.macro.junit4.SideEffectRunListener",
    $instrumentation
  )

  New-Item -ItemType Directory -Force -Path $LocalOutputDirectory | Out-Null
  Set-Content -LiteralPath (Join-Path $LocalOutputDirectory "instrumentation.txt") -Value $instrumentOutput
  $null = Invoke-AdbCapture -AdbArguments @("pull", $RemoteOutputDirectory, $LocalOutputDirectory)
  Assert-InstrumentationSucceeded -InstrumentationOutput $instrumentOutput
}

$deviceState = (Invoke-AdbCapture -AdbArguments @("get-state")).Trim()
if ($deviceState -ne "device") {
  throw "Device '$Serial' is not ready (state: $deviceState)."
}

$apiLevel = [int](Invoke-AdbCapture -AdbArguments @("shell", "getprop", "ro.build.version.sdk")).Trim()
if ($apiLevel -lt 28) {
  throw "Macrobenchmark requires API 28 or newer; device reports API $apiLevel."
}

$installedTarget = Invoke-AdbCapture -AdbArguments @("shell", "pm", "path", $targetPackage) -AllowFailure
if ($installedTarget -notmatch "package:") {
  throw "The target package is not installed. Log in with the release-signed app before running personal-device QA."
}
if (-not (Test-AuthenticatedHomeVisible)) {
  throw "Authenticated Home was not visible in the already installed app. No APK was replaced; unlock the phone and verify the session first."
}

$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$runRoot = Join-Path $resolvedOutputRoot $runId
New-Item -ItemType Directory -Force -Path $runRoot | Out-Null

$settingSnapshots = @(
  Get-DeviceSetting -Namespace "system" -Key "peak_refresh_rate"
  Get-DeviceSetting -Namespace "system" -Key "min_refresh_rate"
  Get-DeviceSetting -Namespace "global" -Key "window_animation_scale"
  Get-DeviceSetting -Namespace "global" -Key "transition_animation_scale"
  Get-DeviceSetting -Namespace "global" -Key "animator_duration_scale"
)
$restoreFailures = [System.Collections.Generic.List[string]]::new()
$runFailure = $null

try {
  $deviceModel = (Invoke-AdbCapture -AdbArguments @("shell", "getprop", "ro.product.model")).Trim()
  $deviceFingerprint = (Invoke-AdbCapture -AdbArguments @("shell", "getprop", "ro.build.fingerprint")).Trim()
  $packageBefore = Invoke-AdbCapture -AdbArguments @("shell", "dumpsys", "package", $targetPackage)
  $firstInstallTimeBefore = Get-PackageField -PackageDump $packageBefore -FieldName "firstInstallTime"
  Set-Content -LiteralPath (Join-Path $runRoot "package-before.txt") -Value $packageBefore
  Set-Content -LiteralPath (Join-Path $runRoot "display-before.txt") -Value (
    Invoke-AdbCapture -AdbArguments @("shell", "dumpsys", "display")
  )

  if (-not $SkipInstall) {
    # -r replaces the APK while retaining app data. No uninstall, pm clear, or downgrade is used.
    $targetInstall = Invoke-AdbCapture -AdbArguments @("install", "-r", $resolvedAppApk)
    if ($targetInstall -notmatch "(?m)^Success$") {
      throw "Target APK replacement did not report Success.`n$targetInstall"
    }
    $testInstall = Invoke-AdbCapture -AdbArguments @("install", "-r", "-t", $resolvedTestApk)
    if ($testInstall -notmatch "(?m)^Success$") {
      throw "Benchmark APK replacement did not report Success.`n$testInstall"
    }
  }

  $instrumentationList = Invoke-AdbCapture -AdbArguments @("shell", "pm", "list", "instrumentation")
  if ($instrumentationList -notmatch [regex]::Escape($instrumentation)) {
    throw "Expected instrumentation '$instrumentation' is not installed."
  }

  $metadata = [ordered]@{
    runId = $runId
    serial = $Serial
    model = $deviceModel
    apiLevel = $apiLevel
    fingerprint = $deviceFingerprint
    targetPackage = $targetPackage
    targetApk = $resolvedAppApk
    testApk = $resolvedTestApk
    installMode = if ($SkipInstall) { "existing APKs" } else { "adb install -r (data preserving)" }
    requestedRefreshRates = $RefreshRates
    requestedAnimatorScales = $AnimatorScales
    originalSettings = $settingSnapshots
  }
  $metadata | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $runRoot "run.json")

  foreach ($refreshRate in $RefreshRates) {
    $refreshValue = ([double]$refreshRate).ToString("0.0", [Globalization.CultureInfo]::InvariantCulture)
    Set-DeviceSetting -Namespace "system" -Key "peak_refresh_rate" -Value $refreshValue
    Set-DeviceSetting -Namespace "system" -Key "min_refresh_rate" -Value $refreshValue

    foreach ($animatorScale in $AnimatorScales) {
      $scaleValue = $animatorScale.ToString("0.0", [Globalization.CultureInfo]::InvariantCulture)
      foreach ($scaleKey in @("window_animation_scale", "transition_animation_scale", "animator_duration_scale")) {
        Set-DeviceSetting -Namespace "global" -Key $scaleKey -Value $scaleValue
      }

      Start-Sleep -Seconds 2
      $configurationName = "${refreshRate}hz-scale-$($scaleValue.Replace('.', '_'))"
      $localConfigurationRoot = Join-Path $runRoot $configurationName
      New-Item -ItemType Directory -Force -Path $localConfigurationRoot | Out-Null
      $displaySnapshot = Invoke-AdbCapture -AdbArguments @("shell", "dumpsys", "display")
      Set-Content -LiteralPath (Join-Path $localConfigurationRoot "display.txt") -Value $displaySnapshot
      $observedRefreshRate = Get-ObservedRefreshRate
      [ordered]@{
        requestedHz = $refreshRate
        observedHz = $observedRefreshRate
        observationSource = "dumpsys SurfaceFlinger --latency"
      } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $localConfigurationRoot "refresh-rate.json")
      if ($null -ne $observedRefreshRate -and [Math]::Abs($observedRefreshRate - $refreshRate) -gt 5.0) {
        throw "Requested $refreshRate Hz, but SurfaceFlinger reports $observedRefreshRate Hz. Results were not collected under a misleading label."
      }

      $remoteOutput = "/sdcard/Android/media/$testPackage/$runId/$configurationName"
      Invoke-InstrumentationClass `
        -ClassName $motionBenchmarkClass `
        -RemoteOutputDirectory $remoteOutput `
        -LocalOutputDirectory $localConfigurationRoot
    }
  }

  if (-not $SkipAnimatorSmoke) {
    $smokeRoot = Join-Path $runRoot "animator-0x-smoke"
    $remoteSmokeOutput = "/sdcard/Android/media/$testPackage/$runId/animator-0x-smoke"
    Invoke-InstrumentationClass `
      -ClassName $animatorSmokeClass `
      -RemoteOutputDirectory $remoteSmokeOutput `
      -LocalOutputDirectory $smokeRoot
  }

  $packageAfter = Invoke-AdbCapture -AdbArguments @("shell", "dumpsys", "package", $targetPackage)
  Set-Content -LiteralPath (Join-Path $runRoot "package-after.txt") -Value $packageAfter
  $firstInstallTimeAfter = Get-PackageField -PackageDump $packageAfter -FieldName "firstInstallTime"
  if ($null -ne $firstInstallTimeBefore -and
      $null -ne $firstInstallTimeAfter -and
      $firstInstallTimeBefore -ne $firstInstallTimeAfter) {
    throw "Target firstInstallTime changed during QA; package preservation cannot be confirmed."
  }
} catch {
  $runFailure = $_
} finally {
  foreach ($snapshot in $settingSnapshots) {
    try {
      Restore-DeviceSetting -Snapshot $snapshot
    } catch {
      $restoreFailures.Add("$($snapshot.Namespace)/$($snapshot.Key): $($_.Exception.Message)")
    }
  }

  try {
    Set-Content -LiteralPath (Join-Path $runRoot "display-restored.txt") -Value (
      Invoke-AdbCapture -AdbArguments @("shell", "dumpsys", "display")
    )
  } catch {
    $restoreFailures.Add("display verification: $($_.Exception.Message)")
  }
}

if ($restoreFailures.Count -gt 0) {
  $restoreMessage = $restoreFailures -join [Environment]::NewLine
  if ($null -ne $runFailure) {
    throw "QA failed and one or more device settings could not be restored.`n$restoreMessage`nOriginal failure: $runFailure"
  }
  throw "QA completed, but one or more device settings could not be restored.`n$restoreMessage"
}
if ($null -ne $runFailure) {
  throw $runFailure
}

Write-Host "Motion device QA completed. Results: $runRoot"
Write-Host "Refresh-rate and animator settings were restored to their original values."
