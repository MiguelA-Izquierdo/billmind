import { escHtml, fmtTime, fmtPrice, fmtKwh } from './utils.js';
import { hideEmptyState } from './ui.js';

let msgCounter     = 0;
let thinkingCounter = 0;

function messagesEl() {
  return document.getElementById('messages');
}

export function scrollBottom() {
  const el = messagesEl();
  if (el) el.scrollTop = el.scrollHeight;
}

/**
 * Drops every message but keeps #empty-state, which lives inside #messages —
 * wiping innerHTML would destroy the hero dropzone for good.
 */
export function clearMessages() {
  messagesEl().querySelectorAll('.msg').forEach(el => el.remove());
}

export function showBanner(msg, type) {
  const el = document.getElementById('error-banner');
  if (el) el.innerHTML = `<div class="banner ${type}" role="alert">${escHtml(msg)}</div>`;
}

export function hideBanner() {
  const el = document.getElementById('error-banner');
  if (el) el.innerHTML = '';
}

export function appendUserMessage(text) {
  const el = document.createElement('div');
  el.className = 'msg user';
  el.innerHTML = `
    <div class="msg-avatar">👤</div>
    <div class="msg-content">
      <div class="msg-bubble">${escHtml(text).replace(/\n/g, '<br>')}</div>
      <span class="msg-meta">${fmtTime(new Date())}</span>
    </div>`;
  messagesEl().appendChild(el);
  scrollBottom();
}

export function appendAssistantMessage(text) {
  const id = 'msg-' + (++msgCounter);
  const el = document.createElement('div');
  el.className = 'msg assistant';
  el.id = id;
  el.innerHTML = `
    <div class="msg-avatar">⚡</div>
    <div class="msg-content">
      <div class="msg-bubble" id="${id}-bubble">${text ? escHtml(text) : '<span class="cursor"></span>'}</div>
      <div class="citations" id="${id}-citations"></div>
      <span class="msg-meta" id="${id}-meta">${fmtTime(new Date())}</span>
    </div>`;
  messagesEl().appendChild(el);
  scrollBottom();
  return id;
}

export function appendMessage(msgId, text) {
  const bubble = document.getElementById(msgId + '-bubble');
  if (!bubble) return;
  bubble.querySelector('.cursor')?.remove();
  bubble.innerHTML += escHtml(text).replace(/\n/g, '<br>');
  bubble.innerHTML += '<span class="cursor"></span>';
  scrollBottom();
}

export function finishStreaming(msgId) {
  document.getElementById(msgId + '-bubble')?.querySelector('.cursor')?.remove();
}

export function renderCitations(msgId, citations) {
  const el = document.getElementById(msgId + '-citations');
  if (!el || !citations.length) return;
  el.innerHTML = citations.map(c =>
    `<span class="citation-chip" title="${escHtml(c.source ?? '')}">${escHtml(c.title ?? c.source ?? 'Fuente')}</span>`
  ).join('');
}

export function appendThinking() {
  const id = 'thinking-' + (++thinkingCounter);
  const el = document.createElement('div');
  el.className = 'msg assistant';
  el.id = id;
  el.innerHTML = `
    <div class="msg-avatar">⚡</div>
    <div class="msg-content">
      <div class="msg-bubble">
        <div class="thinking">
          <div class="thinking-dots"><span></span><span></span><span></span></div>
          <span class="thinking-text">Analizando tu factura…</span>
        </div>
      </div>
    </div>`;
  messagesEl().appendChild(el);
  scrollBottom();
  return id;
}

export function removeThinking(id) {
  document.getElementById(id)?.remove();
}

/** Drops a bubble that never got its answer, so a failure leaves no empty shell behind. */
export function removeMessage(msgId) {
  document.getElementById(msgId)?.remove();
}

