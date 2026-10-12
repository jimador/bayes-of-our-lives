const form = document.querySelector('#guide-form');
const result = document.querySelector('#result');
const submitButton = document.querySelector('#submit');
const cancelButton = document.querySelector('#cancel');
const mealField = document.querySelector('#meal-field');
const minutes = document.querySelector('#minutes');
const minutesValue = document.querySelector('#minutes-value');
let pending = null;
let generation = 0;

function node(tag, className, value) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (value !== undefined) element.textContent = value;
  return element;
}

function show(element) {
  result.replaceChildren(element);
  result.hidden = false;
  result.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function busy(value) {
  submitButton.disabled = value;
  cancelButton.hidden = !value;
  submitButton.querySelector('span').textContent = value ? 'Finding a good stop…' : 'Find my next stop';
}

function renderMessage(kicker, title, body, isError = false) {
  const wrap = node('div', isError ? 'error' : '');
  wrap.append(node('span', 'result-kicker', kicker), node('h2', '', title), node('p', '', body));
  show(wrap);
}

function addMeta(container, label, value) {
  container.append(node('span', '', `${label}: ${value ?? 'unknown'}`));
}

function renderLead(lead, winner) {
  const card = node('article', `lead-card${winner ? ' winner' : ''}`);
  const head = node('div', 'lead-head');
  head.append(node('h3', '', lead.name), node('span', 'pill', winner ? 'Research lead' : lead.hardStatus));
  const meta = node('div', 'lead-meta');
  addMeta(meta, 'Drive each way', lead.oneWayBaseDriveMinutes == null ? 'unavailable' : `${lead.oneWayBaseDriveMinutes} min base`);
  addMeta(meta, 'Choice', lead.choice);
  addMeta(meta, 'Rating', lead.rating);
  addMeta(meta, 'Setting', lead.setting);
  card.append(head, meta);
  if (/^https:\/\/www\.openstreetmap\.org\//.test(lead.sourceUrl)) {
    const source = node('a', '', 'View mapped source ↗');
    source.href = lead.sourceUrl;
    source.target = '_blank';
    source.rel = 'noreferrer noopener';
    card.append(source);
  }
  const details = node('details', '');
  details.append(node('summary', '', 'See typed evidence and accepted input'));
  const evidence = node('div', 'lead-meta');
  addMeta(evidence, 'Interest match', lead.interestMatch);
  addMeta(evidence, 'Model', lead.model);
  addMeta(evidence, 'Decision call', `${Math.round(lead.decisionMs)} ms`);
  addMeta(evidence, 'Classification call', `${Math.round(lead.classificationMs)} ms`);
  details.append(evidence, node('p', '', `Input SHA-256: ${lead.inputSha256}`), node('pre', '', lead.decisionInput));
  card.append(details);
  return card;
}

function renderGuide(data) {
  const wrap = node('div', '');
  wrap.append(node('span', 'result-kicker', data.outcome === 'result' ? '02 / YOUR RESEARCH LEAD' : '02 / NO LEAD THIS TIME'));
  wrap.append(node('h2', '', data.selectedResearchLead || 'Keep exploring.'));
  wrap.append(node('p', '', data.outcome === 'result'
    ? `Bays found a provisional stop in ${data.city}. Check hours, access, food safety, and traffic before you go.`
    : `The ${data.city} lookup finished, but no mapped option passed the source, time, and typed decision checks. Try more time, another city, or another traveler.`));
  const summary = node('div', 'lead-meta');
  addMeta(summary, 'Lookup', 'Live public data');
  addMeta(summary, 'Engine', data.engine);
  addMeta(summary, 'Guide process', `${data.processMs} ms`);
  wrap.append(summary);
  const memory = node('details', 'memory-details');
  memory.append(node('summary', '', 'Which fictional Dice memory was accepted?'));
  memory.append(node('p', '', data.memoryProvenance));
  wrap.append(memory);
  if (data.leads.length) {
    const list = node('div', 'lead-list');
    for (const lead of data.leads) list.append(renderLead(lead, lead.name === data.selectedResearchLead));
    wrap.append(list);
  }
  show(wrap);
}

function updateIntent() {
  mealField.hidden = form.elements.intent.value !== 'meal';
}
function updateMinutes() {
  minutesValue.value = `${minutes.value} min`;
}
form.addEventListener('change', updateIntent);
minutes.addEventListener('input', updateMinutes);
updateIntent();
updateMinutes();

cancelButton.addEventListener('click', () => {
  generation++;
  pending?.abort();
  pending = null;
  busy(false);
  renderMessage('REQUEST CANCELLED', 'No rush.', 'Your last lookup was cancelled. Change the plan and try again.');
});

form.addEventListener('submit', async event => {
  event.preventDefault();
  const city = form.elements.city.value.trim();
  if (!city || city.length > 100) {
    renderMessage('CHECK THE FORM', 'A city, please.', 'Enter a real city of up to 100 characters.', true);
    form.elements.city.focus();
    return;
  }
  pending?.abort();
  const current = ++generation;
  const controller = new AbortController();
  pending = controller;
  busy(true);
  const loading = node('div', '');
  loading.append(node('span', 'result-kicker', 'LOOKING AROUND'));
  const loadingTitle = node('h2', '');
  loadingTitle.append(node('span', 'loading-orb'), document.createTextNode('Finding your next stop…'));
  loading.append(loadingTitle, node('p', '', 'Checking mapped places, base drives, fictional Dice preferences, and typed decisions. Live lookups may take a little while.'));
  show(loading);
  const query = {
    mode: 'live', city, intent: form.elements.intent.value,
    mealPeriod: form.elements.mealPeriod.value, availableMinutes: Number(minutes.value),
    profile: form.elements.profile.value, engine: form.elements.engine.value
  };
  try {
    const response = await fetch('/api/guide', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(query), signal: controller.signal
    });
    const data = await response.json();
    if (current !== generation) return;
    if (!response.ok) {
      const code = data.code || 'GUIDE_UNAVAILABLE';
      renderMessage(code.replaceAll('_', ' '), code === 'MODEL_UNAVAILABLE' ? 'That engine is unavailable.'
        : code === 'DECISION_FAILED' ? 'The decision was incomplete.' : 'The lookup could not finish.',
        data.message || 'Try another city or decision engine.', true);
    } else renderGuide(data);
  } catch (error) {
    if (current !== generation || error.name === 'AbortError') return;
    renderMessage('CONNECTION ERROR', 'Bays could not connect.', 'Check that the local guide is running, then try again.', true);
  } finally {
    if (current === generation) { pending = null; busy(false); }
  }
});
