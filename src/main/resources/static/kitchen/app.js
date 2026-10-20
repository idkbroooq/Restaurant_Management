'use strict';

/* ============================================================================
 * Kitchen Display (KDS) — plain ES2020, no frameworks, no build step.
 *
 * Contract (docs/openapi.yaml):
 *   POST   /api/auth/login          -> { token, staff: StaffProfile }
 *   GET    /api/auth/me             -> StaffProfile
 *   POST   /api/auth/logout         -> { message }
 *   GET    /api/kitchen/orders      -> KitchenOrderTicket[]
 *   PATCH  /api/orders/{id}/status  -> OrderResponse | 409 StatusConflictErrorResponse
 *   OrderStatus lifecycle: PLACED -> ACCEPTED -> PREPARING -> READY -> SERVED
 * ========================================================================== */

/* ----------------------------- Contract constants ------------------------- */

// Board columns, in lifecycle order.
var COLUMNS = ['PLACED', 'ACCEPTED', 'PREPARING', 'READY'];

// Mirrors OrderStatus.getNextStatuses() exactly (CANCELLED is handled separately).
var NEXT_STATUS = {
  PLACED: 'ACCEPTED',
  ACCEPTED: 'PREPARING',
  PREPARING: 'READY',
  READY: 'SERVED'
};

// The states that may still be cancelled.
var CANCELLABLE = new Set(Object.keys(NEXT_STATUS));

var NEXT_LABEL = {
  ACCEPTED: 'Accept',
  PREPARING: 'Start preparing',
  READY: 'Mark ready',
  SERVED: 'Serve'
};

var ALLOWED_ROLES = new Set(['CHEF', 'MANAGER']);

var POLL_MS = 3000;
var WARN_SECONDS = 10 * 60;   // amber from 10 minutes
var DANGER_SECONDS = 20 * 60; // red from 20 minutes
var NEW_HIGHLIGHT_MS = 3000;

var MSG_SESSION_EXPIRED = 'Your session expired. Please sign in again.';
var MSG_ROLE_DENIED = 'Your role is not permitted to use the kitchen board.';
var MSG_ROLE_ACTION = 'Your role is not permitted to perform this action.';

/* -------------------------------- DOM handles ----------------------------- */

var loginScreen = document.getElementById('login-screen');
var loginForm = document.getElementById('login-form');
var loginError = document.getElementById('login-error');
var loginSubmit = document.getElementById('login-submit');
var usernameInput = document.getElementById('username');
var passwordInput = document.getElementById('password');

var boardScreen = document.getElementById('board-screen');
var boardEl = document.getElementById('board');
var staffChip = document.getElementById('staff-chip');
var connectionBanner = document.getElementById('connection-banner');
var soundToggle = document.getElementById('sound-toggle');
var logoutButton = document.getElementById('logout');
var toastsEl = document.getElementById('toasts');

/* ---------------------------------- State --------------------------------- */

var state = {
  staff: null,
  cards: new Map(),     // orderId -> { root, elapsedEl, nextBtn, cancelBtn, status, placedAtMs, busy }
  hasRendered: false,   // suppresses the arrival alert for the first batch
  pollTimer: null,
  pollInFlight: false,
  offline: false,
  roleWarned: false,
  elapsedTimer: null,
  audioCtx: null,
  audioUnlocked: false,
  soundOn: true
};

var columnCards = new Map();  // status -> scrolling card container
var columnCounts = new Map(); // status -> count badge

/* --------------------------------- Helpers -------------------------------- */

/** Escapes a value for safe interpolation into an HTML template string. */
function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function HttpError(status, payload) {
  var message = payload && payload.message ? payload.message : 'HTTP ' + status;
  var error = new Error(message);
  error.name = 'HttpError';
  error.status = status;
  error.payload = payload;
  return error;
}

