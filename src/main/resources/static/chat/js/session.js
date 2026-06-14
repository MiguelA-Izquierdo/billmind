function initSession() {
  let id = localStorage.getItem('billmind-session-id');
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem('billmind-session-id', id);
  }
  return id;
}

export const SESSION_ID = initSession();