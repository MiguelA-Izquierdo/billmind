import { SESSION_ID } from './session.js';
import { state } from './state.js';
import { autoResize } from './utils.js';
import {
  appendUserMessage, appendAssistantMessage,
  appendMessage, finishStreaming, renderCitations,
  appendThinking, removeThinking, removeMessage,
  showBanner, hideBanner,
} from './messages.js';
import { syncChat, announce } from './ui.js';
import { showBotToast } from './char-limit.js';
import { describeFailure } from './errors.js';
import { startCooldown } from './cooldown.js';

const CHAT_ENDPOINT = '/api/v1/assistant/chat';

export async function sendMessage() {
  const input = document.getElementById('chat-input');
  const text  = input.value.trim();
  if (!text || !state.selectedInvoiceId || state.isStreaming || state.isUploading) return;

  input.value = '';
  autoResize(input);
  appendUserMessage(text);
  hideBanner();

  const thinkingId = appendThinking();
  state.isStreaming = true;
  syncChat();
  announce('El asistente está preparando la respuesta.');

  try {
    const res = await fetch(CHAT_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Session-Id': SESSION_ID,
        'Accept':       'text/event-stream',
      },
      body: JSON.stringify({ invoiceId: state.selectedInvoiceId, conversationId: state.conversationId, message: text }),
    });

    removeThinking(thinkingId);

    // The stream never opened: this is a plain JSON error, rate limiter included.
    if (!res.ok) {
      if (res.status === 404) {
        appendAssistantMessage('El asistente aún no está disponible en esta versión.');
        return;
      }
      const err = await res.json().catch(() => ({}));
      reportFailure({
        status:     res.status,
        message:    err.message,
        retryAfter: Number(res.headers.get('Retry-After')) || 0,
      });
      return;
    }

    const msgId   = appendAssistantMessage('');
    const reader  = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer    = '';
    let citations = [];
    let failure   = null;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop();

      for (const line of lines) {
        if (!line.startsWith('data:')) continue;
        const data = line.slice(5).trim();
        if (data === '[DONE]') break;
        try {
          const chunk = JSON.parse(data);
          if (chunk.type === 'conversation') state.conversationId = chunk.id;
          if (chunk.type === 'message')      appendMessage(msgId, chunk.content);
          if (chunk.type === 'citation')     citations.push(chunk);
          if (chunk.type === 'citations')    { citations = chunk.items ?? []; renderCitations(msgId, citations); }
          // The status was committed when the stream opened, so failures arrive as events.
          if (chunk.type === 'error')        failure = { code: chunk.code, message: chunk.content, retryAfter: chunk.retryAfter };
        } catch {
          appendMessage(msgId, data);
        }
      }
    }

    if (failure) {
      removeMessage(msgId); // an answer that failed left an empty bubble behind
      reportFailure(failure);
      return;
    }
    finishStreaming(msgId);
    announce('Respuesta completada.');

  } catch (err) {
    removeThinking(thinkingId);
    showBanner('No he podido conectar con el servidor. Revisa tu conexión y vuelve a intentarlo.', 'error');
  } finally {
    state.isStreaming = false;
    syncChat();
    document.getElementById('chat-input').focus();
  }
}

/**
 * Every chat failure ends here, wherever it came from — HTTP status or SSE event. A throttle gets
 * the bot's voice plus a countdown that locks the input; anything else is a banner. No branch of
 * this function can print a status code.
 */
function reportFailure(raw) {
  const failure = describeFailure(raw);

  if (failure.kind === 'throttled') {
    showBotToast(failure.text);
    startCooldown('Estoy recargando.', failure.retryAfter);
    announce(failure.text);
    return;
  }
  showBanner(failure.text, 'error');
  announce(failure.text);
}

export async function checkBackendStatus() {
  const dot  = document.getElementById('kb-dot');
  const text = document.getElementById('kb-text');
  // Actuator lives on a separate, internal-only management port and is not
  // reachable from the browser by design. Derive status from the chat endpoint
  // itself: a network failure means the service is down, a 404 means the
  // assistant route is not ready yet, anything else means it is available.
  try {
    const probe = await fetch(CHAT_ENDPOINT, { method: 'OPTIONS', headers: { 'X-Session-Id': SESSION_ID } });
    const ready = probe.status !== 404;

    if (!ready) {
      dot.className    = 'kb-dot pending';
      text.textContent = 'Asistente pendiente';
    } else {
      dot.className    = 'kb-dot ready';
      text.textContent = 'Asistente disponible';
    }
  } catch {
    dot.className    = 'kb-dot offline';
    text.textContent = 'Servicio no disponible';
  }
}