/** Thin fetch wrapper: same-origin cookies, JSON in/out, structured errors. */
function api(path, options) {
  var opts = options || {};
  var init = { method: opts.method || 'GET', credentials: 'same-origin' };
  if (opts.body !== undefined) {
    init.headers = { 'Content-Type': 'application/json' };
    init.body = JSON.stringify(opts.body);
  }
  return fetch(path, init).then(function (response) {
    return response.text().then(function (text) {
      var payload = null;
      if (text) {
        try { payload = JSON.parse(text); } catch (e) { payload = null; }
      }
      if (!response.ok) throw HttpError(response.status, payload);
      return payload;
    });
  });
}

function isHttpError(err, status) {
  return err && err.name === 'HttpError' && err.status === status;
}

/* ------------------------------ Toasts / banner --------------------------- */

function showToast(message, tone) {
  var el = document.createElement('div');
  el.className = 'toast' + (tone ? ' toast-' + tone : '');
  el.textContent = message;
  toastsEl.appendChild(el);
  setTimeout(function () {
    el.classList.add('is-out');
    setTimeout(function () { el.remove(); }, 300);
  }, 4500);
}

function clearToasts() {
  toastsEl.replaceChildren();
}

function setOffline(value) {
  if (state.offline === value) return;
  state.offline = value;
  connectionBanner.hidden = !value;
}

/* ------------------------------- Elapsed time ----------------------------- */

/** mm:ss under an hour, "Xm Ys" from an hour onwards. */
function formatElapsed(totalSeconds) {
  var minutes = Math.floor(totalSeconds / 60);
  var seconds = totalSeconds % 60;
  if (minutes >= 60) return minutes + 'm ' + seconds + 's';
  return String(minutes).padStart(2, '0') + ':' + String(seconds).padStart(2, '0');
}

function updateElapsed(card, nowMs) {
  var seconds = Math.max(0, Math.floor((nowMs - card.placedAtMs) / 1000));
  card.elapsedEl.textContent = formatElapsed(seconds);
  card.elapsedEl.classList.toggle('is-warn', seconds >= WARN_SECONDS && seconds < DANGER_SECONDS);
  card.elapsedEl.classList.toggle('is-danger', seconds >= DANGER_SECONDS);
}

function tickElapsed() {
  var nowMs = Date.now();
  state.cards.forEach(function (card) { updateElapsed(card, nowMs); });
}

function startElapsedTicker() {
  if (state.elapsedTimer) return;
  state.elapsedTimer = setInterval(tickElapsed, 1000);
}

/* --------------------------------- Cards ---------------------------------- */

function createCard(ticket) {
  var root = document.createElement('article');
  root.className = 'card';
  root.dataset.orderId = String(ticket.orderId);
  root.innerHTML =
    '<div class="card-top">' +
      '<span class="order-no">Order #' + escapeHtml(ticket.orderId) + '</span>' +
      '<span class="elapsed"></span>' +
    '</div>' +
    '<div class="card-meta"><span class="table-no">Table ' +
      escapeHtml(ticket.tableNumber || '—') + '</span></div>' +
    '<ul class="items"></ul>' +
    '<div class="card-actions">' +
      '<button type="button" class="btn btn-primary next-btn"></button>' +
      '<button type="button" class="btn btn-danger cancel-btn">Cancel</button>' +
    '</div>';

  var itemsEl = root.querySelector('.items');
  (ticket.items || []).forEach(function (item) {
    var li = document.createElement('li');
    li.className = 'item';

    var qty = document.createElement('span');
    qty.className = 'item-qty';
    qty.textContent = item.quantity + '\u00d7';

    var name = document.createElement('span');
    name.className = 'item-name';
    name.textContent = item.name;

    li.append(qty, name);

    if (item.itemNotes) {
      var note = document.createElement('span');
      note.className = 'item-note';
      note.textContent = item.itemNotes;
      li.append(note);
    }
    itemsEl.append(li);
  });

  if (ticket.specialNotes) {
    var notes = document.createElement('p');
    notes.className = 'notes';
    notes.textContent = ticket.specialNotes;
    root.querySelector('.card-actions').before(notes);
  }

  // Prefer the ISO timestamp so the clock can tick client-side; fall back to
  // the contract's server-computed elapsedMinutes if the timestamp is unusable.
  var placedAtMs = Date.parse(ticket.orderPlacedAt);
  if (isNaN(placedAtMs)) {
    placedAtMs = Date.now() - (Number(ticket.elapsedMinutes) || 0) * 60000;
  }

  var card = {
    root: root,
    elapsedEl: root.querySelector('.elapsed'),
    nextBtn: root.querySelector('.next-btn'),
    cancelBtn: root.querySelector('.cancel-btn'),
    status: null,
    placedAtMs: placedAtMs,
    busy: false
  };

  card.nextBtn.addEventListener('click', function () { moveOrder(ticket.orderId, 'next'); });
  card.cancelBtn.addEventListener('click', function () { moveOrder(ticket.orderId, 'cancel'); });

  updateElapsed(card, Date.now());
  return card;
}

