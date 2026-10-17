/* Screen assets stay unchanged. This viewer only connects existing designs. */
'use strict';
const $ = id => document.getElementById(id);
const frame = $('screen');
let config, current, loadedScreen, state, trail = [], timer, loadToken = 0, guidelinesOn = true;
const forms = new Map();
const normal = text => (text || '').replace(/\s+/g, ' ').trim();
const describe = screen => screen.title.replace(/\s*\[fixed\]$/i, '');
const enabled = element => element && !element.closest(':disabled, [aria-disabled="true"]');

function updateGuidelines() {
  $('guidelines').setAttribute('aria-pressed', String(guidelinesOn));
  $('guidelines').textContent = `Red guidelines: ${guidelinesOn ? 'On' : 'Off'}`;
  frame.contentDocument?.documentElement.toggleAttribute('data-prototype-guidelines', guidelinesOn);
}
function destination(target, role = state.role) {
  const id = target?.startsWith('@') ? config.destinations[role]?.[target.slice(1)] : target;
  return config.screens[id] ? id : null;
}
function resolve(rule) {
  const role = rule.action === 'switchRole' ? state.selectedRole : rule.setRole || state.role;
  let target;
  switch (rule.action) {
    case 'goto': target = rule.target; break;
    case 'back': target = trail.at(-1) || rule.fallback || config.screens[current].fallback; break;
    case 'branch': target = rule.cases[state[rule.key]] || rule.default; break;
    case 'switchRole': target = '@home'; break;
    default: return null;
  }
  target = destination(target, role);
  return target && target !== current ? {target, role} : null;
}
function saveForm() {
  if (!current || loadedScreen !== current) return;
  forms.set(current, [...frame.contentDocument.querySelectorAll('input, textarea, select')]
    .map(input => ({value: input.value, checked: input.checked})));
}
function captureBindings(page, doc) {
  for (const binding of page.bindings || []) {
    [...doc.querySelectorAll(binding.selector)].forEach((input, i) => {
      if (input.type !== 'radio' || input.checked) state[binding.state] = binding.values?.[i] ?? input.value;
    });
  }
}
function scalePreview() {
  const stage = $('stage');
  const scale = Math.min(1, Math.max(.3, (stage.clientWidth - 32) / 390), Math.max(.3, (stage.clientHeight - 28) / 884));
  $('phone-space').style.width = `${390 * scale}px`;
  $('phone-space').style.height = `${884 * scale}px`;
  frame.style.transform = `scale(${scale})`;
}
function renderList() {
  const search = normal($('search').value).toLowerCase();
  const list = $('screen-list'); list.replaceChildren();
  let count = 0;
  for (const group of config.groups) {
    const ids = group.screens.filter(id => `${id} ${describe(config.screens[id])}`.toLowerCase().includes(search));
    if (!ids.length) continue;
    const details = document.createElement('details'); details.open = !!search || ids.includes(current);
    const summary = document.createElement('summary'); summary.textContent = `${group.title} (${ids.length})`;
    details.append(summary);
    for (const id of ids) {
      const link = document.createElement('a'); link.href = `#/${id}`;
      const ref = document.createElement('span'); ref.className = 'ref'; ref.textContent = id;
      link.append(ref, describe(config.screens[id]));
      if (id === current) link.setAttribute('aria-current', 'page');
      link.addEventListener('click', event => {
        event.preventDefault(); navigate(id, {setRole: config.screens[id].roleHint});
      });
      details.append(link); count++;
    }
    list.append(details);
  }
  $('screen-count').textContent = `${count} / ${Object.keys(config.screens).length}`;
}
function navigate(target, options = {}) {
  const role = options.setRole || state.role;
  target = destination(target, role);
  if (!target || (target === current && !options.reload)) return;
  saveForm(); clearTimeout(timer);
  if (options.root) trail = [];
  else if (!options.replace && current) trail.push(current);
  current = target; state.role = role;
  const entry = {prototype: true, screen: current, role: state.role, trail: [...trail]};
  history[options.replace ? 'replaceState' : 'pushState'](entry, '', `#/${current}`);
  render();
}
function goBack(fallback) {
  if (trail.length && history.state?.prototype) { history.back(); return; }
  const target = destination(fallback || config.screens[current].fallback);
  if (target && target !== current) navigate(target, {replace: true});
}
function follow(rule) {
  const transition = resolve(rule);
  if (!transition) return;
  if (rule.action === 'back') goBack(rule.fallback);
  else navigate(transition.target, {setRole: transition.role, root: rule.root || rule.action === 'switchRole'});
}
function findRule(page, node, event) {
  if (!(node instanceof frame.contentWindow.Element)) return null;
  let best = null, distance = Infinity;
  for (const rule of page.rules) {
    if ((rule.event || 'click') !== event) continue;
    const match = node.closest(rule.selector);
    if (!match) continue;
    let d = 0; for (let n = node; n !== match; n = n.parentElement) d++;
    if (d <= distance) { best = rule; distance = d; }
  }
  return best;
}
function render() {
  const page = config.screens[current], token = ++loadToken;
  const asset = new URL(page.asset, location.href).href;
  loadedScreen = null;
  $('screen-id').textContent = current; $('screen-title').textContent = describe(page); $('role').value = state.role;
  $('stage').classList.add('loading'); $('load-error').hidden = true;
  renderList();
  frame.onload = () => {
    if (token !== loadToken || frame.contentWindow.location.href !== asset) return;
    const doc = frame.contentDocument;
    if (!doc?.body) {
      $('stage').classList.remove('loading');
      $('load-error').hidden = false; $('load-error').textContent = 'Could not load this screen.';
      return;
    }
    loadedScreen = current;
    const styles = doc.createElement('link'); styles.rel = 'stylesheet';
    styles.href = new URL('hotspots.css?v=4', location.href).href; doc.head.append(styles);
    updateGuidelines();
    const values = forms.get(current);
    if (values) [...doc.querySelectorAll('input, textarea, select')].forEach((input, i) => {
      if (values[i]) { input.value = values[i].value; input.checked = values[i].checked; }
    });
    for (const binding of page.bindings || []) {
      if (binding.state === 'selectedRole') state.selectedRole = state.role;
      [...doc.querySelectorAll(binding.selector)].forEach((input, i) => {
        if (input.type === 'radio') input.checked = binding.values[i] === state[binding.state];
      });
    }
    const updateTransitions = () => {
      for (const gate of page.gates || []) {
        const button = doc.querySelector(gate.selector), checkbox = doc.querySelector(gate.checkbox);
        if (button && checkbox) { button.disabled = !checkbox.checked; button.setAttribute('aria-disabled', String(!checkbox.checked)); }
      }
      doc.querySelectorAll('[data-prototype-transition]').forEach(element => element.removeAttribute('data-prototype-transition'));
      for (const rule of page.rules) {
        if ((rule.event || 'click') !== 'click' || !resolve(rule)) continue;
        for (const element of doc.querySelectorAll(rule.selector)) if (enabled(element)) element.setAttribute('data-prototype-transition', '');
      }
      $('back').disabled = !resolve({action: 'back'});
    };
    const changed = () => { captureBindings(page, doc); saveForm(); updateTransitions(); };
    updateTransitions();
    doc.addEventListener('input', changed); doc.addEventListener('change', changed);
    doc.addEventListener('submit', event => event.preventDefault(), true);
    doc.addEventListener('click', event => {
      captureBindings(page, doc);
      const rule = findRule(page, event.target, 'click');
      if (rule && enabled(event.target.closest(rule.selector))) {
        event.preventDefault(); event.stopImmediatePropagation(); follow(rule);
      } else if (event.target.closest('a')) event.preventDefault();
    }, true);
    doc.addEventListener('keydown', event => {
      if (event.key === 'Escape') { event.preventDefault(); goBack(); }
      if (event.key === 'Enter') {
        const rule = findRule(page, event.target, 'enter');
        if (rule) { event.preventDefault(); captureBindings(page, doc); follow(rule); }
        else if (event.target.matches('[role=button], [role=tab]')) event.target.click();
      }
    }, true);
    $('stage').classList.remove('loading'); scalePreview();
    if (page.after) timer = setTimeout(() => navigate(page.after.target, {replace: page.after.replace, root: page.after.root}), page.after.delay);
  };
  frame.contentWindow.location.replace(asset);
}
async function start() {
  try {
    const response = await fetch('wiring.json');
    if (!response.ok) throw new Error('Navigation could not be loaded.');
    config = await response.json(); state = {...config.defaults};
    for (const journey of config.journeys) {
      const option = document.createElement('option'); option.value = journey.id; option.textContent = journey.title; $('journey').append(option);
    }
    $('journey').addEventListener('change', () => {
      const journey = config.journeys.find(j => j.id === $('journey').value); if (!journey) return;
      $('journey-description').textContent = journey.description;
      navigate(journey.start, {root: true, setRole: journey.role});
    });
    $('search').addEventListener('input', renderList); $('back').addEventListener('click', () => goBack());
    $('role').addEventListener('change', () => navigate('@home', {root: true, setRole: $('role').value}));
    $('guidelines').addEventListener('click', () => { guidelinesOn = !guidelinesOn; updateGuidelines(); });
    $('restart').addEventListener('click', () => { loadedScreen = null; forms.clear(); state = {...config.defaults}; navigate(config.start, {root: true, reload:true}); });
    window.addEventListener('resize', scalePreview);
    window.addEventListener('popstate', event => {
      const id = location.hash.replace(/^#\//, '');
      if (!config.screens[id]) return;
      saveForm(); clearTimeout(timer); current = id;
      trail = event.state?.trail || []; state.role = event.state?.role || config.screens[id].roleHint || state.role; render();
    });
    window.addEventListener('hashchange', () => {
      const id = location.hash.replace(/^#\//, '');
      if (id !== current && config.screens[id]) navigate(id, {replace:true});
    });
    const initial = location.hash.replace(/^#\//, '');
    navigate(config.screens[initial] ? initial : config.start, {replace:true, root:true, setRole:config.screens[initial]?.roleHint});
  } catch (error) {
    $('screen-title').textContent = 'Could not start the viewer'; $('load-error').hidden = false;
    $('load-error').textContent = `${error.message} Serve this folder over HTTP: python3 -m http.server 8080`;
  }
}
start();
