import { escHtml, fmtTime, fmtPrice, fmtKwh } from './utils.js';

let msgCounter     = 0;
let thinkingCounter = 0;

function messagesEl() {
  return document.getElementById('messages');
}

export function scrollBottom() {
  const el = messagesEl();
  if (el) el.scrollTop = el.scrollHeight;
}

export function showBanner(msg, type) {
  const el = document.getElementById('error-banner');
  if (el) el.innerHTML = `<div class="banner ${type}">${escHtml(msg)}</div>`;
}

export function hideBanner() {
  const el = document.getElementById('error-banner');
  if (el) el.innerHTML = '';
}

export function appendUserMessage(text, initials) {
  const el = document.createElement('div');
  el.className = 'msg user';
  el.innerHTML = `
    <div class="msg-avatar">${escHtml(initials)}</div>
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

export function appendToken(msgId, token) {
  const bubble = document.getElementById(msgId + '-bubble');
  if (!bubble) return;
  bubble.querySelector('.cursor')?.remove();
  bubble.innerHTML += escHtml(token).replace(/\n/g, '<br>');
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
  document.getElementById('welcome').style.display = 'none';

  const id = 'msg-' + (++msgCounter);
  const el = document.createElement('div');
  el.className = 'msg assistant cmp-msg';
  el.id = id;

  let inner = `<div class="cmp-intro">He analizado <strong>${escHtml(data.fileName ?? 'tu factura')}</strong>. Esto es lo que encontré:</div>`;

  if (c) {
    const userIsTou = c.userIsTou;
    const flat      = c.flatBlock;
    const tou       = c.touBlock;

    if (flat) {
      const savings  = Number(flat.annualSavingsEuros);
      const positive = savings >= 0;
      inner += `
        <div class="cmp-main">
          <div class="cmp-summary">
            <div class="cmp-savings-label">${positive ? 'Ahorro potencial' : 'Coste adicional'}</div>
            <div class="cmp-amount ${positive ? 'positive' : 'negative'}" id="${id}-counter">0,00 €</div>
            <div class="cmp-year">/ año sin cambios</div>
            <div class="cmp-with">con <strong>${escHtml(flat.bestTariffName)}</strong> de ${escHtml(flat.bestCompany)}</div>
          </div>
          <div class="cmp-grid">
            <div class="cmp-item">
              <span class="cmp-val yours">${fmtPrice(c.userPricePerKwh)}</span>
              <span class="cmp-key">Tu precio${userIsTou ? ' medio' : ''}</span>
            </div>
            <div class="cmp-item">
              <span class="cmp-val best">${fmtPrice(flat.bestPricePerKwh)}</span>
              <span class="cmp-key">Mejor precio</span>
            </div>
            <div class="cmp-item">
              <span class="cmp-val">${fmtKwh(c.annualKwhEstimate)}</span>
              <span class="cmp-key">kWh / año</span>
            </div>
          </div>
        </div>`;
      if (flat.alternatives?.length) {
        inner += `<div class="cmp-alts-title">Otras opciones</div>`;
        inner += flat.alternatives.map(a => `
          <div class="cmp-alt-row">
            <div>
              <div>${escHtml(a.tariffName)}</div>
              <div class="cmp-alt-company">${escHtml(a.company)}</div>
            </div>
            <span class="cmp-alt-price">${fmtPrice(a.effectivePricePerKwh)} €/kWh</span>
          </div>`).join('');
      }
    }

    if (tou) {
      const touSavings  = Number(tou.annualSavingsEuros);
      const touPositive = touSavings >= 0;
      inner += `
        <div class="cmp-tou-section">
          <div class="cmp-tou-header">Adaptando tu consumo a horas valle</div>
          <div class="cmp-main">
            <div class="cmp-summary">
              <div class="cmp-savings-label">${touPositive ? 'Ahorro potencial' : 'Coste adicional'}</div>
              <div class="cmp-amount ${touPositive ? 'positive' : 'negative'}" id="${id}-counter-tou">0,00 €</div>
              <div class="cmp-year">/ año</div>
              <div class="cmp-with">
                con <strong>${escHtml(tou.bestTariffName)}</strong> de ${escHtml(tou.bestCompany)}
                <span class="tou-badge">Discriminada</span>
              </div>
            </div>
            <div class="cmp-grid">
              <div class="cmp-item">
                <span class="cmp-val best">${fmtPrice(tou.bestPricePerKwh)}</span>
                <span class="cmp-key">Precio medio</span>
              </div>
              <div class="cmp-item">
                <span class="cmp-val">${fmtKwh(c.annualKwhEstimate)}</span>
                <span class="cmp-key">kWh / año</span>
              </div>
            </div>
          </div>`;
      if (tou.alternatives?.length) {
        inner += `<div class="cmp-alts-title">Otras opciones disc.</div>`;
        inner += tou.alternatives.map(a => `
          <div class="cmp-alt-row">
            <div>
              <div>${escHtml(a.tariffName)}</div>
              <div class="cmp-alt-company">${escHtml(a.company)}</div>
            </div>
            <span class="cmp-alt-price">${fmtPrice(a.effectivePricePerKwh)} €/kWh · disc.</span>
          </div>`).join('');
      }
      inner += `<div class="cmp-tou-disclaimer">* Precio efectivo medio. El ahorro real depende de trasladar consumos a horas valle (23h–8h).</div>
        </div>`;
    }
  } else {
    inner += `<div style="color:var(--muted);font-size:13px">Factura procesada, pero no hay datos de mercado suficientes para calcular el ahorro.</div>`;
  }

  inner += `<div class="cmp-footer">Puedes preguntarme cualquier cosa sobre esta factura.</div>`;

  el.innerHTML = `
    <div class="msg-avatar">⚡</div>
    <div class="msg-content">
      <div class="msg-bubble cmp-bubble">${inner}</div>
      <span class="msg-meta">${fmtTime(new Date())}</span>
    </div>`;

  messagesEl().appendChild(el);
  el.scrollIntoView({ block: 'start', behavior: 'smooth' });

  if (c?.flatBlock) animateCounter(c.flatBlock.annualSavingsEuros, id + '-counter');
  if (c?.touBlock)  animateCounter(c.touBlock.annualSavingsEuros,  id + '-counter-tou');
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