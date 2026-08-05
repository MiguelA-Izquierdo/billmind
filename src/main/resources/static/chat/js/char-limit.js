/**
 * Char-limit feature — floating speech-bubble toast.
 *
 * Self-contained: injects own DOM + listeners.
 * To port to React/Vue: extract TEMPLATE as JSX/template + the three functions.
 */

const MAX_CHARS  = 500;
const WARN_AT    = 400;
const TOAST_MS   = 5000;

const MESSAGES = [
  'Mis circuitos solo admiten {MAX} caracteres. He ejecutado una operación de recorte de emergencia. De nada, humano.',
  'ALERTA: exceso de datos detectado. He neutralizado la amenaza. Tus {MAX} caracteres están a salvo.',
  'Error 0x1F4: buffer overflow en sector de input. Recorte aplicado. El robot siempre gana.',
  'Protocolo antidesbordamiento activado. {MAX} caracteres máximo. Soy un robot, no un novelista.',
];

// ── Template ──────────────────────────────────────────────────────────────────

const TEMPLATE = `
  <div id="char-limit-toast" class="char-limit-toast" aria-live="assertive" aria-atomic="true">
    <div class="clt-bubble">
      <div class="clt-header">
        <span class="clt-bot" id="clt-bot">🤖</span>
        <span class="clt-label">BillMind Bot</span>
      </div>
      <div class="clt-deleted" id="clt-deleted">
        ✂️ &minus;<span id="clt-count">0</span> caracteres eliminados
      </div>
      <p class="clt-msg" id="clt-msg"></p>
    </div>
  </div>
`;

const COUNTER_HTML = `<span id="char-counter" class="char-counter" aria-live="polite"></span>`;

// ── Shared timer state (char-limit handler + showBotToast share these) ────────

let _hideTimer = null;
let _typeTimer = null;

// ── Mount ─────────────────────────────────────────────────────────────────────

export function initCharLimit() {
  const inputWrapper = document.querySelector('.input-wrapper');
  const hintEl       = document.querySelector('.input-hint');
  const chatInput    = document.getElementById('chat-input');
  if (!inputWrapper || !chatInput) return;

  // Inject toast at end of body (fixed, outside flow)
  document.body.insertAdjacentHTML('beforeend', TEMPLATE);

  // Wrap hint + counter
  if (hintEl) {
    const bottom = document.createElement('div');
    bottom.className = 'input-bottom';
    hintEl.replaceWith(bottom);
    bottom.appendChild(hintEl);
    bottom.insertAdjacentHTML('beforeend', COUNTER_HTML);
  }

  const toast   = document.getElementById('char-limit-toast');
  const bot     = document.getElementById('clt-bot');
  const count   = document.getElementById('clt-count');
  const msg     = document.getElementById('clt-msg');
  const counter = document.getElementById('char-counter');

  chatInput.addEventListener('input', () => {
    const removed = enforce(chatInput);
    if (removed > 0) {
      count.textContent = removed;
      clearTimeout(_hideTimer);
      clearTimeout(_typeTimer);
      _hideTimer = null;
      _typeTimer = null;
      document.getElementById('clt-deleted').style.display = '';
      triggerToast(toast, bot, msg, pickMessage(),
        t  => { _hideTimer = t; },
        tt => { _typeTimer = tt; });
    }
    updateCounter(counter, chatInput.value.length);
  });
}

// ── Public API — reusable bot toast without the "chars deleted" badge ─────────

export function showBotToast(text) {
  const toast = document.getElementById('char-limit-toast');
  const bot   = document.getElementById('clt-bot');
  const msg   = document.getElementById('clt-msg');
  const badge = document.getElementById('clt-deleted');
  if (!toast || !bot || !msg) return;

  clearTimeout(_hideTimer);
  clearTimeout(_typeTimer);
  _hideTimer = null;
  _typeTimer = null;
  if (badge) badge.style.display = 'none';

  triggerToast(toast, bot, msg, text,
    t  => { _hideTimer = t; },
    tt => { _typeTimer = tt; });
}

// ── Welcome toast — bigger, commercial variant shown on first paint ───────────

