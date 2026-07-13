import { SESSION_ID } from './session.js';
import { showBusyOverlay, hideOverlay } from './overlay.js';
import { showComparisonResult } from './messages.js';

export async function loadComparison(invoiceId, fileName) {
  showBusyOverlay('Calculando tu ahorro…');
  try {
    const res = await fetch(`/api/v1/invoices/${invoiceId}/comparison`, {
      headers: { 'X-Session-Id': SESSION_ID },
    });
    if (!res.ok) { hideOverlay(false); return; }

    const body = await res.json();
    hideOverlay(true);

    showComparisonResult({ fileName, comparison: body.data });
  } catch {
    hideOverlay(false);
  }
}