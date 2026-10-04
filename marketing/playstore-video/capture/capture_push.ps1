param(
    [string]$Out = "C:\Users\USER\Documents\infiltrate\screenshots\promo_1080p\S08_push_take1.mp4",
    [double]$SettleAfterLoad = 1.5,
    [double]$RecordSeconds = 16.0
)
$ErrorActionPreference = "Stop"
$repo = "C:\Users\USER\Documents\infiltrate"
$title = "Infiltrate: Shadow Heist"
$ffmpeg = (Get-Command ffmpeg).Source

# Reuse the capture tool's Win32 helpers (window lookup, sizing, focus-safe key injection).
$tool = Get-Content (Join-Path $repo "tools\media_capture_tool.ps1") -Raw
$m = [regex]::Match($tool, '\$code = @"\r?\n(.*?)\r?\n"@', 'Singleline')
Add-Type -TypeDefinition $m.Groups[1].Value
Add-Type -TypeDefinition @"
using System; using System.Runtime.InteropServices;
public class Top { [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr a, int x, int y, int cx, int cy, uint f); }
"@
[Win32Capture]::SetProcessDPIAware() | Out-Null

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
$before = [Win32Capture]::FindWindowsByExactTitle($title)
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "cmd.exe"
$psi.Arguments = "/c `"$repo\gradlew.bat`" runJvm -PstartLevel=level_8 -PwindowSize=2304x1296 -PhideUi=true -q"
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
"window up"

[Win32Capture]::PositionAndResize($hwnd, 0, 0, 2304, 1296)
[Top]::SetWindowPos($hwnd, [IntPtr](-1), 0, 0, 0, 0, 0x0001 -bor 0x0002) | Out-Null  # topmost, keep pos/size

$rect = New-Object Win32Capture+RECT
[Win32Capture]::GetClientRect($hwnd, [ref]$rect) | Out-Null
$pt = New-Object Win32Capture+POINT
[Win32Capture]::ClientToScreen($hwnd, [ref]$pt) | Out-Null
$cw = ($rect.Right - $rect.Left); $cw -= $cw % 2
$ch = ($rect.Bottom - $rect.Top); $ch -= $ch % 2
"client ${cw}x${ch} at $($pt.X),$($pt.Y)"

# The loading screen is near-black; gameplay's sky is bright. Poll a strip of sky until it lights up.
Add-Type -AssemblyName System.Drawing
$deadline = (Get-Date).AddSeconds(120)
while ((Get-Date) -lt $deadline) {
    $bmp = New-Object System.Drawing.Bitmap(200, 60)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($pt.X + [int]($cw / 2) - 100, $pt.Y + 40, 0, 0, $bmp.Size)
    $g.Dispose()
    $sum = 0.0
    for ($x = 0; $x -lt 200; $x += 10) { for ($y = 0; $y -lt 60; $y += 10) { $c = $bmp.GetPixel($x, $y); $sum += ($c.R + $c.G + $c.B) / 3.0 } }
    $bmp.Dispose()
    $mean = $sum / 120.0
    # A fresh window paints white before GL is up, so only a bright sky AFTER the dark loading
    # screen counts.
    if ($mean -lt 30) { $sawLoading = $true }
    if ($sawLoading -and $mean -gt 45) { "gameplay (sky mean $([int]$mean))"; break }
    Start-Sleep -Milliseconds 250
}
Start-Sleep -Milliseconds ([int]($SettleAfterLoad * 1000))

New-Item -ItemType Directory -Force (Split-Path $Out) | Out-Null
$ffArgs = "-y -f lavfi -i `"ddagrab=output_idx=0:framerate=60:offset_x=$($pt.X):offset_y=$($pt.Y):video_size=${cw}x${ch}:draw_mouse=0`" -t $RecordSeconds -vf `"hwdownload,format=bgra,scale=out_range=tv:out_color_matrix=bt709,format=yuv420p`" -color_range tv -colorspace bt709 -color_primaries bt709 -color_trc bt709 -c:v h264_nvenc -preset p5 -tune hq -rc vbr -cq 14 -b:v 0 `"$Out`""
$ff = Start-Process -FilePath $ffmpeg -ArgumentList $ffArgs -PassThru -WindowStyle Hidden
Start-Sleep -Milliseconds 800

$h = @($hwnd)
$VK = @{ D = 0x44; E = 0x45; W = 0x57; SPACE = 0x20 }
function Down($k) { [Win32Capture]::SendKeyToAllWindows($h, $VK[$k]) }
function Up($k) { [Win32Capture]::SendKeyUpToAllWindows($h, $VK[$k]) }
function Tap($k) { Down $k; Start-Sleep -Milliseconds 90; Up $k }
function Wait($s) { Start-Sleep -Milliseconds ([int]($s * 1000)) }

# Walk up to the cart (324 units at 132/s), brace, push it flush to the platform, hop up.
Wait 0.6
Down D; Wait 3.2; Up D
Wait 0.15
Tap E
Wait 1.0
Down D; Wait 3.6; Up D
Wait 0.4
Tap E
Wait 0.5
Down D; Wait 0.15; Tap W; Wait 0.9; Tap W; Wait 1.2; Up D

$ff.WaitForExit(30000) | Out-Null
"recorded -> $Out"
[Win32Capture]::PostMessage($hwnd, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null  # WM_CLOSE
Start-Sleep -Seconds 2
if (-not $game.HasExited) { try { $game.Kill() } catch {} }
"done"
