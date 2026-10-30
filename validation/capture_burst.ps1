param([int]$OriginX = 2560, [int]$OriginY = 0, [string]$OutDir, [int]$Frames = 10, [int]$DelayMs = 60, [string]$Prefix = "burst", [string]$Title = "Minecraft", [int]$ProcessId = 0)
# Saves $Frames pictures of the Minecraft client window in quick succession (the window is brought to the top-left of the monitor at ($OriginX, $OriginY) once first).
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class WinB {
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int ht, bool repaint);
  public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
[WinB]::SetProcessDPIAware() | Out-Null
# only the test client, by process id: the player's own game window is never touched
$p = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
if ($p -and $p.MainWindowHandle -eq 0) { $p.Refresh() }
if (-not $p) { Write-Error "no client window"; exit 1 }
New-Item -ItemType Directory -Force $OutDir | Out-Null
[WinB]::ShowWindow($p.MainWindowHandle, 9) | Out-Null
[WinB]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
Start-Sleep -Milliseconds 400
# never photograph anything but the test client: if another window is in front, the screen holds somebody else's content
if ([WinB]::GetForegroundWindow() -ne $p.MainWindowHandle) { Write-Error "the client window is not in front; nothing was captured"; exit 3 }
$r = New-Object WinB+RECT
[WinB]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
[WinB]::MoveWindow($p.MainWindowHandle, $OriginX, $OriginY, ($r.Right - $r.Left), ($r.Bottom - $r.Top), $true) | Out-Null
Start-Sleep -Milliseconds 700
[WinB]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
$w = $r.Right - $r.Left; $h = $r.Bottom - $r.Top
for ($i = 0; $i -lt $Frames; $i++) {
  $bmp = New-Object System.Drawing.Bitmap $w, $h
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size)
  $bmp.Save((Join-Path $OutDir ("{0}_{1:D2}.png" -f $Prefix, $i)), [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose(); $bmp.Dispose()
  Start-Sleep -Milliseconds $DelayMs
}
Write-Output "$Frames frames $w x $h"
