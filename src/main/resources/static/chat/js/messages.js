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
  if (c?.flatBlock)     animateCounter(c.flatBlock.annualSavingsEuros, id + '-counter');
  else if (c?.touBlock) animateCounter(c.touBlock.annualSavingsEuros,  id + '-counter-tou');
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

  const tabs = (flat && tou)
    ? `<div class="cmp-tabs-hint">Tienes dos formas de ahorrar · toca para comparar</div>
       <div class="cmp-tabs" role="tablist">
         ${tabButton('flat', 'Tarifa plana', flat.annualSavingsEuros, true)}
         ${tabButton('tou',  'Horas valle',  tou.annualSavingsEuros,  false)}
       </div>`
    : '';

  // Flat is the default scenario — it needs no change of habits. TOU only leads
  // when there is no flat block to show.
  const panels =
      (flat ? buildPanel(id, c, flat, 'flat', true)  : '')
    + (tou  ? buildPanel(id, c, tou,  'tou',  !flat) : '');

  return intro + tabs + panels
    + `<div class="cmp-footer">Puedes preguntarme cualquier cosa sobre esta factura.</div>`;
}

function tabButton(kind, label, savings, active) {
  const amount = Number(savings);
  const sign   = amount >= 0 ? '' : '−';
  return `<button class="cmp-tab${active ? ' active' : ''}" role="tab"
            aria-selected="${active}" data-panel="${kind}">
            <span class="cmp-tab-label">${label}</span>
            <span class="cmp-tab-amount ${amount >= 0 ? 'positive' : 'negative'}">${sign}${fmtEurosShort(Math.abs(amount))}</span>
            <span class="cmp-tab-cue">${active ? 'Viendo' : 'Ver'}</span>
          </button>`;
}

function buildPanel(id, c, block, kind, visible) {
  const isTou     = kind === 'tou';
  const savings   = Number(block.annualSavingsEuros);
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
      <div class="cmp-main">
        <div class="cmp-summary">
          <div class="cmp-savings-label">${positive ? 'Ahorro potencial' : 'Coste adicional'}</div>
          <div class="cmp-amount ${positive ? 'positive' : 'negative'}" id="${counterId}">0,00 €</div>
          <div class="cmp-year">/ año${isTou ? '' : ' sin cambios'}</div>
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
      ${isTou ? '<div class="cmp-tou-disclaimer">* Precio efectivo medio. El ahorro real depende de trasladar consumos a horas valle (23h–8h).</div>' : ''}
    </div>`;
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

  let touAnimated = false;

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
    if (target === 'tou' && !touAnimated) {
      touAnimated = true;
      animateCounter(c.touBlock.annualSavingsEuros, id + '-counter-tou');
    }
  }));
}

function fmtEurosShort(v) {
  return Number(v).toLocaleString('es-ES', { maximumFractionDigits: 0 }) + ' €';
}

function animateCounter(target, elId) {
  const el  = document.getElementById(elId);
  if (!el) return;
  const end    = Math.abs(Number(target));
  const prefix = Number(target) >= 0 ? '' : '-';
  const dur    = 1400;
  const start  = performance.now();

  function step(now) {
    const t    = Math.min((now - start) / dur, 1);
    const ease = 1 - Math.pow(1 - t, 3);
    el.textContent = prefix + (end * ease).toLocaleString('es-ES', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }) + ' €';
    if (t < 1) requestAnimationFrame(step);
  }
  requestAnimationFrame(step);
}