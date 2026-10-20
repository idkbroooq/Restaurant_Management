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

// Human labels per column and the empty-state copy shown when a column is quiet.
var STATUS_LABEL = {
  PLACED: 'Placed',
  ACCEPTED: 'Accepted',
  PREPARING: 'Preparing',
  READY: 'Ready'
};

var EMPTY_TEXT = {
  PLACED: 'No new orders',
  ACCEPTED: 'Nothing accepted yet',
  PREPARING: 'Nothing on the pass',
  READY: 'Nothing waiting to be served'
};

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

var liveStatus = document.getElementById('live-status');
var liveText = document.getElementById('live-text');
var statusTabs = document.getElementById('status-tabs');
var boardLive = document.getElementById('board-live');
var oldestOrderEl = document.getElementById('oldest-order');
var confirmOverlay = document.getElementById('confirm-overlay');
var confirmText = document.getElementById('confirm-text');
var confirmKeep = document.getElementById('confirm-keep');
var confirmCancel = document.getElementById('confirm-cancel');

/* ---------------------------------- State --------------------------------- */

var state = {
  staff: null,
  cards: new Map(),     // orderId -> { root, elapsedEl, elapsedText, nextBtn, cancelBtn, status, placedAtMs, busy }
  hasRendered: false,   // suppresses the arrival alert for the first batch
  pollTimer: null,
  pollInFlight: false,
  offline: false,
  roleWarned: false,
  elapsedTimer: null,
  audioCtx: null,
  audioUnlocked: false,
  soundOn: true,
  lastUpdatedMs: null,  // timestamp of the last successful poll ("Updated Xs ago")
  oldest: null,         // { id, placedAtMs } — the order waiting longest
  activeColumn: 'PLACED',
  confirmResolve: null,
  confirmLastFocus: null
};

var columnCards = new Map();      // status -> scrolling card container
var columnCounts = new Map();     // status -> count badge
var columnEls = new Map();        // status -> column <section>
var columnSkeletons = new Map();  // status -> skeleton stack (removed after first paint)
var columnEmpties = new Map();    // status -> empty-state element
var summaryCounts = new Map();    // status -> summary strip count
var tabCounts = new Map();        // status -> status-tab count

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
  if (liveStatus) liveStatus.classList.toggle('is-offline', value);
  updateLiveStatus(Date.now());
}

/** Repaints the live dot caption: "Updated Xs ago", or "Reconnecting…" while offline. */
function updateLiveStatus(nowMs) {
  if (!liveText) return;
  var text;
  if (state.offline) {
    text = 'Reconnecting\u2026';
  } else if (state.lastUpdatedMs == null) {
    text = 'Connecting\u2026';
  } else {
    var secs = Math.max(0, Math.round((nowMs - state.lastUpdatedMs) / 1000));
    text = secs < 2 ? 'Updated just now' : 'Updated ' + secs + 's ago';
  }
  if (liveText.textContent !== text) liveText.textContent = text;
}

/* ------------------------------- Elapsed time ----------------------------- */

/** Compact durations: "12m", "1h 05m", "1d 14h". Never "2325m 31s". */
function formatElapsed(totalSeconds) {
  var seconds = Math.max(0, Math.floor(totalSeconds));
  var minutes = Math.floor(seconds / 60);
  if (minutes < 60) return minutes + 'm';
  var hours = Math.floor(minutes / 60);
  if (hours < 24) return hours + 'h ' + String(minutes % 60).padStart(2, '0') + 'm';
  var days = Math.floor(hours / 24);
  return days + 'd ' + String(hours % 24).padStart(2, '0') + 'h';
}

function updateElapsed(card, nowMs) {
  var seconds = Math.max(0, Math.floor((nowMs - card.placedAtMs) / 1000));
  card.elapsedText.textContent = formatElapsed(seconds);
  card.elapsedEl.classList.toggle('is-warn', seconds >= WARN_SECONDS && seconds < DANGER_SECONDS);
  card.elapsedEl.classList.toggle('is-danger', seconds >= DANGER_SECONDS);
  card.elapsedEl.setAttribute('aria-label', 'Waiting ' + formatElapsed(seconds));
}

function tickElapsed() {
  var nowMs = Date.now();
  state.cards.forEach(function (card) { updateElapsed(card, nowMs); });
  updateOldest();
  updateLiveStatus(nowMs);
}

function startElapsedTicker() {
  if (state.elapsedTimer) return;
  state.elapsedTimer = setInterval(tickElapsed, 1000);
}

/** Summary strip: the ticket that has waited the longest, with its age. */
function updateOldest() {
  if (!oldestOrderEl) return;
  var nowMs = Date.now();
  var oldestId = null;
  var oldestMs = 0;
  state.cards.forEach(function (card) {
    if (oldestId === null || card.placedAtMs < oldestMs) {
      oldestId = card.root.dataset.orderId;
      oldestMs = card.placedAtMs;
    }
  });
  state.oldest = oldestId === null ? null : { id: oldestId, placedAtMs: oldestMs };
  oldestOrderEl.textContent = oldestId === null
    ? '\u2014'
    : '#' + oldestId + ' \u00b7 ' + formatElapsed((nowMs - oldestMs) / 1000);
}

