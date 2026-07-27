// SupplyDomain (backend enum) → what a human calls it
const SUPPLY_LABELS = {
  ELECTRICITY: 'Electricidad',
  GAS:         'Gas',
  WATER:       'Agua',
  TELECOM:     'Telefonía',
  OTHER:       'Otro',
};

export function fmtSupply(type) {
  return SUPPLY_LABELS[type] ?? 'Suministro';
}

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

// €/kWh: 2 decimals read as noise-free but collapse tariffs (0,11 vs 0,11),
// so 3 is the ceiling — enough to tell rates apart, short enough to scan.
export function fmtPrice(val) {
  if (val == null) return '—';
  return Number(val).toLocaleString('es-ES', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 3,
  });
}

export function fmtKwh(val) {
  if (val == null) return '—';
  return Number(val).toLocaleString('es-ES', { maximumFractionDigits: 0 });
}

export function autoResize(el) {
  el.style.height = 'auto';
  el.style.height = Math.min(el.scrollHeight, 120) + 'px';
}