export function showComparisonResult(data) {
  if (!data) return;
  const c = data.comparison;
  hideEmptyState();

  const id = 'msg-' + (++msgCounter);
  const el = document.createElement('div');
  el.className = 'msg assistant cmp-msg';
  el.id = id;

  el.innerHTML = `
    <div class="msg-avatar">⚡</div>
    <div class="msg-content">
      <div class="msg-bubble cmp-bubble">${buildCard(id, data, c)}</div>
      <span class="msg-meta">${fmtTime(new Date())}</span>
    </div>`;

  messagesEl().appendChild(el);
  wireTabs(el, id, c);
  el.scrollIntoView({ block: 'start', behavior: 'smooth' });

  // Only the visible panel counts up; the other one animates when its tab is opened.
  if (touLeads(c))       animateCounter(c.touBlock,  c, id + '-counter-tou');
  else if (c?.flatBlock) animateCounter(c.flatBlock, c, id + '-counter');
}

/**
 * Which scenario opens the card. Flat leads by default because it asks for no change of habits —
 * except for a user already billed by periods, who lives under them already: for them the period
 * scenario is the one that matches their bill, so it is the one that should be on screen.
 */
function touLeads(c) {
  return !!c?.touBlock && (!c.flatBlock || !!c.userIsTou);
}

/**
 * The flat and time-of-use scenarios are the same shape, so stacking both in full
 * made the card taller than the viewport on every screen. They become tabs: one
 * scenario at a time, with each tab showing its own headline figure so nothing of
 * value is hidden behind a click.
 */
function buildCard(id, data, c) {
  const intro = `<div class="cmp-intro">He analizado <strong>${escHtml(data.fileName ?? 'tu factura')}</strong>. Esto es lo que encontré:</div>`;

  if (!c) {
    return intro
      + `<div class="cmp-empty">Factura procesada, pero no hay datos de mercado suficientes para calcular el ahorro.</div>`
      + `<div class="cmp-footer">Puedes preguntarme cualquier cosa sobre esta factura.</div>`;
  }

  const flat = c.flatBlock;
  const tou  = c.touBlock;
  const lead = touLeads(c);

  const tabs = (flat && tou)
    ? `<div class="cmp-tabs-hint">Tienes dos formas de ahorrar · toca para comparar</div>
       <div class="cmp-tabs" role="tablist">
         ${tabButton('flat', 'Tarifa plana', flat.periodSavingsEuros, !lead)}
         ${tabButton('tou',  'Horas valle',  tou.periodSavingsEuros,   lead)}
       </div>`
    : '';

  const panels =
      (flat ? buildPanel(id, c, flat, 'flat', !lead) : '')
    + (tou  ? buildPanel(id, c, tou,  'tou',   lead) : '');

  return intro + tabs + panels
    + `<div class="cmp-footer">Puedes preguntarme cualquier cosa sobre esta factura.</div>`;
}

function tabButton(kind, label, savings, active) {
  const amount = Number(savings);
  const sign   = amount >= 0 ? '' : '−';
  return `<button class="cmp-tab${active ? ' active' : ''}" role="tab"
            aria-selected="${active}" data-panel="${kind}">
            <span class="cmp-tab-label">${label}</span>
            <span class="cmp-tab-amount ${amount >= 0 ? 'positive' : 'negative'}">${sign}${fmtEuros(Math.abs(amount))}</span>
            <span class="cmp-tab-cue">${active ? 'Viendo' : 'Ver'}</span>
          </button>`;
}

