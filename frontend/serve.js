/**
 * Zero-dependency static server for the API console.
 *
 *   node frontend/serve.js        ->  http://localhost:5173
 *
 * Serving over http:// rather than opening index.html from disk gives the page a
 * real origin. Browsers treat a file:// page as origin "null", which some block
 * from calling http://localhost outright - this sidesteps that entirely.
 */
const http = require("http");
const fs = require("fs");
const path = require("path");

const PORT = process.env.PORT || 5173;
const ROOT = __dirname;

const TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon"
};

http.createServer((req, res) => {
  const urlPath = decodeURIComponent(req.url.split("?")[0]);
  const rel = urlPath === "/" ? "index.html" : urlPath.replace(/^\/+/, "");
  const file = path.join(ROOT, rel);

  // Never serve anything outside this folder.
  if (!file.startsWith(ROOT)) {
    res.writeHead(403).end("Forbidden");
    return;
  }

  fs.readFile(file, (err, data) => {
    if (err) {
      res.writeHead(404, { "Content-Type": "text/plain" }).end("Not found: " + rel);
      return;
    }
    res.writeHead(200, {
      "Content-Type": TYPES[path.extname(file).toLowerCase()] || "application/octet-stream",
      "Cache-Control": "no-store"
    }).end(data);
  });
}).listen(PORT, () => {
  console.log("API console  ->  http://localhost:" + PORT);
  console.log("Serving      ->  " + ROOT);
  console.log("Stop with Ctrl+C");
});