/* --------------------------------- Cards ---------------------------------- */

function createCard(ticket) {
  var root = document.createElement('article');
  root.className = 'card';
  root.dataset.orderId = String(ticket.orderId);
  root.innerHTML =
    '<div class="card-top">' +
      '<span class="order-no">#' + escapeHtml(ticket.orderId) + '</span>' +
      '<span class="table-chip">Table ' + escapeHtml(ticket.tableNumber || '\u2014') + '</span>' +
    '</div>' +
    '<span class="elapsed-pill">' +
      '<svg class="elapsed-icon" viewBox="0 0 24 24" width="15" height="15" fill="none" ' +
        'stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<circle cx="12" cy="12" r="9"></circle><path d="M12 7v5l3 2"></path>' +
      '</svg>' +
      '<span class="elapsed-text"></span>' +
    '</span>' +
    '<ul class="items"></ul>' +
    '<div class="card-actions">' +
      '<button type="button" class="btn btn-next next-btn"></button>' +
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
    var block = document.createElement('p');
    block.className = 'card-note';
    block.innerHTML =
      '<svg class="card-note__icon" viewBox="0 0 24 24" width="16" height="16" fill="none" ' +
        'stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M12 9v4"></path><path d="M12 17h.01"></path>' +
        '<path d="M10.3 3.9 2.6 17a2 2 0 0 0 1.7 3h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"></path>' +
      '</svg>' +
      '<span class="card-note__body">' +
        '<span class="card-note__label">Guest note</span>' +
        '<span class="card-note__text"></span>' +
      '</span>';
    block.querySelector('.card-note__text').textContent = ticket.specialNotes;
    root.querySelector('.card-actions').before(block);
  }

  // Prefer the ISO timestamp so the clock can tick client-side; fall back to
  // the contract's server-computed elapsedMinutes if the timestamp is unusable.
  var placedAtMs = Date.parse(ticket.orderPlacedAt);
  if (isNaN(placedAtMs)) {
    placedAtMs = Date.now() - (Number(ticket.elapsedMinutes) || 0) * 60000;
  }

  var card = {
    root: root,
    elapsedEl: root.querySelector('.elapsed-pill'),
    elapsedText: root.querySelector('.elapsed-text'),
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
  card.root.dataset.status = status;
  var next = NEXT_STATUS[status];
  if (next) {
    card.nextBtn.textContent = NEXT_LABEL[next] || next;
    card.nextBtn.dataset.to = next;
    card.nextBtn.hidden = false;
  } else {
    card.nextBtn.hidden = true;
  }
  card.cancelBtn.hidden = !CANCELLABLE.has(status);
}

/* ------------------------------- Board render ----------------------------- */

function buildColumns() {
  document.querySelectorAll('.summary-count[data-count]').forEach(function (el) {
    summaryCounts.set(el.dataset.count, el);
  });

  COLUMNS.forEach(function (status) {
    var column = document.createElement('section');
    column.className = 'column';
    column.dataset.status = status;

    var head = document.createElement('header');
    head.className = 'column-head';

    var name = document.createElement('h2');
    name.className = 'column-name';
    name.textContent = STATUS_LABEL[status] || status;

    var count = document.createElement('span');
    count.className = 'column-count';
    count.textContent = '0';
    count.setAttribute('aria-label', 'orders in this column');

    head.append(name, count);

    var body = document.createElement('div');
    body.className = 'column-body';

    // First-paint placeholders; removed as soon as real data arrives.
    var skeleton = document.createElement('div');
    skeleton.className = 'skeleton-stack';
    skeleton.setAttribute('aria-hidden', 'true');
    for (var i = 0; i < 3; i++) {
      var sk = document.createElement('div');
      sk.className = 'skeleton';
      sk.innerHTML =
        '<span class="skeleton-line skeleton-line--title"></span>' +
        '<span class="skeleton-line"></span>' +
        '<span class="skeleton-line skeleton-line--short"></span>';
      skeleton.append(sk);
    }

    var empty = document.createElement('p');
    empty.className = 'column-empty';
    empty.textContent = EMPTY_TEXT[status] || 'No orders';
    empty.hidden = true;

    var cards = document.createElement('div');
    cards.className = 'cards';

    body.append(skeleton, empty, cards);
    column.append(head, body);
    boardEl.append(column);

    columnEls.set(status, column);
    columnCards.set(status, cards);
    columnCounts.set(status, count);
    columnSkeletons.set(status, skeleton);
    columnEmpties.set(status, empty);
  });

  buildTabs();
}

