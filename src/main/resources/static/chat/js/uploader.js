import { SESSION_ID } from './session.js';
import { state } from './state.js';
import { showOverlay, hideOverlay } from './overlay.js';
import { showBanner, showComparisonResult } from './messages.js';
import { loadInvoices } from './invoices.js';

const UPLOAD_ENDPOINT = '/api/v1/invoices';

export function trigger() {
  document.getElementById('file-input').click();
}

export function onDragOver(e) {
  e.preventDefault();
  document.getElementById('upload-zone').classList.add('dragover');
}

export function onDragLeave() {
  document.getElementById('upload-zone').classList.remove('dragover');
}

export function onDrop(e) {
  e.preventDefault();
  document.getElementById('upload-zone').classList.remove('dragover');
  const file = e.dataTransfer?.files?.[0];
  if (file) uploadFile(file);
}

export async function uploadFile(file) {
  if (!file) return;
  if (file.type !== 'application/pdf' && !file.name.endsWith('.pdf')) {
    showBanner('Solo se aceptan archivos PDF.', 'error');
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    showBanner('El archivo supera el límite de 5 MB.', 'error');
    return;
  }

  state.isUploading = true;
  setUploadBlocked(true);
  showOverlay();

  const formData = new FormData();
  formData.append('file', file);

  try {
    const res  = await fetch(UPLOAD_ENDPOINT, {
      method: 'POST',
      headers: { 'X-Session-Id': SESSION_ID },
      body: formData,
    });
    const body = await res.json();

    if (!res.ok) {
      showBanner(body.message ?? 'Error al subir la factura (HTTP ' + res.status + ')', 'error');
      hideOverlay(false);
      return;
    }

    hideOverlay(true);
    document.getElementById('file-input').value = '';
    await loadInvoices();

    const newId = body.data?.invoiceId;
    if (newId) {
      const sel = document.getElementById('invoice-select');
      sel.value = newId;
      sel.dispatchEvent(new Event('change'));
    }

    showComparisonResult(body.data);
  } catch (err) {
    showBanner('Error de red al subir la factura: ' + err.message, 'error');
    hideOverlay(false);
  } finally {
    state.isUploading = false;
    setUploadBlocked(false);
  }
}

function setUploadBlocked(blocked) {
  const hasInvoice = !!state.selectedInvoiceId && !blocked;
  document.getElementById('chat-input').disabled = !hasInvoice;
  document.getElementById('send-btn').disabled   = !hasInvoice;
  document.getElementById('upload-btn').disabled =  blocked;
  const miniBtn = document.getElementById('upload-mini-btn');
  if (miniBtn) miniBtn.disabled = blocked;
}