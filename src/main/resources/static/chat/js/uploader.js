import { SESSION_ID } from './session.js';
import { state } from './state.js';
import { showUploadOverlay, setUploadProgress, enterProcessing, hideOverlay } from './overlay.js';
import { showBanner, showComparisonResult } from './messages.js';
import { loadInvoices, selectInvoice } from './invoices.js';
import { showBotToast } from './char-limit.js';
import {
  syncChat, setDragVeil, isEmptyStateVisible,
  showHeroError, clearHeroError, announce, closeDrawer,
} from './ui.js';

const UPLOAD_ENDPOINT = '/api/v1/invoices';
const MAX_BYTES       = 5 * 1024 * 1024;
const TIMEOUT_MS      = 90_000;

const SECTOR_NAMES = {
  GAS:    'gas',
  WATER:  'agua',
  TELECOM: 'telefonía',
  OTHER:  null,
};

function buildRejectionToast(backendMessage) {
  const match  = backendMessage?.match(/suministro '(\w+)'/);
  const type   = match?.[1];
  const sector = SECTOR_NAMES[type];

  if (sector) {
    return `Sector de ${sector} detectado. Lo tenemos en el radar y lo estamos investigando. De momento, solo proceso facturas de electricidad.`;
  }
  return 'Esto no parece una factura de suministro del hogar. Más sectores en camino, ¡paciencia, humano!';
}

export function trigger() {
  if (state.isUploading) return;
  closeDrawer(); // the trigger may have been tapped inside the mobile drawer
  document.getElementById('file-input').click();
}

/* ── Drag & drop over the whole window ─────────────────────────────────────
   dragenter/dragleave fire for every child element the cursor crosses, so the
   veil is refcounted rather than toggled. */

let dragDepth = 0;

function carriesFiles(e) {
  return Array.from(e.dataTransfer?.types ?? []).includes('Files');
}

export function onDragEnter(e) {
  if (!carriesFiles(e) || state.isUploading) return;
  dragDepth++;
  setDragVeil(true);
}

export function onDragOver(e) {
  if (!carriesFiles(e) || state.isUploading) return;
  e.preventDefault(); // without this the browser never fires `drop`
  e.dataTransfer.dropEffect = 'copy';
}

export function onDragLeave(e) {
  if (!carriesFiles(e)) return;
  dragDepth = Math.max(0, dragDepth - 1);
  if (dragDepth === 0) setDragVeil(false);
}

export function onDrop(e) {
  if (!carriesFiles(e)) return;
  e.preventDefault();
  dragDepth = 0;
  setDragVeil(false);
  if (state.isUploading) return;

  const file = e.dataTransfer?.files?.[0];
  if (file) uploadFile(file);
}

/* ── Transport ────────────────────────────────────────────────────────────
   XHR, not fetch: it is the only way to observe upload progress and to abort a
   request the user changed their mind about. */

let inFlight = null;

function postInvoice(file, onProgress, onBytesSent) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    inFlight  = xhr;

    xhr.open('POST', UPLOAD_ENDPOINT);
    xhr.setRequestHeader('X-Session-Id', SESSION_ID);
    xhr.timeout = TIMEOUT_MS;

    xhr.upload.onprogress = e => {
      if (e.lengthComputable) onProgress((e.loaded / e.total) * 100);
    };
    xhr.upload.onload = onBytesSent;

    xhr.onload    = () => resolve({ status: xhr.status, body: safeJson(xhr.responseText) });
    xhr.onerror   = () => reject(new Error('network'));
    xhr.ontimeout = () => reject(new Error('timeout'));
    xhr.onabort   = () => reject(new Error('aborted'));

    const formData = new FormData();
    formData.append('file', file);
    xhr.send(formData);
  });
}

function safeJson(text) {
  try { return JSON.parse(text); } catch { return {}; }
}

function cancelUpload() {
  inFlight?.abort();
}

/* ── Upload ───────────────────────────────────────────────────────────────── */

function rejectFile(file) {
  if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
    return 'Solo se aceptan archivos PDF.';
  }
  if (file.size > MAX_BYTES) {
    return `El archivo pesa ${(file.size / 1024 / 1024).toFixed(1)} MB y el límite son 5 MB.`;
  }
  return null;
}

/** Errors surface where the user is looking: in the dropzone, or by the input. */
function reportError(message) {
  if (isEmptyStateVisible()) showHeroError(message);
  else showBanner(message, 'error');
}

export async function uploadFile(file) {
  if (!file || state.isUploading) return;
  clearHeroError();

  const rejection = rejectFile(file);
  if (rejection) {
    reportError(rejection);
    return;
  }

  state.isUploading = true;
  syncChat();
  showUploadOverlay(file.name, cancelUpload);
  announce('Subiendo y analizando tu factura.');

  try {
    const { status, body } = await postInvoice(file, setUploadProgress, enterProcessing);

    if (status < 200 || status >= 300) {
      reportFailure(status, body);
      return;
    }

    hideOverlay(true);
    document.getElementById('file-input').value = '';
    await loadInvoices();
    selectUploaded(body.data?.invoiceId);
    showComparisonResult(body.data);
    announce('Factura analizada. Ya puedes preguntar.');
  } catch (err) {
    hideOverlay(false);
    handleTransportError(err);
  } finally {
    inFlight = null;
    state.isUploading = false;
    syncChat();
  }
}

function handleTransportError(err) {
  document.getElementById('file-input').value = '';

  if (err.message === 'aborted') return; // the user asked for it — nothing to report
  if (err.message === 'timeout') {
    reportError('La factura ha tardado demasiado en procesarse. Vuelve a intentarlo en un momento.');
    return;
  }
  reportError('No se ha podido conectar con el servidor. Revisa tu conexión y vuelve a intentarlo.');
}

function reportFailure(status, body) {
  if (status === 422 && body.message?.includes('suministro')) {
    showBotToast(buildRejectionToast(body.message));
  } else {
    reportError(body.message ?? `Error al subir la factura (HTTP ${status})`);
  }
  hideOverlay(false);
}

function selectUploaded(invoiceId) {
  if (!invoiceId) return;
  // The comparison already came back with the upload — don't fetch it twice.
  selectInvoice(invoiceId, { withComparison: false });
}