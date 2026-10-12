const form = document.querySelector('#guide-form');
const result = document.querySelector('#result');
const submitButton = document.querySelector('#submit');
const cancelButton = document.querySelector('#cancel');
const remoteConsent = document.querySelector('#remote-consent');
let pending = null, generation = 0;

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
  submitButton.querySelector('span').textContent = value ? 'Researching your day…' : 'Build my itinerary';
}
function message(kicker, title, body) {
  const wrap = node('div');
  wrap.append(node('span', 'result-kicker', kicker), node('h2', '', title), node('p', '', body));
  show(wrap);
}
function questions(parent, items) {
  if (!items?.length) return;
  const list = node('ul', 'question-list');
  for (const item of [...new Set(items)]) list.append(node('li', '', item));
  parent.append(list);
}
function renderStop(stop) {
  const wrap = node('div', 'itinerary-stop');
  const time = stop.startsAt && stop.endsAt ? `${stop.startsAt.slice(0, 5)}–${stop.endsAt.slice(0, 5)}` : 'Time unresolved';
  wrap.append(node('small', '', `${time} · ${stop.kind}`), node('strong', '', stop.name));
  const meta = node('div', 'lead-meta');
  meta.append(node('span', '', `Base drive: ${stop.baseTravelMinutes == null ? 'unknown' : `${stop.baseTravelMinutes} min`}`));
  meta.append(node('span', '', `OSM hours: ${stop.hoursFit.replaceAll('_', ' ')}`));
  meta.append(node('span', '', `Step-free tag: ${stop.wheelchairTag}`));
  meta.append(node('span', '', `Cuisine tag: ${stop.cuisineTag}`));
  meta.append(node('span', '', `Admission tag: ${stop.admissionTag}`));
  wrap.append(meta);
  if (/^https:\/\/www\.openstreetmap\.org\/(node|way|relation)\/\d+$/.test(stop.sourceUrl || '')) {
    const link = node('a', '', 'Open mapped place ↗');
    link.href = stop.sourceUrl;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    wrap.append(link);
  }
  questions(wrap, stop.openQuestions);
  return wrap;
}
function renderItinerary(data) {
  const wrap = node('div');
  if (data.status === 'needs_input') {
    wrap.append(node('span', 'result-kicker', 'A FEW DETAILS FIRST'), node('h2', '', 'Let’s fill the gaps.'));
    questions(wrap, data.questions);
    show(wrap);
    return;
  }
  const chosen = data.alternatives.find(a => a.id === data.selectedAlternative);
  wrap.append(node('span', 'result-kicker', '02 / YOUR ITINERARY'),
    node('h2', '', chosen ? `${data.city}: ${chosen.title}` : 'More research needed'));
  wrap.append(node('p', '', data.status === 'sourced'
    ? 'The mapped evidence supports this outline. Confirm current details before leaving.'
    : 'This is an itinerary outline with questions to settle before you go. Bayes will not fill unknown facts with guesses.'));
  const meta = node('div', 'lead-meta');
  meta.append(node('span', '', `Date: ${data.date}`), node('span', '', `Status: ${data.status.replaceAll('_', ' ')}`),
    node('span', '', `Decision: ${data.decision?.model || 'unavailable'}`));
  wrap.append(meta);
  const list = node('div', 'itinerary-list');
  for (const alternative of data.alternatives) {
    const card = node('article', `itinerary-card${alternative.id === data.selectedAlternative ? ' selected' : ''}`);
    card.append(node('h3', '', `${alternative.title}${alternative.id === data.selectedAlternative ? ' · selected' : ''}`),
      node('p', '', `Evidence status: ${alternative.status}. ${alternative.elapsedMinutes < 0 ? 'Timeline unresolved.' : `${alternative.elapsedMinutes} minutes from start to last stop.`}`));
    for (const stop of alternative.stops) card.append(renderStop(stop));
    questions(card, alternative.openQuestions);
    list.append(card);
  }
  wrap.append(list);
  const detail = node('details', 'memory-details');
  detail.append(node('summary', '', 'See Dice memory and typed decision evidence'));
  detail.append(node('p', '', `Accepted for this outing: ${data.rememberedInterests}; ${data.rememberedPace} pace. Dice facts: ${(data.memoryFactIds || []).join(', ')}`));
  detail.append(node('p', '', `Typed result: ${data.decision?.completeness}; choice ${data.decision?.choice}; rating ${data.decision?.rating}; classification ${data.decision?.classification}. Input SHA-256: ${data.decision?.inputSha256}. Decision call ${Math.round(data.decision?.decisionMs || 0)} ms.`));
  wrap.append(detail);
  show(wrap);
}
function updateEngine() { remoteConsent.hidden = form.elements.engine.value === 'local'; }
form.elements.engine.addEventListener('change', updateEngine);
updateEngine();
form.elements.date.min = new Date().toISOString().slice(0, 10);
form.elements.date.value = form.elements.date.min;
cancelButton.addEventListener('click', () => {
  generation++;
  pending?.abort();
  pending = null;
  busy(false);
  message('CANCELLED', 'Take your time.', 'The browser request was cancelled. An already running server lookup may still finish.');
});
form.addEventListener('submit', async event => {
  event.preventDefault();
  if (!form.reportValidity()) return;
  pending?.abort();
  const current = ++generation;
  const controller = new AbortController();
  pending = controller;
  const fields = form.elements;
  const query = {
    city: fields.city.value.trim(), startingLocation: fields.startingLocation.value.trim(),
    date: fields.date.value, startTime: fields.startTime.value, endTime: fields.endTime.value,
    breakfast: fields.breakfast.checked, coffee: fields.coffee.checked,
    earlyFoodDeclined: fields.earlyFoodDeclined.checked, lunch: fields.lunch.checked, dinner: fields.dinner.checked,
    partySize: Number(fields.partySize.value), childrenCount: Number(fields.childrenCount.value),
    interests: fields.interests.value.trim(), pace: fields.pace.value, stepFreeAccess: fields.stepFreeAccess.checked,
    dietaryOrAllergyNeeds: fields.dietaryOrAllergyNeeds.value.trim(), engine: fields.engine.value,
    publicMapConsent: fields.publicMapConsent.checked, remoteModelConsent: fields.remoteModelConsent.checked
  };
  busy(true);
  message('RESEARCHING', 'Building your day…', 'Looking up mapped places and base routes, retrieving accepted Dice preferences, and comparing itinerary alternatives.');
  try {
    const response = await fetch('/api/itinerary', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(query), signal: controller.signal });
    const data = await response.json();
    if (current !== generation) return;
    if (!response.ok) message(data.code || 'ERROR', 'Bayes could not finish.', data.message || 'Check the details and try again.');
    else renderItinerary(data);
  } catch (error) {
    if (current !== generation || error.name === 'AbortError') return;
    message('CONNECTION ERROR', 'Bayes could not connect.', 'Check that the local app is running, then try again.');
  } finally {
    if (current === generation) { pending = null; busy(false); }
  }
});
