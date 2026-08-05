/**
 * The waiting room. When we are told to slow down — by our own limiter or by the model provider —
 * the controls that would fail again are locked for exactly as long as the wait lasts, and a banner
 * counts it down. Letting the user press send into a wall would just produce a second 429.
 *
 * The deadline lives in `state.cooldownUntil` so syncChat() can read it without importing this
 * module back (ui.js is the single source of truth for what is enabled; this only sets the clock).
 */

import { state } from './state.js';
import { syncChat } from './ui.js';
import { showBanner, hideBanner } from './messages.js';
import { formatClock } from './errors.js';

let ticker = null;
let copy   = '';

/**
 * @param {string} text     what to show above the countdown
 * @param {number} seconds  0 or undefined when the provider named no wait — then we say so but
 *                          lock nothing, since guessing a deadline is worse than none.
 */
export function startCooldown(text, seconds) {
  copy = text;
  if (!seconds || seconds <= 0) {
    showBanner(text, 'warn');
    return;
  }
  // Never shorten a wait already running: the strictest limit is the one that matters.
  state.cooldownUntil = Math.max(state.cooldownUntil, Date.now() + seconds * 1000);
  clearInterval(ticker);
  tick();
  ticker = setInterval(tick, 1000);
  syncChat();
}

export function cooldownSecondsLeft() {
  return Math.max(0, Math.ceil((state.cooldownUntil - Date.now()) / 1000));
}

function tick() {
  const left = cooldownSecondsLeft();
  if (left <= 0) {
    stop();
    return;
  }
  showBanner(`${copy} (${formatClock(left)})`, 'warn');
}

function stop() {
  clearInterval(ticker);
  ticker = null;
  state.cooldownUntil = 0;
  hideBanner();
  syncChat();
}