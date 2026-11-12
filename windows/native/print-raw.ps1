param(
  [Parameter(Mandatory=$true)][ValidateSet('windows','serial')][string]$Mode,
  [string]$PrinterName,
  [string]$PortName,
  [int]$BaudRate = 9600,
  [Parameter(Mandatory=$true)][string]$FilePath
)

$ErrorActionPreference = 'Stop'
$bytes = [System.IO.File]::ReadAllBytes($FilePath)

if ($Mode -eq 'serial') {
  if ($PortName -notmatch '^COM\d+$') { throw 'Port série Bluetooth invalide.' }
  $port = [System.IO.Ports.SerialPort]::new($PortName, $BaudRate, [System.IO.Ports.Parity]::None, 8, [System.IO.Ports.StopBits]::One)
  $port.Handshake = [System.IO.Ports.Handshake]::None
  $port.WriteTimeout = 10000
  try {
    $port.Open()
    $port.BaseStream.Write($bytes, 0, $bytes.Length)
    $port.BaseStream.Flush()
  } finally {
    if ($port.IsOpen) { $port.Close() }
    $port.Dispose()
  }
  exit 0
}

if ([string]::IsNullOrWhiteSpace($PrinterName)) { throw 'Imprimante Windows non sélectionnée.' }

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public static class GuichetPlusRawPrinter {
  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
  public class DOC_INFO_1 {
    [MarshalAs(UnmanagedType.LPWStr)] public string pDocName;
    [MarshalAs(UnmanagedType.LPWStr)] public string pOutputFile;
    [MarshalAs(UnmanagedType.LPWStr)] public string pDataType;
  }

  [DllImport("winspool.drv", SetLastError=true, CharSet=CharSet.Unicode)]
  static extern bool OpenPrinter(string name, out IntPtr printer, IntPtr defaults);
  [DllImport("winspool.drv", SetLastError=true)] static extern bool ClosePrinter(IntPtr printer);
  [DllImport("winspool.drv", SetLastError=true, CharSet=CharSet.Unicode)]
  static extern int StartDocPrinter(IntPtr printer, int level, [In] DOC_INFO_1 docInfo);
  [DllImport("winspool.drv", SetLastError=true)] static extern bool EndDocPrinter(IntPtr printer);
  [DllImport("winspool.drv", SetLastError=true)] static extern bool StartPagePrinter(IntPtr printer);
  [DllImport("winspool.drv", SetLastError=true)] static extern bool EndPagePrinter(IntPtr printer);
  [DllImport("winspool.drv", SetLastError=true)]
  static extern bool WritePrinter(IntPtr printer, byte[] bytes, int count, out int written);

  public static void Send(string printerName, byte[] bytes) {
    IntPtr printer;
    if (!OpenPrinter(printerName, out printer, IntPtr.Zero)) throw new System.ComponentModel.Win32Exception();
    try {
      var info = new DOC_INFO_1 { pDocName = "GUICHET+ Ticket", pDataType = "RAW" };
      if (StartDocPrinter(printer, 1, info) == 0) throw new System.ComponentModel.Win32Exception();
      try {
        if (!StartPagePrinter(printer)) throw new System.ComponentModel.Win32Exception();
        try {
          int written;
          if (!WritePrinter(printer, bytes, bytes.Length, out written) || written != bytes.Length) throw new System.ComponentModel.Win32Exception();
        } finally { EndPagePrinter(printer); }
      } finally { EndDocPrinter(printer); }
    } finally { ClosePrinter(printer); }
  }
}
'@

[GuichetPlusRawPrinter]::Send($PrinterName, $bytes)
