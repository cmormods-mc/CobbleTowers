param([string]$OutDir, [int]$Frames = 10, [int]$DelayMs = 60, [string]$Prefix = "burst", [string]$Title = "Minecraft")
# Saves $Frames pictures of the Minecraft client window in quick succession (the window is brought to the top-left once first).
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class WinB {
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr h, int x, int y, int w, int ht, bool repaint);
  public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
[WinB]::SetProcessDPIAware() | Out-Null
$p = Get-Process | Where-Object { $_.MainWindowTitle -like "$Title*" -and $_.ProcessName -like "java*" } | Select-Object -First 1
if (-not $p) { Write-Error "no client window"; exit 1 }
New-Item -ItemType Directory -Force $OutDir | Out-Null
[WinB]::ShowWindow($p.MainWindowHandle, 9) | Out-Null
[WinB]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
$r = New-Object WinB+RECT
[WinB]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
[WinB]::MoveWindow($p.MainWindowHandle, 0, 0, ($r.Right - $r.Left), ($r.Bottom - $r.Top), $true) | Out-Null
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
