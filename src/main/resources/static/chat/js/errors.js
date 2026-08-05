/**
 * The single point where a failed request becomes something a person can read.
 *
 * Every caller — upload, chat, SSE error events — goes through describeFailure(), so no code path
 * can leak "HTTP 500" into the interface. The backend already sends Spanish messages; what it
 * cannot know is which of them are worth showing (a rate limit is actionable, an internal error is
 * not) and how to phrase the wait, so that decision lives here.
 */

const COPY = {
  unavailable: 'Mi motor de IA no responde ahora mismo. No es cosa tuya — vuelve a probar en unos minutos.',
  internal:    'Se me ha cruzado un cable por dentro. Vuelve a intentarlo en un momento.',
  generic:     'No he podido completar la operación. Vuelve a intentarlo en un momento.',
};

/**
 * Seconds → the same coarse Spanish the backend uses. Returns null when the wait is unknown,
 * which is not the same as zero: callers must not lock the UI on a guess.
 */
export function humanizeWait(seconds) {
  const total = Number(seconds);
  if (!Number.isFinite(total) || total <= 0) return null;
  if (total <= 60) return 'un minuto';
  const minutes = Math.ceil(total / 60);
  if (minutes < 60) return `${minutes} minutos`;
  const hours = Math.ceil(minutes / 60);
  return hours === 1 ? 'una hora' : `${hours} horas`;
}

/**
 * A live countdown for the banner: 2:05, 0:47 — and 1:12:30 once the wait passes an hour, which a
 * provider's daily quota does (minutes alone would render an eleven-hour wait as "660:00").
 */
export function formatClock(seconds) {
  const total   = Math.max(0, Math.ceil(seconds));
  const hours   = Math.floor(total / 3600);
  const minutes = Math.floor(total / 60) % 60;
  const secs    = String(total % 60).padStart(2, '0');
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, '0')}:${secs}`
    : `${minutes}:${secs}`;
}

/**
 * @param  {{status?: number, code?: string, message?: string, retryAfter?: number}} failure
 * @return {{kind: string, text: string, retryAfter: number}} retryAfter of 0 means "unknown".
 */
export function describeFailure({ status, code, message, retryAfter } = {}) {
  const wait = Number(retryAfter) > 0 ? Math.ceil(Number(retryAfter)) : 0;

  if (code === 'RATE_LIMITED' || status === 429) {
    return { kind: 'throttled', text: throttledCopy(wait), retryAfter: wait };
  }
  if (code === 'LLM_UNAVAILABLE' || status === 503) {
    return { kind: 'unavailable', text: COPY.unavailable, retryAfter: wait };
  }
  if (code === 'INTERNAL' || (status >= 500 && status < 600)) {
    return { kind: 'internal', text: COPY.internal, retryAfter: 0 };
  }
  // 4xx: the backend knows exactly what is wrong with the request and says so in Spanish.
  return { kind: 'client', text: message || COPY.generic, retryAfter: wait };
}

function throttledCopy(wait) {
  const human = humanizeWait(wait);
  return human
    ? `Te has puesto a tope: has gastado todas tus consultas de este rato. Recargo en ${human} y seguimos.`
    : 'Te has puesto a tope: has gastado todas tus consultas de este rato. Dame unos minutos y seguimos.';
}