/** Rewrites the action buttons whenever the ticket enters a new status. */
function renderActions(card, status) {
  var next = NEXT_STATUS[status];
  if (next) {
    card.nextBtn.textContent = NEXT_LABEL[next] || next;
    card.nextBtn.hidden = false;
  } else {
    card.nextBtn.hidden = true;
  }
  card.cancelBtn.hidden = !CANCELLABLE.has(status);
}

/* ------------------------------- Board render ----------------------------- */

function buildColumns() {
  COLUMNS.forEach(function (status) {
    var column = document.createElement('section');
    column.className = 'column';

    var head = document.createElement('header');
    head.className = 'column-head';

    var name = document.createElement('h2');
    name.className = 'column-name';
    name.textContent = status;

    var count = document.createElement('span');
    count.className = 'column-count';
    count.textContent = '0';

    head.append(name, count);

    var cards = document.createElement('div');
    cards.className = 'cards';

    column.append(head, cards);
    boardEl.append(column);

    columnCards.set(status, cards);
    columnCounts.set(status, count);
  });
}

/** Diffs the polled tickets into the existing DOM (never re-renders the tree). */
function renderBoard(tickets) {
  var byColumn = new Map();
  var liveIds = new Set();
  var arrivals = [];

  COLUMNS.forEach(function (status) { byColumn.set(status, []); });

  tickets.forEach(function (ticket) {
    if (!byColumn.has(ticket.status)) return; // SERVED / CANCELLED are off-board
    liveIds.add(ticket.orderId);

    var card = state.cards.get(ticket.orderId);
    if (!card) {
      card = createCard(ticket);
      state.cards.set(ticket.orderId, card);
      // Only flag genuine arrivals; the first batch would otherwise alert en masse.
      if (state.hasRendered) {
        card.root.classList.add('is-new');
        arrivals.push(card.root);
      }
    }

    if (card.status !== ticket.status) {
      card.status = ticket.status;
      renderActions(card, ticket.status);
    }

    byColumn.get(ticket.status).push({
      id: ticket.orderId,
      placedAtMs: card.placedAtMs
    });
  });

  // Drop cards that left the board (served or cancelled elsewhere).
  state.cards.forEach(function (card, id) {
    if (!liveIds.has(id)) {
      card.root.remove();
      state.cards.delete(id);
    }
  });

  COLUMNS.forEach(function (status) {
    var container = columnCards.get(status);
    var rows = byColumn.get(status).sort(function (a, b) { return a.placedAtMs - b.placedAtMs; });

    rows.forEach(function (row, index) {
      var el = state.cards.get(row.id).root;
      var current = container.children[index];
      if (current !== el) container.insertBefore(el, current || null);
    });

    columnCounts.get(status).textContent = String(rows.length);
  });

  state.hasRendered = true;

  if (arrivals.length > 0) {
    playChime();
    setTimeout(function () {
      arrivals.forEach(function (el) { el.classList.remove('is-new'); });
    }, NEW_HIGHLIGHT_MS);
  }
}