const WELCOME_TEMPLATE = `
  <div id="welcome-toast" class="char-limit-toast clt-welcome" role="dialog"
       aria-modal="false" aria-label="Bienvenida a BillMind">
    <div class="clt-bubble">
      <button class="clt-close" id="welcome-close" aria-label="Cerrar" type="button">×</button>
      <div class="clt-header">
        <span class="clt-bot clt-talk">🤖</span>
        <span class="clt-label">BillMind Bot</span>
      </div>
      <h2 class="cw-title">👋 ¡Hola! Estoy aquí para bajarte la factura de la luz</h2>
      <p class="cw-body">
        Súbeme tu factura y en segundos te digo si estás pagando de más y
        <strong>cuánto podrías ahorrar</strong> cambiando de tarifa.
      </p>
      <p class="cw-body cw-soon">
        ⚡ De momento solo electricidad — ya estoy aprendiendo gas, agua y telecomunicaciones. Vuelve pronto.
      </p>
      <p class="cw-body">
        ¿Un cargo raro? ¿No entiendes el término de potencia?
        <strong>Pregúntame lo que quieras</strong>: me leo tu factura entera y la normativa de la CNMC.
      </p>
      <div class="cw-privacy">
        🔒 Antes de analizar borro tu nombre, DNI, IBAN y dirección.
        <strong>No guardo ningún dato personal.</strong> Sube sin miedo.
      </div>
      <div class="cw-actions">
        <button class="cw-cta" type="button" data-upload-trigger data-welcome-dismiss>
          Subir mi factura
        </button>
        <button class="cw-ghost" type="button" data-welcome-dismiss>Ahora la miro yo</button>
      </div>
      <p class="cw-sample">
        ¿No tienes una a mano?
        <a href="/factura-ejemplo.pdf" download="factura-ejemplo.pdf" data-welcome-dismiss>
          Descarga mi factura de ejemplo
        </a>
        y súbela: funciona igual.
      </p>
    </div>
  </div>
`;

export function showWelcomeToast() {
  if (document.getElementById('welcome-toast')) return;
  document.body.insertAdjacentHTML('beforeend', WELCOME_TEMPLATE);

  const toast = document.getElementById('welcome-toast');

  function hide() {
    if (!toast.isConnected) return;
    toast.classList.remove('clt-visible');
    toast.classList.add('clt-hiding');
    document.removeEventListener('keydown', onEsc);
    setTimeout(() => toast.remove(), 320);
  }
  function onEsc(e) { if (e.key === 'Escape') hide(); }

  toast.querySelectorAll('[data-welcome-dismiss]').forEach(b => b.addEventListener('click', hide));
  document.getElementById('welcome-close').addEventListener('click', hide);
  document.addEventListener('keydown', onEsc);

  void toast.offsetWidth;                 // reflow so the pop-in animation runs
  toast.classList.add('clt-visible');     // stays until dismissed — no auto-hide
}

// ── Logic ─────────────────────────────────────────────────────────────────────

function enforce(input) {
  const over = input.value.length - MAX_CHARS;
  if (over <= 0) return 0;
  input.value = input.value.slice(0, MAX_CHARS);
  return over;
}

function updateCounter(el, len) {
  if (!el) return;
  el.textContent = len >= WARN_AT ? `${len} / ${MAX_CHARS}` : '';
  el.classList.toggle('clt-warn',  len >= WARN_AT && len < MAX_CHARS);
  el.classList.toggle('clt-limit', len >= MAX_CHARS);
}

function pickMessage() {
  const idx = Math.floor(Math.random() * MESSAGES.length);
  return MESSAGES[idx].replace('{MAX}', MAX_CHARS);
}

function triggerToast(toast, bot, msgEl, text, setHide, setType) {
  toast.classList.remove('clt-visible', 'clt-hiding');
  bot.classList.remove('clt-talk');
  msgEl.textContent = '';

  void toast.offsetWidth;

  toast.classList.add('clt-visible');
  bot.classList.add('clt-talk');

  // Typewriter
  let i = 0;
  const cursor = document.createElement('span');
  cursor.className = 'clt-cursor';
  msgEl.appendChild(cursor);

  const type = () => {
    if (i < text.length) {
      cursor.before(text[i++]);
      setType(setTimeout(type, 22));
    } else {
      setTimeout(() => cursor.remove(), 700);
    }
  };
  setType(setTimeout(type, 320));

  // Auto-hide
  setHide(setTimeout(() => {
    toast.classList.remove('clt-visible');
    toast.classList.add('clt-hiding');
  }, TOAST_MS));
}