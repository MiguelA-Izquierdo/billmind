import { SESSION_ID } from './session.js';
import { fmtDate, fmtEuros } from './utils.js';
import { showBanner } from './messages.js';

const INVOICES_ENDPOINT = '/api/v1/invoices';

export async function loadInvoices() {
  const sel = document.getElementById('invoice-select');
  try {
    const res = await fetch(INVOICES_ENDPOINT, {
      headers: { 'X-Session-Id': SESSION_ID },
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const body = await res.json();
    const invoices = body.data?.content ?? body.data ?? [];

    const uploadZone = document.getElementById('upload-zone');
    const uploadMini = document.getElementById('upload-mini');

    sel.innerHTML = '';
    if (!invoices.length) {
      sel.innerHTML = '<option value="">Sin facturas — sube una primero</option>';
      uploadZone.style.display = 'block';
      uploadMini.style.display = 'none';
      return;
    }

    uploadZone.style.display = 'none';
    uploadMini.style.display = 'block';
    sel.innerHTML = '<option value="">Selecciona una factura…</option>';
    invoices.forEach(inv => {
      const opt = document.createElement('option');
      opt.value = inv.id;
      opt.textContent = `${inv.supplyType ?? '?'} · ${inv.provider ?? 'Desconocida'} · ${fmtDate(inv.billingPeriodStart)}`;
      sel.appendChild(opt);
    });
    sel.disabled = false;
  } catch (err) {
    sel.innerHTML = '<option value="">Error al cargar facturas</option>';
    showBanner('No se pudieron cargar las facturas: ' + err.message, 'error');
  }
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

    document.getElementById('ic-type').textContent    = inv.supplyType ?? '—';
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