/* --------------------------------- Polling -------------------------------- */

function schedulePoll() {
  clearTimeout(state.pollTimer);
  state.pollTimer = setTimeout(runPollCycle, POLL_MS);
}

function stopPolling() {
  clearTimeout(state.pollTimer);
  state.pollTimer = null;
}

async function pollOnce() {
  try {
    var tickets = await api('/api/kitchen/orders');
    setOffline(false);
    renderBoard(Array.isArray(tickets) ? tickets : []);
  } catch (err) {
    if (isHttpError(err, 401)) { onSessionExpired(); return; }
    if (isHttpError(err, 403)) {
      if (!state.roleWarned) {
        state.roleWarned = true;
        showToast(MSG_ROLE_ACTION, 'error');
      }
      return;
    }
    setOffline(true); // network failure or server error — the banner clears on the next success
  }
}

async function runPollCycle() {
  state.pollTimer = null;
  if (document.hidden || !state.staff || state.pollInFlight) return;

  state.pollInFlight = true;
  try {
    await pollOnce();
  } finally {
    state.pollInFlight = false;
    if (!document.hidden && state.staff) schedulePoll();
  }
}

/** Restarts the chain and fetches immediately (used after a mutation). */
function refreshBoard() {
  stopPolling();
  runPollCycle();
}

/* ----------------------------- Status mutations --------------------------- */

async function moveOrder(orderId, kind) {
  var card = state.cards.get(orderId);
  if (!card || card.busy) return;

  var from = card.status;
  var to = kind === 'cancel' ? 'CANCELLED' : NEXT_STATUS[from];
  if (!to) return;

  if (kind === 'cancel' && !window.confirm('Cancel Order #' + orderId + '? This cannot be undone.')) {
    return;
  }

  card.busy = true;
  card.root.classList.add('is-busy');

  try {
    await api('/api/orders/' + orderId + '/status', {
      method: 'PATCH',
      body: {
        from: from,
        to: to,
        notes: 'Kitchen board: ' + from + ' -> ' + to
      }
    });
    refreshBoard();
  } catch (err) {
    if (isHttpError(err, 409)) {
      showToast('Order #' + orderId + ' was already moved by someone else \u2014 refreshing the board', 'warn');
      refreshBoard();
    } else if (isHttpError(err, 401)) {
      onSessionExpired();
    } else if (isHttpError(err, 403)) {
      showToast(MSG_ROLE_ACTION, 'error');
    } else {
      showToast(err && err.message ? err.message : 'Could not update the order.', 'error');
    }
  } finally {
    card.busy = false;
    card.root.classList.remove('is-busy');
  }
}

/* --------------------------------- Audio ---------------------------------- */

function updateSoundButton() {
  if (!state.audioUnlocked) {
    soundToggle.textContent = 'Enable sound';
    soundToggle.setAttribute('aria-pressed', 'false');
  } else if (state.soundOn) {
    soundToggle.textContent = 'Sound: On';
    soundToggle.setAttribute('aria-pressed', 'true');
  } else {
    soundToggle.textContent = 'Sound: Off';
    soundToggle.setAttribute('aria-pressed', 'false');
  }
}

/** Short two-tone chime built with the Web Audio API (no audio asset). */
function playChime() {
  if (!state.soundOn || !state.audioCtx || state.audioCtx.state !== 'running') return;

  var ctx = state.audioCtx;
  var now = ctx.currentTime;
  var osc = ctx.createOscillator();
  var gain = ctx.createGain();

  osc.type = 'sine';
  osc.frequency.setValueAtTime(880, now);
  osc.frequency.setValueAtTime(1320, now + 0.12);

  gain.gain.setValueAtTime(0.0001, now);
  gain.gain.exponentialRampToValueAtTime(0.3, now + 0.02);
  gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.4);

  osc.connect(gain).connect(ctx.destination);
  osc.start(now);
  osc.stop(now + 0.42);
}