function buildPanel(id, c, block, kind, visible) {
  const isTou     = kind === 'tou';
  const savings   = Number(block.annualSavingsMid);
  const positive  = savings >= 0;
  const counterId = isTou ? `${id}-counter-tou` : `${id}-counter`;

  // [class, value, unit, label] — a bare 0,218 is meaningless without its unit,
  // and only "kWh / año" carried one.
  const metrics = isTou
    ? [['best', fmtPrice(block.bestPricePerKwh), '€/kWh', 'Precio medio'],
       ['',     fmtKwh(c.annualKwhEstimate),     '',      'kWh / año']]
    : [['yours', fmtPrice(c.userPricePerKwh),     '€/kWh', `Tu precio${c.userIsTou ? ' medio' : ''}`],
       ['best',  fmtPrice(block.bestPricePerKwh), '€/kWh', 'Mejor precio'],
       ['',      fmtKwh(c.annualKwhEstimate),     '',      'kWh / año']];

  // The winning tariff gets its own full-width line: squeezed into the narrow
  // summary column it wrapped over three lines and pushed the badge loose.
  return `
    <div class="cmp-panel" data-panel="${kind}" ${visible ? '' : 'hidden'}>
      ${buildHeadline(c, block, counterId)}
      <div class="cmp-main">
        <div class="cmp-summary">
          <div class="cmp-savings-label">A un año</div>
          <div class="cmp-proj-amount ${positive ? 'positive' : 'negative'}">≈ ${fmtEurosShort(savings)}</div>
          <div class="cmp-year">si tu consumo se mantiene</div>
          <div class="cmp-range">entre ${fmtEurosShort(block.annualSavingsLow)} y ${fmtEurosShort(block.annualSavingsHigh)}</div>
        </div>
        <div class="cmp-grid">
          ${metrics.map(([cls, val, unit, key]) => `
            <div class="cmp-item">
              <span class="cmp-val ${cls}">${val}${unit ? `<span class="cmp-unit">${unit}</span>` : ''}</span>
              <span class="cmp-key">${key}</span>
            </div>`).join('')}
        </div>
      </div>
      <div class="cmp-best">con <strong>${escHtml(block.bestTariffName)}</strong> de ${escHtml(block.bestCompany)}</div>
      ${buildAlternatives(block, isTou)}
      ${basisNote(c, isTou)}
    </div>`;
}

/**
 * The figure the user can check against the paper in their hand: what this very invoice would
 * have cost on the winning tariff. It extrapolates nothing, so it leads the card and takes the
 * counter — which until now animated the *least* certain number on it. The annual projection
 * below inherits its credibility from this one.
 */
function buildHeadline(c, block, counterId) {
  const saving = Number(block.periodSavingsEuros);
  if (!Number.isFinite(saving) || saving === 0) return '';

  const total   = Number(c.invoiceTotalEuros);
  const hasBill = Number.isFinite(total) && total > 0;
  const cheaper = saving > 0;
  const days    = c.basis?.observedDays;

  // With the printed total we can show the bill itself shrinking; without it, only the delta.
  const figure = hasBill ? total - saving : Math.abs(saving);
  const aside  = hasBill
    ? `<span class="cmp-bill-was">en vez de ${fmtEuros(total)}</span>`
    : '';

  return `
    <div class="cmp-headline">
      <div class="cmp-savings-label">En esta factura ${cheaper ? 'habrías pagado' : 'pagarías'}</div>
      <div class="cmp-bill">
        <span class="cmp-amount ${cheaper ? 'positive' : 'negative'}" id="${counterId}">${fmtEuros(figure)}</span>
        ${aside}
      </div>
      <div class="cmp-year">
        ${cheaper ? '−' : '+'}${fmtEuros(Math.abs(saving))}${days ? ` · ${days} días facturados` : ''}
      </div>
    </div>`;
}

/**
 * The figure above is an extrapolation over a year the user has not lived yet. This is the one
 * place that says so, and it says it from the payload rather than from a fixed sentence — a card
 * that assumed a consumption profile and one that read it off the invoice do not deserve the
 * same caveat.
 */
