const CACHE_NAME = 'thai-boxer-workout-timer-v1';
const urlsToCache = [
  // App shell
  '/',
  '/index.html',
  '/manifest.json',
  '/assets/icon.svg',
  
  // Scripts
  '/index.tsx',
  '/App.tsx',
  '/types.ts',
  '/utils/state.ts',
  '/utils/time.ts',
  '/utils/gemini.ts',
  '/utils/storage.ts',
  '/components/icons.tsx',
  '/components/WorkoutSetup.tsx',
  '/components/CircularProgress.tsx',
  '/components/Controls.tsx',
  '/components/WorkoutScreen.tsx',
  '/components/WorkoutSelectionScreen.tsx',
  '/components/WorkoutGeneratorScreen.tsx',
  '/constants/workouts.ts',
  '/constants/translations.ts',
  '/hooks/useAudio.ts',
  '/contexts/LanguageContext.tsx',

  // External libs
  'https://cdn.tailwindcss.com',
  'https://esm.sh/react@^19.1.0',
  'https://esm.sh/react-dom@^19.1.0/client',
  'https://esm.sh/@google/genai'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => {
        console.log('Opened cache');
        // We use addAll, which is atomic. If any file fails, the whole cache operation fails.
        return cache.addAll(urlsToCache);
      })
  );
});

self.addEventListener('fetch', (event) => {
  event.respondWith(
    caches.match(event.request)
      .then((response) => {
        // Cache hit - return response
        if (response) {
          return response;
        }
        // Not in cache - fetch from network
        return fetch(event.request);
      }
    )
  );
});

// Clean up old caches
self.addEventListener('activate', (event) => {
  const cacheWhitelist = [CACHE_NAME];
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.map((cacheName) => {
          if (cacheWhitelist.indexOf(cacheName) === -1) {
            return caches.delete(cacheName);
          }
        })
      );
    })
  );
});