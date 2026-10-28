// Service worker: يعمل التطبيق دون اتصال. الصفحة: الشبكة أولاً (تحديثات فورية) ثم النسخة المحفوظة. الباقي: المحفوظ ثم تحديث بالخلفية.
const C = 'mohasbak-4343cddd84';
const CORE = ['./', 'index.html', 'manifest.webmanifest', 'icon.svg', 'icon-192.png', 'icon-512.png'];
self.addEventListener('install', (e) => { e.waitUntil(caches.open(C).then((c) => c.addAll(CORE)).then(() => self.skipWaiting())); });
self.addEventListener('activate', (e) => { e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== C).map((k) => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const put = (res) => { if (res && res.ok && (res.type === 'basic' || res.type === 'cors')) { const cp = res.clone(); caches.open(C).then((c) => c.put(req, cp)); } return res; };
  if (req.url.includes("version.json")) return; // فحص التحديث يذهب للشبكة دائماً
  if (req.mode === 'navigate') {
    // no-cache: لا نأخذ الصفحة من ذاكرة المتصفح القديمة، نتحقق من الخادم كل مرة
    e.respondWith(fetch(req.url, { cache: 'no-cache', credentials: 'same-origin' }).then(put).catch(() => caches.match(req).then((r) => r || caches.match('index.html'))));
    return;
  }
  e.respondWith(caches.match(req).then((hit) => { const net = fetch(req).then(put).catch(() => hit); return hit || net; }));
});