function basisNote(c, isTou) {
  const b = c.basis;
  const parts = [];
  if (b) {
    parts.push(`* Estimación sobre ${b.observedDays} días facturados${b.annualised ? ', extrapolados a un año' : ''}.`);
    if (b.powerTerm === 'UNAVAILABLE') {
      parts.push('Solo compara el término de energía: el de potencia no se pudo leer de la factura.');
    } else if (b.powerTerm === 'DERIVED') {
      parts.push('El término de potencia está estimado a partir del total de la factura.');
    }
    if (b.consumptionProfile === 'ASSUMED') {
      parts.push('El reparto del consumo por periodos usa un perfil doméstico estándar.');
    }
  }
  // Only worth saying when the period split is a guess. A user whose invoice breaks consumption
  // down period by period is already living the split this figure was computed from — telling
  // them the saving depends on moving consumption would be describing someone else's bill.
  if (isTou && b?.consumptionProfile === 'ASSUMED') {
    parts.push('El ahorro real depende de trasladar consumos a horas valle (23h–8h).');
  }
  return parts.length ? `<div class="cmp-basis-note">${parts.join(' ')}</div>` : '';
}

/** Alternatives are reference material, not the headline — they fold away. */
function buildAlternatives(block, isTou) {
  const alts = block.alternatives ?? [];
  if (!alts.length) return '';

  const rows = alts.map(a => `
    <div class="cmp-alt-row">
      <div>
        <div>${escHtml(a.tariffName)}</div>
        <div class="cmp-alt-company">${escHtml(a.company)}</div>
      </div>
      <span class="cmp-alt-price">${fmtPrice(a.effectivePricePerKwh)} €/kWh${isTou ? ' · disc.' : ''}</span>
    </div>`).join('');

  return `<details class="cmp-alts">
            <summary>Ver ${alts.length} ${alts.length === 1 ? 'opción' : 'opciones'} más</summary>
            ${rows}
          </details>`;
}

function wireTabs(root, id, c) {
  const tabs = root.querySelectorAll('.cmp-tab');
  if (!tabs.length) return;

  // Either panel can be the one that opens the card now, so both are tracked: the leading one
  // already counted up on render, and re-running it on a click would reset a figure the user has
  // been looking at.
  const lead     = touLeads(c);
  const animated = { flat: !lead, tou: lead };

  tabs.forEach(tab => tab.addEventListener('click', () => {
    const target = tab.dataset.panel;

    tabs.forEach(t => {
      const on = t === tab;
      t.classList.toggle('active', on);
      t.setAttribute('aria-selected', String(on));
      t.querySelector('.cmp-tab-cue').textContent = on ? 'Viendo' : 'Ver';
    });
    root.querySelectorAll('.cmp-panel').forEach(p => {
      p.hidden = p.dataset.panel !== target;
    });

    // Run the count-up the first time the panel is actually seen
    if (!animated[target]) {
      animated[target] = true;
      animateCounter(target === 'tou' ? c.touBlock : c.flatBlock,
                     c, id + (target === 'tou' ? '-counter-tou' : '-counter'));
    }
  }));
}

function fmtEurosShort(v) {
  return Number(v).toLocaleString('es-ES', { maximumFractionDigits: 0 }) + ' €';
}

function fmtEuros(v) {
  return Number(v).toLocaleString('es-ES', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }) + ' €';
}

/**
 * Runs the bill down from what the user actually paid to what they would have paid. Cents are
 * warranted here and only here: this figure comes from the invoice's own consumption and days,
 * so it is the one number on the card that is not an estimate.
 */
function animateCounter(block, c, elId) {
  const el = document.getElementById(elId);
  if (!el) return;

  const saving  = Number(block.periodSavingsEuros);
  const total   = Number(c.invoiceTotalEuros);
  const hasBill = Number.isFinite(total) && total > 0;
  if (!Number.isFinite(saving) || saving === 0) return;

  const from = hasBill ? total : 0;
  const to   = hasBill ? total - saving : Math.abs(saving);
  const dur   = 1400;
  const start = performance.now();

  function step(now) {
    const t    = Math.min((now - start) / dur, 1);
    const ease = 1 - Math.pow(1 - t, 3);
    el.textContent = fmtEuros(from + (to - from) * ease);
    if (t < 1) requestAnimationFrame(step);
  }
  requestAnimationFrame(step);
}