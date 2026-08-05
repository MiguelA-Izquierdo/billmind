import { state } from './state.js';

/**
 * Single source of truth for what the UI offers at any moment.
 * Every module that changes `state` calls syncChat() instead of poking
 * individual controls, so the chat can never end up enabled without an invoice.
 */

// There is no drag & drop on a touch screen — don't tell a phone user to drag.
const isTouch = window.matchMedia('(pointer: coarse)').matches;

const EMPTY_COPY = {
  empty: {
    title: 'Sube tu factura de la luz',
    sub: isTouch
      ? 'Elige el PDF de tu factura y en unos segundos te digo si estás pagando de más.'
      : 'Arrástrala aquí —o suéltala en cualquier parte de la pantalla— y en unos segundos te digo si estás pagando de más.',
  },
  unselected: {
    title: 'Sube otra factura',
    sub: isTouch
      ? 'O elige una de las que ya has analizado desde el menú ☰ y sigue preguntando.'
      : 'O elige una de las que ya has analizado en el panel de la izquierda y sigue preguntando.',
  },
};

let wasUnlocked = false;

export function syncChat() {
  // Having an invoice is what unlocks the chat; streaming only pauses input,
  // so the "upload first" hint must not reappear mid-answer.
  const hasInvoice = !!state.selectedInvoiceId && !state.isUploading;
  // A 429 told us exactly how long to wait — pressing send before then only earns another one.
  const cooling    = state.cooldownUntil > Date.now();
  const canType    = hasInvoice && !state.isStreaming && !cooling;

  const input   = document.getElementById('chat-input');
  const wrapper = document.querySelector('.input-wrapper');

  input.disabled = !canType;
  document.getElementById('send-btn').disabled = !canType;

  // Until now the input looked identical locked and unlocked, so nothing told
  // you the chat had just become usable.
  wrapper.classList.toggle('ready', hasInvoice && !cooling);
  input.placeholder = placeholderFor(hasInvoice, cooling);

  if (hasInvoice && !wasUnlocked) pulseInput(wrapper);
  wasUnlocked = hasInvoice;

  document.getElementById('hint-locked').hidden = hasInvoice;
  document.getElementById('hint-ready').hidden  = !hasInvoice;

  document.querySelectorAll('button[data-upload-trigger]')
    .forEach(btn => { btn.disabled = state.isUploading || cooling; });

  ['s1', 's2', 's3', 's4', 's5']
    .forEach(id => { document.getElementById(id).disabled = !canType; });
}

function placeholderFor(hasInvoice, cooling) {
  if (cooling)     return 'Recargando… en un momento seguimos.';
  if (!hasInvoice) return 'Sube una factura para poder preguntar…';
  return 'Pregúntame lo que quieras sobre tu factura…';
}

/** Fires once, when the chat unlocks — not after every streamed answer. */
function pulseInput(wrapper) {
  wrapper.classList.remove('pulse');
  void wrapper.offsetWidth;
  wrapper.classList.add('pulse');
  setTimeout(() => wrapper.classList.remove('pulse'), 3400);
}

export function showEmptyState(mode) {
  const copy = EMPTY_COPY[mode] ?? EMPTY_COPY.empty;
  document.getElementById('hero-title').textContent = copy.title;
  document.getElementById('hero-sub').textContent   = copy.sub;
  document.getElementById('empty-state').hidden     = false;
}

export function hideEmptyState() {
  document.getElementById('empty-state').hidden = true;
}

/** Below 860px the sidebar is an off-canvas drawer (see responsive.css). */
export function setDrawer(open) {
  document.getElementById('sidebar').classList.toggle('open', open);
  document.getElementById('sidebar-scrim').hidden = !open;
  document.getElementById('menu-btn').setAttribute('aria-expanded', String(open));
}

export function closeDrawer() {
  setDrawer(false);
}

export function isDrawerOpen() {
  return document.getElementById('sidebar').classList.contains('open');
}

/** Screen-reader milestones. Announcing every streamed token would be unusable. */
export function announce(message) {
  document.getElementById('sr-status').textContent = message;
}

export function isEmptyStateVisible() {
  return !document.getElementById('empty-state').hidden;
}

/** Errors belong where the user just dropped the file, not pinned to the input bar. */
export function showHeroError(message) {
  const el = document.getElementById('hero-error');
  el.textContent = message;
  el.hidden = false;

  const zone = document.getElementById('hero-dropzone');
  zone.classList.remove('shake');
  void zone.offsetWidth; // restart the animation on a repeated failure
  zone.classList.add('shake');
}

export function clearHeroError() {
  const el = document.getElementById('hero-error');
  el.hidden = true;
  el.textContent = '';
  document.getElementById('hero-dropzone').classList.remove('shake');
}

export function setDragVeil(active) {
  document.getElementById('drag-veil').classList.toggle('active', active);
  document.getElementById('hero-dropzone').classList.toggle('dragover', active);
}

/** No invoices yet: the sidebar defers to the hero instead of offering a rival CTA. */
export function showSidebarEmpty(isEmpty) {
  document.getElementById('sidebar-empty').hidden  = !isEmpty;
  document.getElementById('invoice-select').hidden = isEmpty;
  document.getElementById('upload-mini').hidden    = isEmpty;
}