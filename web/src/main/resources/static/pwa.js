// Politisation Populaire Française: registers the service worker that keeps the practical guides
// for offline reading (D-091). Only a plus: every page of the site is readable without this script.
'use strict';
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(function () { /* Offline reading stays unavailable; the site works. */ });
}