/** Mobile-only tab bar: switches which single column is visible below 700px. */
function buildTabs() {
  if (!statusTabs) return;

  COLUMNS.forEach(function (status) {
    var tab = document.createElement('button');
    tab.type = 'button';
    tab.className = 'status-tab';
    tab.dataset.status = status;

    var label = document.createElement('span');
    label.textContent = STATUS_LABEL[status] || status;

    var count = document.createElement('b');
    count.className = 'status-tab-count';
    count.textContent = '0';

    tab.append(label, count);
    tab.addEventListener('click', function () { setActiveColumn(status); });
    statusTabs.append(tab);
    tabCounts.set(status, count);
  });

  setActiveColumn(state.activeColumn);
}

function setActiveColumn(status) {
  state.activeColumn = status;
  columnEls.forEach(function (column, key) {
    column.classList.toggle('is-active', key === status);
  });
  if (!statusTabs) return;
  statusTabs.querySelectorAll('.status-tab').forEach(function (tab) {
    tab.setAttribute('aria-pressed', tab.dataset.status === status ? 'true' : 'false');
  });
}

/** Polite screen-reader announcement for genuinely new tickets. */
function announceArrivals(arrivals) {
  if (!boardLive) return;
  var message = arrivals.map(function (arrival) {
    return 'New order #' + arrival.id + (arrival.table ? ', Table ' + arrival.table : '');
  }).join('. ');
  boardLive.textContent = message;
  setTimeout(function () {
    if (boardLive.textContent === message) boardLive.textContent = '';
  }, 4000);
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
        arrivals.push({ root: card.root, id: ticket.orderId, table: ticket.tableNumber });
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
    if (summaryCounts.has(status)) summaryCounts.get(status).textContent = String(rows.length);
    if (tabCounts.has(status)) tabCounts.get(status).textContent = String(rows.length);
  });

  var firstPaint = !state.hasRendered;
  state.hasRendered = true;
  state.lastUpdatedMs = Date.now();

  if (firstPaint) {
    columnSkeletons.forEach(function (skeleton) { skeleton.remove(); });
    columnSkeletons.clear();
  }

  COLUMNS.forEach(function (status) {
    var empty = columnEmpties.get(status);
    if (empty) empty.hidden = byColumn.get(status).length > 0;
  });

  updateOldest();
  updateLiveStatus(state.lastUpdatedMs);

  if (arrivals.length > 0) {
    playChime();
    announceArrivals(arrivals);
    setTimeout(function () {
      arrivals.forEach(function (arrival) { arrival.root.classList.remove('is-new'); });
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

  if (kind === 'cancel') {
    var confirmed = await confirmCancel(orderId);
    if (!confirmed) return;
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

/* --------------------------- Confirm dialog ------------------------------- */

/** Opens the modal and resolves true/false. Replaces window.confirm. */
function confirmCancel(orderId) {
  return new Promise(function (resolve) {
    state.confirmResolve = resolve;
    state.confirmLastFocus = document.activeElement;
    confirmText.textContent = 'Order #' + orderId + ' will be cancelled. This cannot be undone.';
    confirmOverlay.hidden = false;
    confirmCancel.focus();
    document.addEventListener('keydown', onConfirmKeydown);
    confirmOverlay.addEventListener('click', onConfirmBackdrop);
  });
}

function closeConfirm(result) {
  if (!state.confirmResolve) return;
  var resolve = state.confirmResolve;
  state.confirmResolve = null;
  confirmOverlay.hidden = true;
  document.removeEventListener('keydown', onConfirmKeydown);
  confirmOverlay.removeEventListener('click', onConfirmBackdrop);
  if (state.confirmLastFocus && typeof state.confirmLastFocus.focus === 'function') {
    state.confirmLastFocus.focus();
  }
  state.confirmLastFocus = null;
  resolve(result);
}

/** Traps Tab between the two dialog buttons and closes on Escape. */
function onConfirmKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault();
    closeConfirm(false);
    return;
  }
  if (event.key !== 'Tab') return;
  var focusables = [confirmKeep, confirmCancel];
  var first = focusables[0];
  var last = focusables[focusables.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

function onConfirmBackdrop(event) {
  if (event.target === confirmOverlay) closeConfirm(false);
}

/* --------------------------------- Audio ---------------------------------- */

function updateSoundButton() {
  var on = state.audioUnlocked && state.soundOn;
  soundToggle.classList.toggle('is-on', on);
  soundToggle.setAttribute('aria-checked', on ? 'true' : 'false');
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
  state.lastUpdatedMs = null;
  state.oldest = null;
  setOffline(false);
  columnCounts.forEach(function (el) { el.textContent = '0'; });
  summaryCounts.forEach(function (el) { el.textContent = '0'; });
  tabCounts.forEach(function (el) { el.textContent = '0'; });
  columnEmpties.forEach(function (el) { el.hidden = true; });
  if (oldestOrderEl) oldestOrderEl.textContent = '\u2014';
  updateLiveStatus(Date.now());
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

  // Custom cancel dialog: buttons resolve the pending moveOrder promise.
  confirmKeep.addEventListener('click', function () { closeConfirm(false); });
  confirmCancel.addEventListener('click', function () { closeConfirm(true); });

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
