import { SESSION_ID } from './session.js';
import { state } from './state.js';
import { autoResize } from './utils.js';
import {
  appendUserMessage, appendAssistantMessage,
  appendMessage, finishStreaming, renderCitations,
  appendThinking, removeThinking,
  showBanner, hideBanner,
} from './messages.js';
import { syncChat, announce } from './ui.js';

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

    if (!res.ok) {
      if (res.status === 404) {
        appendAssistantMessage('El asistente aún no está disponible en esta versión.');
      } else {
        const err = await res.json().catch(() => ({}));
        showBanner(err.message ?? 'Error al contactar el asistente (HTTP ' + res.status + ')', 'error');
      }
      return;
    }

    const msgId   = appendAssistantMessage('');
    const reader  = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer    = '';
    let citations = [];

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
          if (chunk.type === 'error')        appendMessage(msgId, chunk.content);
        } catch {
          appendMessage(msgId, data);
        }
      }
    }
    finishStreaming(msgId);
    announce('Respuesta completada.');

  } catch (err) {
    removeThinking(thinkingId);
    showBanner('Error de red: ' + err.message, 'error');
  } finally {
    state.isStreaming = false;
    syncChat();
    document.getElementById('chat-input').focus();
  }
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