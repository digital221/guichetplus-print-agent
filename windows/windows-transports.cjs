'use strict';

const { execFile } = require('node:child_process');
const fs = require('node:fs');
const net = require('node:net');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');

function powershell(args, timeout = 10000) {
  return new Promise((resolve, reject) => {
    execFile('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', ...args],
      { windowsHide: true, timeout, maxBuffer: 1024 * 1024 }, (error, stdout, stderr) => {
        if (error) return reject(new Error(String(stderr || error.message).trim()));
        resolve(String(stdout).trim());
      });
  });
}

function normalizeArray(value) {
  if (!value) return [];
  return Array.isArray(value) ? value : [value];
}

async function listDevices() {
  if (process.platform !== 'win32') return { printers: [], serialPorts: [], platform: process.platform };
  const command = [
    "$ErrorActionPreference='Stop'",
    "$printers=@(Get-CimInstance Win32_Printer | Select-Object Name,PortName,DriverName,Default,WorkOffline)",
    "$serial=@(Get-CimInstance Win32_SerialPort | Select-Object DeviceID,Name,PNPDeviceID)",
    "@{printers=$printers;serialPorts=$serial}|ConvertTo-Json -Compress -Depth 4"
  ].join(';');
  const parsed = JSON.parse(await powershell(['-Command', command]));
  return { printers: normalizeArray(parsed.printers), serialPorts: normalizeArray(parsed.serialPorts), platform: 'win32' };
}

function sendNetwork(payload, config) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: config.printerHost, port: Number(config.printerPort) });
    const timer = setTimeout(() => socket.destroy(new Error('Délai imprimante dépassé')), 5000);
    socket.once('connect', () => socket.end(payload));
    socket.once('close', hadError => { clearTimeout(timer); if (!hadError) resolve(); });
    socket.once('error', error => { clearTimeout(timer); reject(error); });
  });
}

function helperPath(resourcesPath, developmentDirectory) {
  return process.env.NODE_ENV === 'development' || !resourcesPath
    ? path.join(developmentDirectory, 'native', 'print-raw.ps1')
    : path.join(resourcesPath, 'native', 'print-raw.ps1');
}

async function sendWindows(payload, config, resourcesPath, developmentDirectory) {
  if (process.platform !== 'win32') throw new Error('Impression Windows disponible uniquement sur Windows');
  const tempFile = path.join(os.tmpdir(), `guichetplus-${crypto.randomUUID()}.bin`);
  fs.writeFileSync(tempFile, payload, { mode: 0o600 });
  try {
    const script = helperPath(resourcesPath, developmentDirectory);
    const args = ['-File', script, '-Mode', config.transport];
    if (config.transport === 'windows') args.push('-PrinterName', config.printerName);
    else args.push('-PortName', config.serialPort, '-BaudRate', String(config.serialBaud || 9600));
    args.push('-FilePath', tempFile);
    await powershell(args, 15000);
  } finally {
    try { fs.unlinkSync(tempFile); } catch {}
  }
}

async function send(payload, config, resourcesPath, developmentDirectory) {
  if (config.transport === 'network') await sendNetwork(payload, config);
  else if (config.transport === 'windows' || config.transport === 'serial') {
    await sendWindows(payload, config, resourcesPath, developmentDirectory);
  } else throw new Error('Mode de connexion inconnu');
  return { bytes: payload.length, transport: config.transport };
}

async function check(config) {
  if (config.transport === 'network') return sendNetwork(Buffer.alloc(0), config);
  const devices = await listDevices();
  if (config.transport === 'windows') {
    const printer = devices.printers.find(item => item.Name === config.printerName);
    if (!printer) throw new Error('Imprimante Windows déconnectée ou pilote absent');
    if (printer.WorkOffline) throw new Error('Imprimante Windows hors ligne');
    return;
  }
  if (config.transport === 'serial') {
    if (!devices.serialPorts.some(item => item.DeviceID === config.serialPort)) throw new Error('Port Bluetooth COM indisponible');
    return;
  }
  throw new Error('Mode de connexion inconnu');
}

module.exports = { listDevices, send, check };
