const STAGES = {
  upload: [
    { pct: 10, label: 'Leyendo el PDF…',                           delay: 0 },
    { pct: 30, label: 'Identificando tipo de factura…',            delay: 2500 },
    { pct: 60, label: 'Extrayendo campos con IA…',                 delay: 5500 },
    { pct: 80, label: 'Calculando tu ahorro potencial…',           delay: 9000 },
    { pct: 90, label: 'Buscando las mejores tarifas del mercado…', delay: 12500 },
  ],
  comparison: [
    { pct: 20, label: 'Cargando factura…',                         delay: 0 },
    { pct: 65, label: 'Calculando tu ahorro potencial…',           delay: 800 },
    { pct: 90, label: 'Buscando las mejores tarifas del mercado…', delay: 1800 },
  ],
};

let _timers = [];

export function showOverlay(mode = 'upload') {
  const stages = STAGES[mode] ?? STAGES.upload;
  document.getElementById('upload-overlay').classList.add('active');
  setProgress(0);
  setMessage(stages[0].label);

  stages.slice(1).forEach(stage => {
    const t = setTimeout(() => {
      setProgress(stage.pct);
      setMessage(stage.label);
    }, stage.delay);
    _timers.push(t);
  });
}

export function hideOverlay(success = true) {
  _timers.forEach(clearTimeout);
  _timers = [];

  if (success) {
    setProgress(100);
    setTimeout(() => {
      document.getElementById('upload-overlay').classList.remove('active');
      setTimeout(() => setProgress(0), 300);
    }, 500);
  } else {
    document.getElementById('upload-overlay').classList.remove('active');
    setTimeout(() => setProgress(0), 300);
  }
}

function setProgress(pct) {
  const el = document.getElementById('overlay-progress');
  if (el) el.style.width = pct + '%';
}

function setMessage(msg) {
  const el = document.getElementById('overlay-message');
  if (!el) return;
  el.style.opacity = '0';
  setTimeout(() => {
    el.textContent = msg;
    el.style.opacity = '1';
  }, 200);
}