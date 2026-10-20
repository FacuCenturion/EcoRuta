// Service worker de la app del chofer: permite instalarla y que abra rápido aunque la señal
// sea mala. Guarda los archivos de la app; los datos (la API) siempre se piden al servidor.
const CACHE = 'ecoruta-chofer-v1';

self.addEventListener('install', (evento) => {
  evento.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(['/', '/index.html', '/icono.svg'])));
  self.skipWaiting();
});

self.addEventListener('activate', (evento) => {
  evento.waitUntil(
    caches.keys().then((claves) => Promise.all(claves.filter((c) => c !== CACHE).map((c) => caches.delete(c)))),
  );
  self.clients.claim();
});

self.addEventListener('fetch', (evento) => {
  const pedido = evento.request;
  const url = new URL(pedido.url);
  // Solo los archivos de la app, nunca la API ni los mapas
  if (pedido.method !== 'GET' || url.origin !== self.location.origin) return;
  evento.respondWith(
    fetch(pedido)
      .then((respuesta) => {
        const copia = respuesta.clone();
        caches.open(CACHE).then((cache) => cache.put(pedido, copia));
        return respuesta;
      })
      .catch(() => caches.match(pedido).then((guardada) => guardada ?? caches.match('/index.html'))),
  );
});

// Tocar la notificación de ruta nueva abre la app
self.addEventListener('notificationclick', (evento) => {
  evento.notification.close();
  evento.waitUntil(
    self.clients.matchAll({ type: 'window' }).then((ventanas) => {
      const abierta = ventanas.find((v) => 'focus' in v);
      return abierta ? abierta.focus() : self.clients.openWindow('/');
    }),
  );
});
