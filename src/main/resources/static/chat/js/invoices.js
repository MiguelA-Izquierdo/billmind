import { SESSION_ID } from './session.js';
import { state } from './state.js';
import { fmtDate, fmtEuros, fmtSupply } from './utils.js';
import { showBanner, hideBanner, clearMessages } from './messages.js';
import { describeFailure } from './errors.js';
import { loadComparison } from './comparison.js';
import { checkBackendStatus } from './chat.js';
import {
  showEmptyState, hideEmptyState, showSidebarEmpty,
  syncChat, clearHeroError, closeDrawer,
} from './ui.js';

const INVOICES_ENDPOINT = '/api/v1/invoices';

/**
 * The one way an invoice becomes the active one.
 *
 * `withComparison` is false right after an upload, where the comparison already
 * came back in the upload response and re-fetching it would be a second render.
 * This used to be inferred from `event.isTrusted`, which broke under any
 * synthetic event and made the path impossible to test.
 */
export function selectInvoice(invoiceId, { withComparison } = { withComparison: true }) {
  const sel = document.getElementById('invoice-select');
  sel.value = invoiceId ?? '';

  state.selectedInvoiceId = invoiceId || null;
  state.conversationId    = null;

  // The backend conversation is gone; leaving its answers on screen would read
  // as one thread while the assistant has no memory of them.
  clearMessages();
  hideBanner();
  clearHeroError();
  closeDrawer(); // on mobile the drawer covers the answer you just asked for

  updateInvoiceCard(state.selectedInvoiceId);
  syncChat();

  if (!state.selectedInvoiceId) {
    showEmptyState('unselected');
    return;
  }

  hideEmptyState();
  document.getElementById('chat-input').focus();
  checkBackendStatus();

  if (withComparison) {
    const label = sel.options[sel.selectedIndex]?.textContent ?? '';
    loadComparison(state.selectedInvoiceId, label);
  }
}

export async function loadInvoices() {
  const sel = document.getElementById('invoice-select');
  try {
    const res = await fetch(INVOICES_ENDPOINT, {
      headers: { 'X-Session-Id': SESSION_ID },
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new HttpFailure(res.status, err.message,
        Number(res.headers.get('Retry-After')) || 0);
    }
    const body = await res.json();
    const invoices = body.data?.content ?? body.data ?? [];

    if (!invoices.length) {
      sel.innerHTML = '';
      showSidebarEmpty(true);
      showEmptyState('empty');
      return;
    }

    fillSelector(sel, invoices);
    showSidebarEmpty(false);

    // Mid-upload the caller selects the new invoice itself — don't flash the hero.
    if (!state.selectedInvoiceId && !state.isUploading) showEmptyState('unselected');
  } catch (err) {
    sel.hidden = false;
    sel.innerHTML = '<option value="">Error al cargar facturas</option>';
    // A network failure carries no status; describeFailure then falls back to its generic copy.
    showBanner(describeFailure(err instanceof HttpFailure ? err : {}).text, 'error');
  }
}

/** Carries the status through the throw so the catch can describe it instead of printing it. */
class HttpFailure extends Error {
  constructor(status, message, retryAfter) {
    super(message ?? `HTTP ${status}`);
    this.status     = status;
    this.retryAfter = retryAfter;
  }
}

function fillSelector(sel, invoices) {
  sel.innerHTML = '<option value="">Selecciona una factura…</option>';
  invoices.forEach(inv => {
    const opt = document.createElement('option');
    opt.value = inv.id;
    opt.textContent = `${fmtSupply(inv.supplyType)} · ${inv.provider ?? 'Desconocida'} · ${fmtDate(inv.billingPeriodStart)}`;
    sel.appendChild(opt);
  });
  sel.disabled = false;
}

export async function updateInvoiceCard(invoiceId) {
  const card = document.getElementById('invoice-card');
  if (!invoiceId) { card.classList.remove('visible'); return; }

  try {
    const res = await fetch(`${INVOICES_ENDPOINT}/${invoiceId}`, {
      headers: { 'X-Session-Id': SESSION_ID },
    });
    if (!res.ok) throw new Error();
    const { data: inv } = await res.json();

    document.getElementById('ic-type').textContent    = inv.supplyType ? fmtSupply(inv.supplyType) : '—';
    document.getElementById('ic-company').textContent = inv.provider   ?? '—';
    const from = fmtDate(inv.billingPeriodStart);
    const to   = fmtDate(inv.billingPeriodEnd);
    document.getElementById('ic-period').textContent  = from && to ? `${from} – ${to}` : (from || '—');
    document.getElementById('ic-total').textContent   = inv.totalAmount != null ? fmtEuros(inv.totalAmount) : '—';
    card.classList.add('visible');
  } catch {
    card.classList.remove('visible');
  }
}