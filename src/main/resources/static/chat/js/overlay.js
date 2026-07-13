/**
 * Upload/busy overlay.
 *
 * The progress bar only ever claims what we can actually measure: the byte
 * transfer, reported by XHR. Once the bytes are on the server we have no signal
 * about how far along the LLM is, so the bar switches to indeterminate and we
 * show elapsed time instead of inventing a percentage.
 */

const SLOW_AFTER_MS = 30_000;

const el = id => document.getElementById(id);

let elapsedTimer = null;
let slowTimer    = null;
let startedAt    = 0;

function reset() {
  clearInterval(elapsedTimer);
  clearTimeout(slowTimer);
  elapsedTimer = null;
  slowTimer    = null;

  el('overlay-track').classList.remove('indeterminate');
  el('overlay-progress').style.width = '0%';
  el('overlay-file').hidden    = true;
  el('overlay-elapsed').hidden = true;
  el('overlay-cancel').hidden  = true;
  el('overlay-cancel').onclick = null;
}

/** Phase 1: real, measured progress while the file is being sent. */
export function showUploadOverlay(fileName, onCancel) {
  reset();
  el('upload-overlay').classList.add('active');
  el('overlay-message').textContent = 'Subiendo tu factura…';
  el('overlay-hint').textContent    = 'Enviando el archivo al servidor.';

  const file = el('overlay-file');
  file.textContent = fileName;
  file.hidden      = false;

  const cancel = el('overlay-cancel');
  cancel.hidden  = false;
  cancel.onclick = onCancel;
}

export function setUploadProgress(pct) {
  el('overlay-progress').style.width = Math.min(100, Math.round(pct)) + '%';
}

/** Phase 2: bytes delivered. We no longer know anything — say so, and count up. */
export function enterProcessing() {
  el('overlay-message').textContent = 'Analizando tu factura con IA…';
  el('overlay-hint').textContent    = 'Suele tardar entre 10 y 30 segundos.';
  el('overlay-track').classList.add('indeterminate');
  el('overlay-progress').style.width = '100%';

  startedAt = Date.now();
  const elapsed = el('overlay-elapsed');
  elapsed.hidden = false;
  elapsed.textContent = '0 s';

  elapsedTimer = setInterval(() => {
    elapsed.textContent = Math.floor((Date.now() - startedAt) / 1000) + ' s';
  }, 1000);

  slowTimer = setTimeout(() => {
    el('overlay-hint').textContent =
      'Está tardando más de lo normal. Puedes cancelar y volver a intentarlo.';
    el('overlay-hint').classList.add('warn');
  }, SLOW_AFTER_MS);
}

/** Indeterminate spinner for short server-side work with no cancel path. */
export function showBusyOverlay(label) {
  reset();
  el('upload-overlay').classList.add('active');
  el('overlay-message').textContent = label;
  el('overlay-hint').textContent    = 'Un momento…';
  el('overlay-track').classList.add('indeterminate');
  el('overlay-progress').style.width = '100%';
}

export function hideOverlay(success = true) {
  clearInterval(elapsedTimer);
  clearTimeout(slowTimer);
  el('overlay-hint').classList.remove('warn');

  const close = () => {
    el('upload-overlay').classList.remove('active');
    setTimeout(reset, 300);
  };

  if (!success) return close();

  // Land the bar on a full, solid 100% before dismissing.
  el('overlay-track').classList.remove('indeterminate');
  el('overlay-progress').style.width = '100%';
  el('overlay-message').textContent  = 'Listo';
  el('overlay-cancel').hidden        = true;
  setTimeout(close, 400);
}