'use strict';

const http = require('http');
const fs = require('fs');
const fsp = fs.promises;
const path = require('path');

const ROOT = __dirname;
const portArg = process.argv.find(value => value.startsWith('--port='));
const argIndex = process.argv.indexOf('--port');
const requestedPort = portArg ? portArg.slice('--port='.length) : (argIndex >= 0 ? process.argv[argIndex + 1] : null);
const PORT = Number(requestedPort || process.env.PORT || 8080);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.txt': 'text/plain; charset=utf-8'
};

function reply(res, status, body, type = 'text/plain; charset=utf-8') {
  const data = Buffer.from(body);
  res.writeHead(status, {
    'Content-Type': type,
    'Content-Length': data.length,
    'Cache-Control': 'no-store'
  });
  res.end(data);
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    let relative = decodeURIComponent(url.pathname);
    if (relative === '/') relative = '/index.html';
    const file = path.resolve(ROOT, `.${relative}`);
    if (!file.startsWith(ROOT + path.sep)) return reply(res, 403, 'Nicht erlaubt');
    const stat = await fsp.stat(file).catch(() => null);
    if (!stat || !stat.isFile()) return reply(res, 404, 'Nicht gefunden');
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream',
      'Content-Length': stat.size,
      'Cache-Control': 'no-store'
    });
    fs.createReadStream(file).pipe(res);
  } catch (error) {
    console.error(error);
    reply(res, 500, 'Serverfehler');
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`MiniGolf Google-Drive-Test läuft auf http://localhost:${PORT}`);
  console.log('Freigaben werden nicht lokal gespeichert.');
  console.log('Beenden mit Strg + C');
});
