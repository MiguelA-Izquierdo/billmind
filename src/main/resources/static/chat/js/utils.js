export function escHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g,  '&lt;')
    .replace(/>/g,  '&gt;')
    .replace(/"/g,  '&quot;');
}

export function fmtDate(d) {
  if (!d) return '';
  const [y, m, day] = String(d).split('-');
  return day ? `${day}/${m}/${y}` : d;
}

export function fmtTime(d) {
  return d.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' });
}

export function fmtEuros(v) {
  return Number(v).toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });
}

export function fmtPrice(val) {
  if (val == null) return '—';
  return Number(val).toFixed(6).replace(/0+$/, '').replace(/\.$/, '').replace('.', ',');
}

export function fmtKwh(val) {
  if (val == null) return '—';
  return Number(val).toLocaleString('es-ES', { maximumFractionDigits: 0 });
}

export function autoResize(el) {
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 120) + 'px';
}