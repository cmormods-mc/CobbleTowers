param([int]$X, [int]$Y)
# Left-clicks at screen position ($X, $Y), in physical pixels (the same space capture_window.ps1 photographs).
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Clk {
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, UIntPtr e);
}
"@
[Clk]::SetProcessDPIAware() | Out-Null
[Clk]::SetCursorPos($X, $Y) | Out-Null
Start-Sleep -Milliseconds 300
[Clk]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 80
[Clk]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
