const { app, BrowserWindow } = require('electron');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { adapt: adaptEscPos } = require('./escpos-compat.cjs');
const transports = require('./windows-transports.cjs');

const ORIGIN = 'https://gplus.digitalfactory.sn';
let mainWindow;
let server;

function configPath() { return path.join(app.getPath('userData'), 'config.json'); }
const defaults = { transport: 'network', printerHost: '192.168.1.196', printerPort: 9100, printerName: '', serialPort: '', serialBaud: 9600, printerProfile: 'auto' };
function readConfig() {
  try { return { ...defaults, ...JSON.parse(fs.readFileSync(configPath(), 'utf8')) }; }
  catch { return { ...defaults }; }
}
function writeConfig(value) { fs.writeFileSync(configPath(), JSON.stringify(value, null, 2)); }
function cors(req) {
  const origin = req.headers.origin;
  return (origin === ORIGIN || origin === 'null') ? {
    'access-control-allow-origin': origin,
    'access-control-allow-methods': 'GET, POST, OPTIONS',
    'access-control-allow-headers': 'Content-Type',
    'access-control-allow-private-network': 'true',
    'access-control-max-age': '600',
    'vary': 'Origin'
  } : {};
}
function json(res, status, value, headers = {}) {
  const body = Buffer.from(JSON.stringify(value));
  res.writeHead(status, { ...headers, 'content-type': 'application/json; charset=utf-8', 'content-length': body.length });
  res.end(body);
}
async function body(req) {
  const chunks = []; let size = 0;
  for await (const chunk of req) { size += chunk.length; if (size > 768 * 1024) throw new Error('Requête trop volumineuse'); chunks.push(chunk); }
  return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
}
async function sendPrinter(payload) {
  const config = readConfig();
  const compatiblePayload = adaptEscPos(payload, config.printerProfile);
  return { ...(await transports.send(compatiblePayload, config, app.isPackaged ? process.resourcesPath : null, __dirname)), ...config };
}
function startServer() {
  server = http.createServer(async (req, res) => {
    const headers = cors(req);
    try {
      const url = new URL(req.url, 'http://127.0.0.1:3210');
      if (req.method === 'OPTIONS') { res.writeHead(Object.keys(headers).length ? 204 : 403, headers); return res.end(); }
      if (req.method === 'GET' && url.pathname === '/api/health') {
        const config = readConfig();
        try { await transports.check(config); return json(res, 200, { service: 'ok', printer: { online: true, ...config } }, headers); }
        catch (error) { return json(res, 503, { service: 'ok', printer: { online: false, ...config, error: error.message } }, headers); }
      }
      if (req.method === 'GET' && url.pathname === '/api/devices') return json(res, 200, await transports.listDevices(), headers);
      if (req.method === 'GET' && url.pathname === '/api/config') return json(res, 200, readConfig(), headers);
      if (req.method === 'POST' && url.pathname === '/api/config') {
        const data = await body(req); const port = Number(data.printerPort); const baud = Number(data.serialBaud || 9600);
        const transport = ['network', 'windows', 'serial'].includes(data.transport) ? data.transport : '';
        if (!transport) return json(res, 422, { error: 'Mode de connexion invalide' }, headers);
        if (transport === 'network' && (!String(data.printerHost || '').trim() || !Number.isInteger(port) || port < 1 || port > 65535)) return json(res, 422, { error: 'Adresse réseau invalide' }, headers);
        if (transport === 'windows' && !String(data.printerName || '').trim()) return json(res, 422, { error: 'Sélectionnez une imprimante USB/Bluetooth Windows' }, headers);
        if (transport === 'serial' && (!/^COM\d+$/i.test(String(data.serialPort || '')) || ![9600,19200,38400,57600,115200,230400,460800,921600].includes(baud))) return json(res, 422, { error: 'Port Bluetooth virtuel COM invalide' }, headers);
        const profile = ['auto', 'native', 'legacy'].includes(data.printerProfile) ? data.printerProfile : 'auto';
        writeConfig({ transport, printerHost: String(data.printerHost || '').trim(), printerPort: port || 9100, printerName: String(data.printerName || '').trim(), serialPort: String(data.serialPort || '').toUpperCase(), serialBaud: baud, printerProfile: profile });
        return json(res, 200, readConfig(), headers);
      }
      if (req.method === 'POST' && url.pathname === '/api/print/raw') {
        const data = await body(req);
        if (typeof data.data !== 'string' || !/^[A-Za-z0-9+/]*={0,2}$/.test(data.data)) return json(res, 400, { error: 'Flux ESC/POS invalide' }, headers);
        const payload = Buffer.from(data.data, 'base64');
        if (!payload.length || payload.length > 512 * 1024) return json(res, 413, { error: 'Taille ESC/POS invalide' }, headers);
        return json(res, 201, { printed: true, ...(await sendPrinter(payload)) }, headers);
      }
      if (req.method === 'POST' && url.pathname === '/api/test') {
        const payload = Buffer.from('\x1b@\x1ba\x01\x1bE\x01GUICHET+\nTest Windows\n\x1bE\x00\n\n\x1dVB\x00', 'binary');
        return json(res, 201, { printed: true, ...(await sendPrinter(payload)) }, headers);
      }
      return json(res, 404, { error: 'Route inconnue' }, headers);
    } catch (error) { return json(res, 500, { error: error.message || 'Erreur interne' }, headers); }
  });
  server.listen(3210, '127.0.0.1');
}
function createWindow(show = true) {
  mainWindow = new BrowserWindow({ width: 720, height: 620, show, title: 'GUICHET+ Kiosk', icon: path.join(__dirname, 'public', 'guichetplus-icon.png'), webPreferences: { contextIsolation: true, nodeIntegration: false } });
  mainWindow.loadFile(path.join(__dirname, 'public', 'index.html'));
  mainWindow.on('close', event => { if (!app.isQuitting) { event.preventDefault(); mainWindow.hide(); } });
}

const lock = app.requestSingleInstanceLock();
if (!lock) app.quit();
else {
  app.on('second-instance', () => { if (mainWindow) { mainWindow.show(); mainWindow.focus(); } });
  app.whenReady().then(() => {
    startServer();
    app.setLoginItemSettings({ openAtLogin: true, args: ['--background'] });
    createWindow(!process.argv.includes('--background'));
  });
}
app.on('before-quit', () => { app.isQuitting = true; if (server) server.close(); });
app.on('window-all-closed', event => event.preventDefault());
