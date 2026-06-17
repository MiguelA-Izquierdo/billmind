import { SESSION_ID } from './session.js';
import { state } from './state.js';
import { autoResize } from './utils.js';
import { loadInvoices, updateInvoiceCard } from './invoices.js';
import { trigger, onDragOver, onDragLeave, onDrop, uploadFile } from './uploader.js';
import { sendMessage, checkBackendStatus } from './chat.js';
import { initCharLimit } from './char-limit.js';
import { loadComparison } from './comparison.js';

// Session label
document.getElementById('session-label').textContent = 'Sesión: ' + SESSION_ID.slice(0, 8) + '…';

// Invoice selector
document.getElementById('invoice-select').addEventListener('change', function (e) {
  state.selectedInvoiceId = this.value || null;
  state.conversationId    = null;
  updateInvoiceCard(state.selectedInvoiceId);

  const hasInvoice = !!state.selectedInvoiceId;
  document.getElementById('chat-input').disabled = !hasInvoice;
  document.getElementById('send-btn').disabled   = !hasInvoice;

  if (hasInvoice) {
    document.getElementById('welcome').style.display = 'none';
    document.getElementById('chat-input').focus();
    enableSuggestions(true);
    checkBackendStatus();

    // e.isTrusted = false when dispatched programmatically after upload
    // (comparison is already shown from the upload response in that case)
    if (e.isTrusted) {
      const label = this.options[this.selectedIndex]?.textContent ?? '';
      loadComparison(state.selectedInvoiceId, label);
    }
  }
});

// Upload zone
const uploadZone = document.getElementById('upload-zone');
uploadZone.addEventListener('click',      trigger);
uploadZone.addEventListener('dragover',   onDragOver);
uploadZone.addEventListener('dragleave',  onDragLeave);
uploadZone.addEventListener('drop',       onDrop);

document.getElementById('upload-btn').addEventListener('click', e => {
  e.stopPropagation();
  trigger();
});

document.getElementById('upload-mini-btn')?.addEventListener('click', trigger);

document.getElementById('file-input').addEventListener('change', function () {
  if (this.files[0]) uploadFile(this.files[0]);
});

// Chat input
const chatInput = document.getElementById('chat-input');
chatInput.addEventListener('input', () => autoResize(chatInput));
chatInput.addEventListener('keydown', e => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
});

document.getElementById('send-btn').addEventListener('click', sendMessage);

// Suggestions
function enableSuggestions(on) {
  ['s1', 's2', 's3', 's4', 's5'].forEach(id => {
    const btn = document.getElementById(id);
    if (btn) btn.disabled = !on;
  });
}

['s1', 's2', 's3', 's4', 's5'].forEach(id => {
  document.getElementById(id)?.addEventListener('click', function () {
    chatInput.value = this.textContent.trim();
    chatInput.focus();
    autoResize(chatInput);
  });
});

// Init
loadInvoices();
checkBackendStatus();
initCharLimit();