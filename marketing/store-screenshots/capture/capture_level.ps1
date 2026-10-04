<#
  Launches one level of the JVM build at a fixed 16:9 size, waits for gameplay to appear, plays
  a scripted key sequence and grabs lossless PNG frames of the game window (GPU capture).

  -Actions is PowerShell using Down/Up/Tap/Wait, e.g. "Down D; Wait 1.5; Up D; Tap E".
  Keys: A D W S E SPACE F1 F2 1-5.

  Example:
    .\capture_level.ps1 -Level level_3 -Name l3_beam -Seconds 8 -Actions "Down S; Down D; Wait 1.4; Up D"
#>
param(
    [Parameter(Mandatory = $true)][string]$Level,
    [Parameter(Mandatory = $true)][string]$Name,
    [string]$Actions = "",
    [double]$Seconds = 8.0,
    [int]$Fps = 10,
    [switch]$ShowUi,
    [double]$SettleAfterLoad = 1.0,
    [string]$OutRoot = "$PSScriptRoot\..\frames",
    [int]$Width = 2304,
    [int]$Height = 1296
)
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path "$PSScriptRoot\..\..\..").Path
$title = "Infiltrate: Shadow Heist"
$ffmpeg = (Get-Command ffmpeg).Source

# Reuse the capture tool's Win32 helpers (window lookup, sizing, focus-safe key injection).
$tool = Get-Content (Join-Path $repo "tools\media_capture_tool.ps1") -Raw
$m = [regex]::Match($tool, '\$code = @"\r?\n(.*?)\r?\n"@', 'Singleline')
if (-not ("Win32Capture" -as [type])) { Add-Type -TypeDefinition $m.Groups[1].Value }
if (-not ("TopMost" -as [type])) {
    Add-Type -TypeDefinition @"
using System; using System.Runtime.InteropServices;
public class TopMost { [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr a, int x, int y, int cx, int cy, uint f); }
"@
}
Add-Type -AssemblyName System.Drawing
[Win32Capture]::SetProcessDPIAware() | Out-Null

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$before = [Win32Capture]::FindWindowsByExactTitle($title)
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "cmd.exe"
$hide = if ($ShowUi) { "false" } else { "true" }
$psi.Arguments = "/c `"$repo\gradlew.bat`" runJvm -PstartLevel=$Level -PwindowSize=${Width}x${Height} -PhideUi=$hide -q"
$psi.WorkingDirectory = $repo
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
$game = [System.Diagnostics.Process]::Start($psi)

$hwnd = [IntPtr]::Zero
$deadline = (Get-Date).AddSeconds(300)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 400
    $new = @([Win32Capture]::FindWindowsByExactTitle($title) | Where-Object { $before -notcontains $_ })
    if ($new.Count -gt 0) { $hwnd = $new[0]; break }
    if ($game.HasExited) { throw "gradle exited before the window opened" }
}
if ($hwnd -eq [IntPtr]::Zero) { throw "game window never appeared" }

[Win32Capture]::PositionAndResize($hwnd, 0, 0, $Width, $Height)
[TopMost]::SetWindowPos($hwnd, [IntPtr](-1), 0, 0, 0, 0, 0x0001 -bor 0x0002) | Out-Null

$rect = New-Object Win32Capture+RECT
[Win32Capture]::GetClientRect($hwnd, [ref]$rect) | Out-Null
$pt = New-Object Win32Capture+POINT
[Win32Capture]::ClientToScreen($hwnd, [ref]$pt) | Out-Null
$cw = ($rect.Right - $rect.Left); $cw -= $cw % 2
$ch = ($rect.Bottom - $rect.Top); $ch -= $ch % 2

# Wait for the loading screen (near-black top band; a fresh window paints white first), take it
# as a reference, then wait until the window stops looking like it. The loading bar's own rows
# are skipped so its progress doesn't count as a change - this works for dark levels too.
function Grab-Grid {
    $bmp = New-Object System.Drawing.Bitmap($cw, $ch)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($pt.X, $pt.Y, 0, 0, $bmp.Size)
    $g.Dispose()
    $vals = New-Object System.Collections.Generic.List[double]
    for ($j = 1; $j -lt 24; $j++) {
        $fy = $j / 24.0
        if ($fy -gt 0.40 -and $fy -lt 0.56) { continue }
        for ($i = 1; $i -lt 32; $i++) {
            $c = $bmp.GetPixel([int]($cw * $i / 32.0), [int]($ch * $fy))
            $vals.Add(($c.R + $c.G + $c.B) / 3.0)
        }
    }
    $bmp.Dispose()
    return , $vals
}
$ref = $null
$deadline = (Get-Date).AddSeconds(180)
while ((Get-Date) -lt $deadline) {
    $v = Grab-Grid
    $topMean = ($v | Select-Object -First 31 | Measure-Object -Average).Average
    if ($null -eq $ref) {
        if ($topMean -lt 22) { $ref = $v; Start-Sleep -Milliseconds 500; $ref = Grab-Grid }
    } else {
        $d = 0.0
        for ($k = 0; $k -lt $v.Count; $k++) { $d += [Math]::Abs($v[$k] - $ref[$k]) }
        if (($d / $v.Count) -gt 8) { break }
    }
    Start-Sleep -Milliseconds 200
}
Start-Sleep -Milliseconds ([int]($SettleAfterLoad * 1000))

$outDir = Join-Path $OutRoot $Name
New-Item -ItemType Directory -Force $outDir | Out-Null
Get-ChildItem $outDir -Filter *.png -ErrorAction SilentlyContinue | Remove-Item
$ffArgs = "-y -f lavfi -i `"ddagrab=output_idx=0:framerate=${Fps}:offset_x=$($pt.X):offset_y=$($pt.Y):video_size=${cw}x${ch}:draw_mouse=0`" -t $Seconds -vf `"hwdownload,format=bgra,format=rgb24`" -c:v png -compression_level 1 `"$outDir\f_%04d.png`""
$ff = Start-Process -FilePath $ffmpeg -ArgumentList $ffArgs -PassThru -WindowStyle Hidden
Start-Sleep -Milliseconds 600

$h = @($hwnd)
$VK = @{ A = 0x41; D = 0x44; W = 0x57; S = 0x53; E = 0x45; SPACE = 0x20; F1 = 0x70; F2 = 0x71; '1' = 0x31; '2' = 0x32; '3' = 0x33; '4' = 0x34; '5' = 0x35 }
function Down($k) { [Win32Capture]::SendKeyToAllWindows($h, $VK["$k"]) }
function Up($k) { [Win32Capture]::SendKeyUpToAllWindows($h, $VK["$k"]) }
function Tap($k) { Down $k; Start-Sleep -Milliseconds 90; Up $k }
function Wait($s) { Start-Sleep -Milliseconds ([int]($s * 1000)) }

if ($Actions) { Invoke-Expression $Actions }
foreach ($k in $VK.Keys) { Up $k }

$ff.WaitForExit([int](($Seconds + 20) * 1000)) | Out-Null
[Win32Capture]::PostMessage($hwnd, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null  # WM_CLOSE
Start-Sleep -Seconds 2
if (-not $game.HasExited) { try { $game.Kill() } catch {} }
$n = (Get-ChildItem $outDir -Filter *.png).Count
"$Name : $n frames -> $outDir"
