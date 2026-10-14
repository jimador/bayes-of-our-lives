const form = document.querySelector('#guide-form');
const result = document.querySelector('#result');
const submitButton = document.querySelector('#submit');
const cancelButton = document.querySelector('#cancel');
const remoteConsent = document.querySelector('#remote-consent');
const recallChoice = document.querySelector('#recall-choice');
let savedProfileId = localStorage.getItem('bayes-profile-id');
recallChoice.hidden = !savedProfileId;
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
function label(value) { return String(value || 'unknown').replaceAll('_', ' '); }
function sourceLink(parent, value, title) {
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' || url.username || url.password || !url.hostname.includes('.')) return;
    const link = node('a', 'source-link', title || `${url.hostname} ↗`);
    link.href = url.href;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    parent.append(link);
  } catch { /* Missing or malformed source URLs are plain text only. */ }
}
function notes(parent, items, heading) {
  if (!items?.length) return;
  parent.append(node('h4', '', heading));
  questions(parent, items);
}
function renderStop(stop) {
  const wrap = node('div', 'itinerary-stop');
  const time = stop.startsAt && stop.endsAt ? `${stop.startsAt.slice(0, 5)}–${stop.endsAt.slice(0, 5)}` : 'Time unresolved';
  wrap.append(node('small', '', `${time} · ${label(stop.kind)}`), node('strong', '', stop.name));
  const meta = node('div', 'lead-meta');
  meta.append(node('span', '', `Base drive: ${stop.baseTravelMinutes == null ? 'unknown' : `${stop.baseTravelMinutes} min`}`),
    node('span', '', `Route basis: ${label(stop.routeBasis)}`),
    node('span', '', `Hours basis: ${label(stop.hoursBasis)}`),
    node('span', '', `Cuisines: ${(stop.cuisines || []).join(', ') || 'unknown'}`),
    node('span', '', `Preference fit: ${label(stop.preferenceFit)}`),
    node('span', '', `Meal service basis: ${label(stop.mealServiceBasis)}`));
  wrap.append(meta);
  sourceLink(wrap, stop.sourceUrl, 'Open mapped place ↗');
  wrap.append(node('p', 'source-ids', `Evidence IDs: ${(stop.sourceIds || []).join(', ') || 'none'}`));
  notes(wrap, stop.notes, 'Advisory notes');
  return wrap;
}
function renderItinerary(data) {
  const wrap = node('div');
  if (data.status === 'needs_input') {
    wrap.append(node('span', 'result-kicker', 'A FEW DETAILS FIRST'), node('h2', '', 'Let’s fill the gaps.'));
    questions(wrap, data.questions);
    if ((data.questions || []).some(q => q.startsWith('With an early start'))) {
      const choices = node('div', 'actions follow-up');
      for (const [text, field] of [['Add breakfast', 'breakfast'], ['Add coffee', 'coffee'], ['Neither', 'earlyFoodDeclined']]) {
        const button = node('button', 'button secondary', text);
        button.type = 'button';
        button.addEventListener('click', () => {
          form.elements.breakfast.checked = field === 'breakfast';
          form.elements.coffee.checked = field === 'coffee';
          form.elements.earlyFoodDeclined.checked = field === 'earlyFoodDeclined';
          form.requestSubmit();
        });
        choices.append(button);
      }
      wrap.append(choices);
    }
  } else {
    const alternatives = data.alternatives || [];
    const chosen = alternatives.find(a => a.id === data.selectedAlternative);
    wrap.append(node('span', 'result-kicker', '02 / YOUR ITINERARY'),
      node('h2', '', chosen ? `${data.city}: ${chosen.title}` : 'More research needed'),
      node('p', '', data.status === 'usable'
        ? 'A complete timed outline fits the attributed evidence. Check changes before visiting; this is not a booking.'
        : 'Material questions remain. Review the blocking issues before relying on this outline.'));
    const meta = node('div', 'lead-meta');
    meta.append(node('span', '', `Date: ${data.date}`), node('span', '', `Status: ${label(data.status)}`),
      node('span', '', `Times: ${data.destinationTimezone || 'destination zone unverified'}`));
    wrap.append(meta);
    const decision = data.decision;
    if (decision) {
      const evidence = node('div', 'decision-evidence');
      evidence.append(node('h3', '', 'How the decision reads'),
        node('p', '', `Requested coverage: ${decision.coverage}. Choice: ${decision.choice}. Confidence: ${decision.rating}. Readiness: ${label(decision.classification)}.`),
        node('p', '', `Decision model: ${decision.model}. These answers describe evidence fit, not verified availability.`));
      wrap.append(evidence);
    }
    const list = node('div', 'itinerary-list');
    for (const alternative of alternatives) {
      const card = node('article', `itinerary-card${alternative.id === data.selectedAlternative ? ' selected' : ''}`);
      card.append(node('h3', '', `${alternative.title}${alternative.id === data.selectedAlternative ? ' · selected' : ''}`),
        node('p', '', `Outline status: ${label(alternative.status)}.`));
      if (alternative.reason) card.append(node('p', '', `Planner rationale (unverified): ${alternative.reason}`));
      for (const stop of alternative.stops || []) card.append(renderStop(stop));
      notes(card, alternative.openQuestions, 'Blocking issues');
      notes(card, alternative.notes, 'Advisory notes');
      list.append(card);
    }
    wrap.append(list);
    if (!alternatives.length) notes(wrap, data.questions, 'Blocking issues');
    const detail = node('details', 'memory-details');
    detail.append(node('summary', '', 'Preferences and request timings'),
      node('p', '', `Accepted for this outing: ${data.rememberedInterests}; ${data.rememberedPace} pace. Dice facts: ${(data.memoryFactIds || []).join(', ') || 'none'}.`),
      node('p', '', `Backend: ${Math.round(data.processMs || 0)} ms. Decision: ${Math.round(decision?.decisionMs || 0)} ms. Classification: ${Math.round(decision?.classificationMs || 0)} ms.`));
    wrap.append(detail);
    const research = node('details', 'memory-details');
    research.append(node('summary', '', 'Place research and attributed sources'),
      node('p', '', `Retrieved: ${data.retrievedUtc || 'unreported'}. Map tags and page interpretations are provisional. Search leads do not establish opening hours, meal service or allergy safety.`));
    for (const view of data.researchedPlaces || []) {
      const place = view.place, interpretation = view.interpretation;
      const item = node('div', 'itinerary-stop');
      item.append(node('strong', '', place.name));
      sourceLink(item, place.sourceUrl, `Mapped place ${place.id} ↗`);
      if (interpretation) {
        item.append(node('p', '', interpretation.summary),
          node('p', '', `Hours: ${label(interpretation.hoursStatus)}. Meals: ${(interpretation.supportedMeals || []).join(', ') || 'unknown'}. Cuisines: ${(interpretation.cuisines || []).join(', ') || 'unknown'}. Access: ${label(interpretation.access)}. Suitability: ${label(interpretation.suitability)}.`));
        for (const window of interpretation.operatingWindows || []) item.append(node('p', '', `Attributed window: ${window.date} ${window.opensAt}–${window.closesAt}`));
        item.append(node('p', 'source-ids', `Interpretation evidence IDs: ${(interpretation.sourceIds || []).join(', ') || 'none'}`));
        notes(item, interpretation.notes, 'Advisory notes');
      }
      for (const page of view.pages || []) {
        const evidence = node('div', 'page-evidence');
        evidence.append(node('p', '', `${page.id}: ${label(page.status)}`));
        sourceLink(evidence, page.url, 'Public page source ↗');
        if (page.excerpt) evidence.append(node('p', '', page.excerpt));
        item.append(evidence);
      }
      research.append(item);
    }
    for (const url of data.researchSources || []) sourceLink(research, url);
    wrap.append(research);
  }
  const revise = node('button', 'button secondary', data.status === 'needs_input' ? 'Edit the day and try again' : 'Revise requirements');
  revise.type = 'button';
  revise.addEventListener('click', () => {
    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    form.elements.city.focus({ preventScroll: true });
  });
  wrap.append(revise);
  show(wrap);
}
function updateEngine() { remoteConsent.hidden = false; }
function updateRecall() {
  const recall = form.elements.useRememberedPreferences.checked;
  form.elements.interests.disabled = recall;
  form.elements.interests.required = !recall;
  form.elements.pace.disabled = recall;
}
form.elements.useRememberedPreferences.addEventListener('change', updateRecall);
updateRecall();
form.elements.engine.addEventListener('change', updateEngine);
updateEngine();
// The destination timezone is established by the backend; the traveler chooses its local date.
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
    cuisineLikes: fields.cuisineLikes.value.trim(), cuisineDislikes: fields.cuisineDislikes.value.trim(),
    dietaryOrAllergyNeeds: fields.dietaryOrAllergyNeeds.value.trim(), engine: fields.engine.value,
    publicMapConsent: fields.publicMapConsent.checked, remoteModelConsent: fields.remoteModelConsent.checked,
    profileId: savedProfileId || (fields.rememberPreferences.checked ? crypto.randomUUID() : null),
    rememberPreferences: fields.rememberPreferences.checked,
    useRememberedPreferences: fields.useRememberedPreferences.checked
  };
  busy(true);
  message('RESEARCHING', 'Building your day…', 'Looking up mapped places and base routes, retrieving accepted Dice preferences, and comparing itinerary alternatives.');
  try {
    const response = await fetch('/api/itinerary', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(query), signal: controller.signal });
    const data = await response.json();
    if (current !== generation) return;
    if (!response.ok) message(data.code || 'ERROR', 'Bayes could not finish.', data.message || 'Check the details and try again.');
    else {
      if (query.rememberPreferences && data.status !== 'needs_input') {
        localStorage.setItem('bayes-profile-id', query.profileId);
        savedProfileId = query.profileId;
        recallChoice.hidden = false;
      }
      renderItinerary(data);
    }
  } catch (error) {
    if (current !== generation || error.name === 'AbortError') return;
    message('CONNECTION ERROR', 'Bayes could not connect.', 'Check that the local app is running, then try again.');
  } finally {
    if (current === generation) { pending = null; busy(false); }
  }
});
