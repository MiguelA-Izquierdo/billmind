import { SESSION_ID } from './session.js';
import { autoResize } from './utils.js';
import { loadInvoices, selectInvoice } from './invoices.js';
import { trigger, onDragEnter, onDragOver, onDragLeave, onDrop, uploadFile } from './uploader.js';
import { sendMessage, checkBackendStatus } from './chat.js';
import { initCharLimit } from './char-limit.js';
import { syncChat, showEmptyState, setDrawer, closeDrawer, isDrawerOpen } from './ui.js';

// Session label
document.getElementById('session-label').textContent = 'Sesión: ' + SESSION_ID.slice(0, 8) + '…';

// Invoice selector
document.getElementById('invoice-select').addEventListener('change', function () {
  selectInvoice(this.value || null, { withComparison: true });
});

// Every upload affordance (hero, sidebar link, input hint, "subir otra") opts in
// with data-upload-trigger and is wired here by delegation.
document.addEventListener('click', e => {
  if (e.target.closest('[data-upload-trigger]')) trigger();
});

document.getElementById('hero-dropzone').addEventListener('keydown', e => {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault();
    trigger();
  }
});

// Mobile drawer
document.getElementById('menu-btn').addEventListener('click', () => setDrawer(!isDrawerOpen()));
document.getElementById('sidebar-scrim').addEventListener('click', closeDrawer);
document.addEventListener('keydown', e => {
  if (e.key === 'Escape' && isDrawerOpen()) closeDrawer();
});

// Drag & drop anywhere in the window
window.addEventListener('dragenter', onDragEnter);
window.addEventListener('dragover',  onDragOver);
window.addEventListener('dragleave', onDragLeave);
window.addEventListener('drop',      onDrop);

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
['s1', 's2', 's3', 's4', 's5'].forEach(id => {
  document.getElementById(id).addEventListener('click', function () {
    chatInput.value = this.textContent.trim();
    chatInput.focus();
    autoResize(chatInput);
  });
});

// Init
syncChat();
showEmptyState('empty'); // settle the copy for this device before the fetch resolves
loadInvoices();
checkBackendStatus();
initCharLimit();