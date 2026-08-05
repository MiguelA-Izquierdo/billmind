export const state = {
  selectedInvoiceId: null,
  conversationId: null,
  isStreaming: false,
  isUploading: false,
  // Epoch ms until which we have been told to wait (429). 0 when nothing is throttled.
  cooldownUntil: 0,
};