/** Browsers block audio until a user gesture, so the AudioContext is created here. */
function onSoundToggle() {
  if (!state.audioUnlocked) {
    var Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) {
      state.soundOn = false;
      updateSoundButton();
      showToast('This browser does not support audio alerts.', 'error');
      return;
    }
    if (!state.audioCtx) state.audioCtx = new Ctx();
    var resume = state.audioCtx.state === 'suspended' ? state.audioCtx.resume() : Promise.resolve();
    resume.then(function () {
      state.audioUnlocked = true;
      state.soundOn = true;
      updateSoundButton();
      playChime();
    });
    return;
  }

  state.soundOn = !state.soundOn;
  updateSoundButton();
}

/* ------------------------------- Screens ---------------------------------- */

function showLogin(message) {
  boardScreen.hidden = true;
  loginScreen.hidden = false;
  passwordInput.value = '';
  if (message) {
    loginError.textContent = message;
    loginError.hidden = false;
  } else {
    loginError.textContent = '';
    loginError.hidden = true;
  }
  usernameInput.focus();
}

function setLoginBusy(busy) {
  loginSubmit.disabled = busy;
  loginSubmit.textContent = busy ? 'Signing in\u2026' : 'Sign in';
}

function resetState() {
  stopPolling();
  state.cards.forEach(function (card) { card.root.remove(); });
  state.cards.clear();
  state.hasRendered = false;
  state.roleWarned = false;
  state.staff = null;
  setOffline(false);
  columnCounts.forEach(function (el) { el.textContent = '0'; });
  clearToasts();
}

function enterBoard(staff) {
  resetState();
  state.staff = staff;
  staffChip.textContent = staff.fullName + ' \u00b7 ' + staff.role;
  loginScreen.hidden = true;
  boardScreen.hidden = false;
  updateSoundButton();
  startElapsedTicker();
  runPollCycle();
}

function onSessionExpired() {
  resetState();
  showLogin(MSG_SESSION_EXPIRED);
}

/* --------------------------------- Events --------------------------------- */

async function onLoginSubmit(event) {
  event.preventDefault();
  var username = usernameInput.value.trim();
  var password = passwordInput.value;
  if (!username || !password) return;

  setLoginBusy(true);
  try {
    var result = await api('/api/auth/login', {
      method: 'POST',
      body: { username: username, password: password }
    });
    var staff = result.staff;
    if (!staff || !ALLOWED_ROLES.has(staff.role)) {
      showLogin(MSG_ROLE_DENIED);
      return;
    }
    enterBoard(staff);
  } catch (err) {
    showLogin(err && err.name === 'HttpError'
      ? err.message
      : 'Unable to reach the server. Please try again.');
  } finally {
    setLoginBusy(false);
  }
}

async function onLogout() {
  stopPolling();
  try {
    await api('/api/auth/logout', { method: 'POST' });
  } catch (err) {
    // Local state is cleared regardless; the server session may already be gone.
  }
  resetState();
  showLogin(null);
}

function wireEvents() {
  loginForm.addEventListener('submit', onLoginSubmit);
  logoutButton.addEventListener('click', onLogout);
  soundToggle.addEventListener('click', onSoundToggle);

  // Pause the poll while the tab is hidden and refresh immediately on return.
  document.addEventListener('visibilitychange', function () {
    if (!state.staff) return;
    if (document.hidden) stopPolling();
    else refreshBoard();
  });
}

/* ---------------------------------- Boot ---------------------------------- */

async function init() {
  buildColumns();
  wireEvents();
  updateSoundButton();

  try {
    var staff = await api('/api/auth/me');
    if (staff && ALLOWED_ROLES.has(staff.role)) {
      enterBoard(staff);
      return;
    }
    showLogin(MSG_ROLE_DENIED);
  } catch (err) {
    // A 401 simply means "no session yet"; anything else also lands on the login screen.
    showLogin(null);
  }
}

init();
