'use strict';

/* ==========================================================================
   Saffron & Sage — customer table-ordering UI.
   Vanilla ES2020. Talks only to the five customer endpoints documented in
   docs/openapi.yaml (table by token, menu, place order, order by id, table
   order history). No frameworks, no build step, no external hosts.
   ========================================================================== */

var POLL_MS = 4000;      // tracking poll interval (chained setTimeout, never setInterval)
var MAX_QTY = 20;        // mirrors @Max(20) on CreateOrderRequest.items[].quantity
var NOTES_MAX = 255;     // mirrors @Size(max = 255) on CreateOrderRequest.specialNotes

var STATUS_FLOW = ['PLACED', 'ACCEPTED', 'PREPARING', 'READY', 'SERVED'];
var STATUS_LABEL = {
  PLACED: 'Order placed',
  ACCEPTED: 'Accepted by kitchen',
  PREPARING: 'Being prepared',
  READY: 'Ready to serve',
  SERVED: 'Served',
  CANCELLED: 'Cancelled'
};

/* MenuItem (docs/openapi.yaml) has NO vegetarian field, so the Veg / Non-veg pill is
   an honest heuristic: drinks and desserts are shown as Veg; a FOOD item is shown as
   Non-veg only when its name or description matches a meat keyword; every other FOOD
   item gets the neutral "Food" pill instead of claiming it is veg. */
var MEAT_KEYWORDS = ['chicken', 'mutton', 'lamb', 'fish', 'prawn', 'egg', 'kebab', 'biryani', 'tikka', 'rogan'];

/* ---------- State ---------- */
var state = {
  screen: 'loading',
  token: '',
  table: null,
  categories: [],          // MenuCategoryWithItems[]
  itemsById: new Map(),    // itemId -> MenuItem
  unavailableIds: new Set(), // items we learned are 86'd, kept visible so we can flag them
  cart: new Map(),         // itemId -> quantity
  category: 'all',
  search: '',
  notes: '',
  order: null,             // latest OrderResponse
  placing: false,
  pollActive: false,
  pollTimer: null,
  bannerRetry: null,
  bannerKind: null,
  fatalRetry: null,
  lastFocus: null
};

/* ---------- DOM ---------- */
var dom = {
  tableLabel: document.getElementById('table-label'),
  banner: document.getElementById('global-banner'),
  bannerText: document.getElementById('global-banner-text'),
  bannerRetry: document.getElementById('global-banner-retry'),
  fatalTitle: document.getElementById('fatal-title'),
  fatalText: document.getElementById('fatal-text'),
  fatalRetry: document.getElementById('fatal-retry'),
  search: document.getElementById('menu-search'),
  tabs: document.getElementById('menu-tabs'),
  menuList: document.getElementById('menu-list'),
  cartBar: document.getElementById('cart-bar'),
  cartBarOpen: document.getElementById('cart-bar-open'),
  cartCount: document.getElementById('cart-count'),
  cartTotal: document.getElementById('cart-total'),
  cartScrim: document.getElementById('cart-scrim'),
  cartDrawer: document.getElementById('cart-drawer'),
  cartClose: document.getElementById('cart-close'),
  cartError: document.getElementById('cart-error'),
  cartErrorText: document.getElementById('cart-error-text'),
  cartErrorRetry: document.getElementById('cart-error-retry'),
  cartEmpty: document.getElementById('cart-empty'),
  cartLines: document.getElementById('cart-lines'),
  notes: document.getElementById('special-notes'),
  notesCount: document.getElementById('notes-count'),
  cartFootTotal: document.getElementById('cart-foot-total'),
  placeOrder: document.getElementById('place-order'),
  trackTable: document.getElementById('track-table'),
  trackTitle: document.getElementById('track-title'),
  trackSubtitle: document.getElementById('track-subtitle'),
  trackCancelled: document.getElementById('track-cancelled'),
  trackStepper: document.getElementById('track-stepper'),
  trackItems: document.getElementById('track-items'),
  trackTotal: document.getElementById('track-total'),
  trackNotes: document.getElementById('track-notes'),
  orderMore: document.getElementById('btn-order-more'),
  requestBill: document.getElementById('btn-request-bill'),
  billScrim: document.getElementById('bill-scrim'),
  billPanel: document.getElementById('bill-panel'),
  billClose: document.getElementById('bill-close'),
  billLines: document.getElementById('bill-lines'),
  billTotals: document.getElementById('bill-totals'),
  billSubtotal: document.getElementById('bill-subtotal'),
  billGst: document.getElementById('bill-gst'),
  billGrand: document.getElementById('bill-grand'),
  billStatus: document.getElementById('bill-status')
};

/* ---------- Small helpers ---------- */

/** Escapes a value for safe interpolation into innerHTML. */
function escapeHtml(value) {
  return String(value == null ? '' : value).replace(/[&<>"']/g, function (ch) {
    return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
  });
}

/** Formats money with two decimals and the rupee symbol (JSON numbers in, text out). */
function money(value) {
  var n = Number(value);
  if (!isFinite(n)) n = 0;
  return '\u20B9' + n.toFixed(2);
}

function round2(n) {
  return Math.round(n * 100) / 100;
}

/** "T05" -> "Table 5"; "5" -> "Table 5"; anything else is shown as-is. */
function formatTableLabel(tableNumber) {
  var raw = String(tableNumber == null ? '' : tableNumber).trim();
  var match = /^T?\s*0*(\d+)$/i.exec(raw);
  return match ? 'Table ' + match[1] : raw;
}

function spiceLabel(level) {
  var map = { MILD: 'Mild', MEDIUM: 'Medium', HOT: 'Hot', EXTRA_HOT: 'Extra hot' };
  return map[level] || String(level).replace(/_/g, ' ').toLowerCase();
}

/** Veg / Non-veg / neutral pill — see MEAT_KEYWORDS note above. */
function dietaryPill(item) {
  if (item.itemType === 'BEVERAGE' || item.itemType === 'DESSERT') return { label: 'Veg', tone: 'veg' };
  var text = ((item.name || '') + ' ' + (item.description || '')).toLowerCase();
  if (item.itemType === 'FOOD' && MEAT_KEYWORDS.some(function (k) { return text.indexOf(k) !== -1; })) {
    return { label: 'Non-veg', tone: 'nonveg' };
  }
  return { label: 'Food', tone: 'neutral' };
}

function isItemAvailable(item) {
  return item.isAvailable !== false && !state.unavailableIds.has(item.itemId);
}

/* ---------- HTTP ---------- */

/** Thin fetch wrapper: same-origin credentials, JSON in/out, typed errors. */
function api(path, options) {
  var opts = options || {};
  var init = { method: opts.method || 'GET', credentials: 'same-origin' };
  if (opts.body !== undefined) {
    init.headers = { 'Content-Type': 'application/json' };
    init.body = JSON.stringify(opts.body);
  }

  return fetch(path, init).then(function (res) {
    return res.text().then(function (text) {
      var data = null;
      if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }
      if (!res.ok) {
        var err = new Error((data && data.message) || res.statusText || 'Request failed');
        err.status = res.status;
        err.body = data;
        throw err;
      }
      return data;
    });
  }, function () {
    var netErr = new Error('network');
    netErr.network = true;   // distinct from an HTTP error so we can show the retry banner
    throw netErr;
  });
}

/* ---------- Screens, banner, overlays ---------- */

function showScreen(name) {
  state.screen = name;
  document.body.setAttribute('data-screen', name);
  var screens = document.querySelectorAll('.screen');
  for (var i = 0; i < screens.length; i++) {
    screens[i].hidden = screens[i].id !== 'screen-' + name;
  }
  window.scrollTo({ top: 0, behavior: 'auto' });
  renderCartBar();
}

function showFatal(title, text, retry) {
  dom.fatalTitle.textContent = title;
  dom.fatalText.textContent = text;
  state.fatalRetry = retry || null;
  dom.fatalRetry.hidden = !retry;
  showScreen('error');
}

function showBanner(message, retryAction, kind) {
  dom.bannerText.textContent = message;
  state.bannerRetry = retryAction || null;
  state.bannerKind = kind || null;
  dom.bannerRetry.hidden = !retryAction;
  dom.banner.hidden = false;
}

function hideBanner() {
  dom.banner.hidden = true;
  dom.bannerText.textContent = '';
  dom.bannerRetry.hidden = true;
  state.bannerRetry = null;
  state.bannerKind = null;
}

function openOverlay(drawer, scrim) {
  state.lastFocus = document.activeElement;
  drawer.classList.add('is-open');
  drawer.setAttribute('aria-hidden', 'false');
  scrim.classList.add('is-open');
  var first = drawer.querySelector('.icon-btn');
  if (first) first.focus();
}

function closeOverlay(drawer, scrim) {
  drawer.classList.remove('is-open');
  drawer.setAttribute('aria-hidden', 'true');
  scrim.classList.remove('is-open');
  if (state.lastFocus && typeof state.lastFocus.focus === 'function') state.lastFocus.focus();
  state.lastFocus = null;
}

function openCart() { openOverlay(dom.cartDrawer, dom.cartScrim); }
function closeCart() { closeOverlay(dom.cartDrawer, dom.cartScrim); }
function openBillPanel() { openOverlay(dom.billPanel, dom.billScrim); }
function closeBillPanel() { closeOverlay(dom.billPanel, dom.billScrim); }

function showCartError(message, canRetry) {
  dom.cartErrorText.textContent = message;
  dom.cartErrorRetry.hidden = !canRetry;
  dom.cartError.hidden = false;
}

function clearCartError() {
  dom.cartError.hidden = true;
  dom.cartErrorText.textContent = '';
  dom.cartErrorRetry.hidden = true;
}

/* ---------- Boot ---------- */

function boot() {
  hideBanner();
  showScreen('loading');
  api('/api/tables/' + encodeURIComponent(state.token)).then(function (table) {
    state.table = table;
    dom.tableLabel.textContent = formatTableLabel(table.tableNumber);
    restoreCart();
    return loadMenu();
  }).then(function () {
    state.notes = '';
    dom.notes.value = '';
    updateNotesCounter();
    showScreen('menu');
  }).catch(function (err) {
    if (err.network) {
      showFatal('We could not reach the restaurant', 'Please check your connection and try again.', boot);
    } else if (err.status === 404) {
      // Unknown / invalid token: show the same friendly scan prompt, with no menu.
      showScreen('scan');
    } else {
      showFatal('Something went wrong', err.message || 'Please try again in a moment.', boot);
    }
  });
}

/* ---------- Menu ---------- */

function loadMenu() {
  var previous = state.categories;
  return api('/api/menu').then(function (data) {
    var list = Array.isArray(data) ? data : [];
    state.categories = list.map(function (c) {
      return {
        categoryId: c.categoryId,
        name: c.name,
        description: c.description,
        displayOrder: c.displayOrder,
        items: Array.isArray(c.items) ? c.items : []
      };
    }).sort(function (a, b) { return (a.displayOrder || 0) - (b.displayOrder || 0); });

    state.itemsById = new Map();
    state.categories.forEach(function (c) {
      c.items.forEach(function (i) { state.itemsById.set(i.itemId, i); });
    });

    reattachUnavailable(previous);
    renderTabs();
    renderMenuList();
    updateCartUI();
  });
}

/** Keeps an item visible after the server drops it from the menu because it went 86'd. */
function reattachUnavailable(previous) {
  if (!previous || state.unavailableIds.size === 0) return;
  previous.forEach(function (cat) {
    cat.items.forEach(function (item) {
      if (!state.unavailableIds.has(item.itemId) || state.itemsById.has(item.itemId)) return;
      var target = null;
      for (var i = 0; i < state.categories.length; i++) {
        if (state.categories[i].categoryId === cat.categoryId) { target = state.categories[i]; break; }
      }
      if (!target) {
        target = {
          categoryId: cat.categoryId, name: cat.name, description: cat.description,
          displayOrder: cat.displayOrder, items: []
        };
        state.categories.push(target);
      }
      target.items.push(item);
      state.itemsById.set(item.itemId, item);
    });
  });
}

function renderTabs() {
  var tabs = [{ id: 'all', name: 'All' }].concat(state.categories.map(function (c) {
    return { id: String(c.categoryId), name: c.name };
  }));
  dom.tabs.innerHTML = tabs.map(function (t) {
    var selected = state.category === t.id;
    return '<button class="tab" type="button" role="tab" data-category="' + escapeHtml(t.id) +
      '" aria-selected="' + selected + '" tabindex="' + (selected ? '0' : '-1') + '">' +
      escapeHtml(t.name) + '</button>';
  }).join('');
}

function matchesSearch(item, query) {
  if (!query) return true;
  return String(item.name || '').toLowerCase().indexOf(query) !== -1 ||
    String(item.description || '').toLowerCase().indexOf(query) !== -1;
}

function renderMenuList() {
  var query = state.search.trim().toLowerCase();
  var groups = state.categories
    .filter(function (c) { return state.category === 'all' || String(c.categoryId) === state.category; })
    .map(function (c) {
      return { category: c, items: c.items.filter(function (i) { return matchesSearch(i, query); }) };
    });

  var count = groups.reduce(function (n, g) { return n + g.items.length; }, 0);
  if (count === 0) {
    dom.menuList.innerHTML = '<p class="empty-note">' +
      (query ? 'No dishes match your search.' : 'No dishes in this category yet.') + '</p>';
    return;
  }

  dom.menuList.innerHTML = groups.filter(function (g) { return g.items.length > 0; }).map(function (g) {
    var desc = g.category.description
      ? '<p class="menu-group__desc">' + escapeHtml(g.category.description) + '</p>' : '';
    return '<section class="menu-group">' +
      '<h2 class="menu-group__title">' + escapeHtml(g.category.name) + '</h2>' + desc +
      '<div class="items">' + g.items.map(renderItemCard).join('') + '</div>' +
      '</section>';
  }).join('');
}

function renderItemCard(item) {
  var available = isItemAvailable(item);
  var pill = dietaryPill(item);
  var chips = '';
  if (item.spiceLevel) chips += '<span class="chip">' + escapeHtml(spiceLabel(item.spiceLevel)) + '</span>';
  if (item.volumeMl) chips += '<span class="chip">' + escapeHtml(item.volumeMl) + ' ml</span>';
  if (!available) chips += '<span class="chip chip--out">Out of stock</span>';

  return '<article class="item' + (available ? '' : ' item--out') + '" data-item-id="' + escapeHtml(item.itemId) + '">' +
    '<div class="item__body">' +
      '<div class="item__headline">' +
        '<span class="pill pill--' + pill.tone + '">' + escapeHtml(pill.label) + '</span>' +
        '<h3 class="item__name">' + escapeHtml(item.name) + '</h3>' +
      '</div>' +
      (item.description ? '<p class="item__desc">' + escapeHtml(item.description) + '</p>' : '') +
      '<div class="item__meta">' +
        '<span class="item__price">' + money(item.price) + '</span>' + chips +
      '</div>' +
    '</div>' +
    '<div class="item__action">' + renderItemAction(item, available) + '</div>' +
    '</article>';
}

function renderItemAction(item, available) {
  if (!available) return '';
  var name = escapeHtml(item.name);
  var qty = state.cart.get(item.itemId) || 0;
  if (qty === 0) {
    return '<button class="btn btn--primary btn--sm" type="button" data-action="add" ' +
      'data-item-id="' + escapeHtml(item.itemId) + '" aria-label="Add ' + name + ' to cart">Add</button>';
  }
  return '<div class="stepper">' +
    '<button class="stepper__btn" type="button" data-action="dec" data-item-id="' + escapeHtml(item.itemId) +
      '" aria-label="Remove one ' + name + '">\u2212</button>' +
    '<span class="stepper__qty" aria-live="polite">' + qty + '</span>' +
    '<button class="stepper__btn" type="button" data-action="inc" data-item-id="' + escapeHtml(item.itemId) +
      '" aria-label="Add one more ' + name + '"' + (qty >= MAX_QTY ? ' disabled' : '') + '>+</button>' +
    '</div>';
}

/** Repaints just the action area of one card so quantities update without losing scroll. */
function refreshItemAction(itemId) {
  var card = dom.menuList.querySelector('.item[data-item-id="' + itemId + '"]');
  if (!card) return;
  var item = state.itemsById.get(itemId);
  var action = card.querySelector('.item__action');
  if (item && action) action.innerHTML = renderItemAction(item, isItemAvailable(item));
}

/* ---------- Cart ---------- */

function cartKey() { return 'cart:' + state.token; }

function persistCart() {
  try {
    sessionStorage.setItem(cartKey(), JSON.stringify(Array.from(state.cart.entries())));
  } catch (e) {
    // sessionStorage unavailable (private mode / quota) — cart simply stays in memory.
  }
}

function clearPersistedCart() {
  try { sessionStorage.removeItem(cartKey()); } catch (e) { /* nothing to clear */ }
}

function restoreCart() {
  state.cart.clear();
  var raw = null;
  try { raw = sessionStorage.getItem(cartKey()); } catch (e) { raw = null; }
  if (!raw) return;
  var parsed;
  try { parsed = JSON.parse(raw); } catch (e) { parsed = null; }
  if (!Array.isArray(parsed)) return;
  parsed.forEach(function (entry) {
    var id = Number(entry[0]);
    var qty = Number(entry[1]);
    if (Number.isInteger(id) && qty > 0) state.cart.set(id, Math.min(MAX_QTY, Math.floor(qty)));
  });
}

function cartLines() {
  var lines = [];
  state.cart.forEach(function (qty, itemId) {
    var item = state.itemsById.get(itemId);
    if (item) lines.push({ itemId: itemId, quantity: qty, item: item });
  });
  return lines;
}

function cartCount() {
  var n = 0;
  state.cart.forEach(function (qty) { n += qty; });
  return n;
}

function cartTotal() {
  var total = 0;
  cartLines().forEach(function (l) { total += Number(l.item.price || 0) * l.quantity; });
  return round2(total);
}

function setQuantity(itemId, quantity) {
  var qty = Math.max(0, Math.min(MAX_QTY, quantity));
  if (qty === 0) state.cart.delete(itemId);
  else state.cart.set(itemId, qty);
  persistCart();
  refreshItemAction(itemId);
  updateCartUI();
}

function renderCartBar() {
  var count = cartCount();
  var visible = state.screen === 'menu' && count > 0;
  dom.cartBar.hidden = !visible;
  if (!visible) return;
  dom.cartCount.textContent = count + (count === 1 ? ' item' : ' items');
  dom.cartTotal.textContent = money(cartTotal());
}

function renderCartLines() {
  var lines = cartLines();
  dom.cartEmpty.hidden = lines.length > 0;
  dom.cartLines.innerHTML = lines.map(function (l) {
    var name = escapeHtml(l.item.name);
    var available = isItemAvailable(l.item);
    return '<li class="cart-line' + (available ? '' : ' cart-line--out') + '" data-item-id="' + escapeHtml(l.itemId) + '">' +
      '<div class="cart-line__info">' +
        '<p class="cart-line__name">' + name + '</p>' +
        '<p class="cart-line__meta">' + money(l.item.price) + ' each</p>' +
        (available ? '' : '<p class="cart-line__warn">No longer available — please remove</p>') +
      '</div>' +
      '<p class="cart-line__subtotal">' + money(Number(l.item.price || 0) * l.quantity) + '</p>' +
      '<div class="cart-line__actions">' +
        '<div class="stepper">' +
          '<button class="stepper__btn" type="button" data-action="dec" data-item-id="' + escapeHtml(l.itemId) +
            '" aria-label="Remove one ' + name + '">\u2212</button>' +
          '<span class="stepper__qty" aria-live="polite">' + l.quantity + '</span>' +
          '<button class="stepper__btn" type="button" data-action="inc" data-item-id="' + escapeHtml(l.itemId) +
            '" aria-label="Add one more ' + name + '"' + (l.quantity >= MAX_QTY ? ' disabled' : '') + '>+</button>' +
        '</div>' +
        '<button class="icon-btn" type="button" data-action="remove" data-item-id="' + escapeHtml(l.itemId) +
          '" aria-label="Remove ' + name + ' from cart">\u2715</button>' +
      '</div>' +
      '</li>';
  }).join('');
}

function updateNotesCounter() {
  dom.notesCount.textContent = String(dom.notes.value.length);
}

function updateCartUI() {
  var total = money(cartTotal());
  dom.cartTotal.textContent = total;
  dom.cartFootTotal.textContent = total;
  renderCartLines();
  renderCartBar();
}

/* ---------- Placing an order ---------- */

function buildOrderPayload(lines) {
  var body = {
    tableToken: state.token,
    items: lines.map(function (l) { return { itemId: l.itemId, quantity: l.quantity }; })
  };
  // The contract names this field "specialNotes" (CreateOrderRequest in docs/openapi.yaml).
  var notes = (state.notes || '').trim().slice(0, NOTES_MAX);
  if (notes) body.specialNotes = notes;
  var session = state.table && state.table.session;
  if (session && session.sessionToken) body.sessionToken = session.sessionToken;
  return body;
}

function setBusy(busy) {
  dom.placeOrder.disabled = busy;
  dom.cartDrawer.setAttribute('aria-busy', busy ? 'true' : 'false');
  dom.placeOrder.textContent = busy ? 'Placing…' : 'Place Order';
}

function placeOrder() {
  if (state.placing) return;   // a second tap while in flight is ignored
  clearCartError();

  var lines = cartLines();
  if (lines.length === 0) {
    showCartError('Your cart is empty.', false);
    return;
  }

  state.placing = true;
  setBusy(true);

  api('/api/orders', { method: 'POST', body: buildOrderPayload(lines) }).then(function (order) {
    state.cart.clear();
    state.notes = '';
    dom.notes.value = '';
    updateNotesCounter();
    clearPersistedCart();
    updateCartUI();

    state.order = order;
    closeCart();
    showScreen('tracking');
    renderTracking(order);
    startPolling();
  }).catch(function (err) {
    handlePlaceError(err);
  }).then(function () {
    state.placing = false;
    setBusy(false);
  });
}

function handlePlaceError(err) {
  if (err.network) {
    showCartError('We could not reach the kitchen — check your connection and try again.', true);
    return;
  }
  if (err.status === 400) {
    // Usually a sold-out line: surface the server message and flag the item on the menu.
    showCartError(err.message, true);
    markUnavailableFromMessage(err.message);
    reloadMenuAfterUnavailable();
    return;
  }
  if (err.status === 409) {
    showCartError(err.message || 'This order conflicts with a change on our side.', false);
    handleConflict(err.message);
    return;
  }
  showCartError(err.message || 'We could not place your order. Please try again.', true);
}

/** Extracts the offending item from the server message, e.g. "Menu item 'X' (ID: 4)…". */
function markUnavailableFromMessage(message) {
  var text = String(message || '');
  var itemId = null;
  var idMatch = /\(ID:\s*(\d+)\)/i.exec(text);
  if (idMatch) {
    itemId = Number(idMatch[1]);
  } else {
    var nameMatch = /'([^']+)'/.exec(text);
    if (nameMatch) {
      var wanted = nameMatch[1].trim().toLowerCase();
      state.itemsById.forEach(function (item, id) {
        if (itemId === null && String(item.name || '').trim().toLowerCase() === wanted) itemId = id;
      });
    }
  }
  if (itemId !== null) state.unavailableIds.add(itemId);
  return itemId;
}

function reloadMenuAfterUnavailable() {
  loadMenu().catch(function () {
    showBanner('We could not refresh the menu.', reloadMenuAfterUnavailable, 'menu');
  });
}

/** Order state changed server-side: refresh what we are tracking. */
function handleConflict(message) {
  showBanner(message || 'This order changed on our side — showing the latest state.', null, null);
  if (state.order && state.order.orderId != null) refreshOrder(state.order.orderId);
}

function refreshOrder(orderId) {
  api('/api/orders/' + encodeURIComponent(orderId)).then(function (order) {
    state.order = order;
    if (state.screen === 'tracking') renderTracking(order);
    if (order.status === 'SERVED' || order.status === 'CANCELLED') stopPolling();
  }).catch(function () {
    // Leave the current view untouched; the next poll will try again.
  });
}

/* ---------- Tracking + polling ---------- */

function renderTracking(order) {
  dom.trackTable.textContent = formatTableLabel(state.table && state.table.tableNumber);
  dom.trackTitle.textContent = 'Order #' + order.orderId;
  dom.trackSubtitle.textContent = order.status === 'CANCELLED'
    ? 'This order was cancelled.'
    : 'This page updates automatically — no need to refresh.';

  dom.trackCancelled.hidden = order.status !== 'CANCELLED';
  dom.trackStepper.innerHTML = renderProgressStepper(order.status);

  dom.trackItems.innerHTML = (order.items || []).map(function (it) {
    return '<li class="order-line">' +
      '<span class="order-line__qty">' + escapeHtml(it.quantity) + '\u00D7</span>' +
      '<span class="order-line__name">' + escapeHtml(it.name) + '</span>' +
      '<span class="order-line__sub">' + money(it.subtotal) + '</span>' +
      '</li>';
  }).join('');

  dom.trackTotal.textContent = money(order.totalAmount);

  if (order.specialNotes) {
    dom.trackNotes.textContent = 'Notes: ' + order.specialNotes;
    dom.trackNotes.hidden = false;
  } else {
    dom.trackNotes.hidden = true;
    dom.trackNotes.textContent = '';
  }
}

function renderProgressStepper(status) {
  var cancelled = status === 'CANCELLED';
  var current = STATUS_FLOW.indexOf(status);
  return STATUS_FLOW.map(function (step, i) {
    var cls = 'step';
    if (!cancelled && i < current) cls += ' step--done';
    else if (!cancelled && i === current) cls += ' step--current';
    return '<li class="' + cls + '"' + (i === current && !cancelled ? ' aria-current="step"' : '') + '>' +
      '<span class="step__dot" aria-hidden="true"></span>' +
      '<span class="step__label">' + escapeHtml(STATUS_LABEL[step]) + '</span>' +
      '</li>';
  }).join('');
}

function schedulePoll(delay) {
  clearPollTimer();
  if (!state.pollActive) return;
  state.pollTimer = window.setTimeout(pollOnce, delay);
}

function clearPollTimer() {
  if (state.pollTimer) {
    window.clearTimeout(state.pollTimer);
    state.pollTimer = null;
  }
}

function startPolling() {
  stopPolling();
  if (!state.order) return;
  if (state.order.status === 'SERVED' || state.order.status === 'CANCELLED') return;
  state.pollActive = true;
  schedulePoll(POLL_MS);
}

function stopPolling() {
  state.pollActive = false;
  clearPollTimer();
}

function pollOnce() {
  state.pollTimer = null;
  if (!state.pollActive) return;
  if (document.hidden) return;   // paused while hidden; visibilitychange resumes

  var orderId = state.order && state.order.orderId;
  if (orderId == null) return;

  api('/api/orders/' + encodeURIComponent(orderId)).then(function (order) {
    state.order = order;
    renderTracking(order);
    if (state.bannerKind === 'poll') hideBanner();
    if (order.status === 'SERVED' || order.status === 'CANCELLED') {
      stopPolling();
      return;
    }
    schedulePoll(POLL_MS);
  }).catch(function (err) {
    if (err.status === 404) {
      stopPolling();
      showBanner('This order could not be found. Please speak to a member of staff.', null, null);
      return;
    }
    if (err.status === 409) handleConflict(err.message);
    else showBanner('We are having trouble updating your order — retrying.', function () { schedulePoll(0); }, 'poll');
    schedulePoll(POLL_MS);
  });
}

/* ---------- Bill preview ---------- */
/* UI-only preview: the real bill (POST /api/sessions/{id}/bill) and payment
   (POST /api/sessions/{id}/pay) endpoints are protected server-side by a staff-only
   interceptor (WAITER / MANAGER), so this customer page must never call them.
   We simply sum this table's own orders and apply the same 5% GST the server uses,
   purely for display. The figures here are informational — staff issue the real bill. */

function openBill() {
  openBillPanel();
  dom.billLines.innerHTML = '';
  dom.billTotals.hidden = true;
  dom.billStatus.hidden = false;
  dom.billStatus.textContent = 'Loading your orders…';

  api('/api/tables/' + encodeURIComponent(state.token) + '/orders').then(function (orders) {
    renderBillPreview(Array.isArray(orders) ? orders : []);
  }).catch(function (err) {
    dom.billStatus.hidden = false;
    dom.billStatus.textContent = err.network
      ? 'We could not load your orders. Please try again in a moment.'
      : (err.message || 'We could not load your orders.');
  });
}

function renderBillPreview(orders) {
  var active = orders.filter(function (o) { return o.status !== 'CANCELLED'; });
  var subtotal = round2(active.reduce(function (sum, o) { return sum + Number(o.totalAmount || 0); }, 0));
  var gst = round2(subtotal * 0.05);

  dom.billLines.innerHTML = active.map(function (o) {
    return '<li class="bill-line">' +
      '<span class="bill-line__name">Order #' + escapeHtml(o.orderId) + '</span>' +
      '<span class="bill-line__status">' + escapeHtml(STATUS_LABEL[o.status] || o.status || '') + '</span>' +
      '<span class="bill-line__amount">' + money(o.totalAmount) + '</span>' +
      '</li>';
  }).join('');

  dom.billSubtotal.textContent = money(subtotal);
  dom.billGst.textContent = money(gst);
  dom.billGrand.textContent = money(round2(subtotal + gst));
  dom.billTotals.hidden = false;
  dom.billStatus.hidden = active.length > 0;
  dom.billStatus.textContent = active.length > 0 ? '' : 'You have no active orders yet.';
}

/* ---------- Event wiring ---------- */

function selectCategory(categoryId) {
  state.category = String(categoryId);
  renderTabs();
  renderMenuList();
  var selected = dom.tabs.querySelector('.tab[aria-selected="true"]');
  if (selected) selected.focus();
}

function wireEvents() {
  // Menu search (client-side filter on name + description).
  dom.search.addEventListener('input', function () {
    state.search = dom.search.value;
    renderMenuList();
  });

  // Category tabs (click + arrow-key navigation).
  dom.tabs.addEventListener('click', function (e) {
    var tab = e.target.closest('.tab');
    if (tab) selectCategory(tab.getAttribute('data-category'));
  });
  dom.tabs.addEventListener('keydown', function (e) {
    if (['ArrowRight', 'ArrowLeft', 'Home', 'End'].indexOf(e.key) === -1) return;
    var tabs = Array.prototype.slice.call(dom.tabs.querySelectorAll('.tab'));
    var index = tabs.indexOf(document.activeElement);
    if (index === -1) return;
    e.preventDefault();
    if (e.key === 'ArrowRight') index = (index + 1) % tabs.length;
    else if (e.key === 'ArrowLeft') index = (index - 1 + tabs.length) % tabs.length;
    else if (e.key === 'Home') index = 0;
    else index = tabs.length - 1;
    tabs[index].focus();
  });

  // Item quantity controls.
  dom.menuList.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-action]');
    if (!btn) return;
    var itemId = Number(btn.getAttribute('data-item-id'));
    var current = state.cart.get(itemId) || 0;
    if (btn.getAttribute('data-action') === 'dec') setQuantity(itemId, current - 1);
    else setQuantity(itemId, current + 1);
  });

  // Cart bar + drawer.
  dom.cartBarOpen.addEventListener('click', function () {
    clearCartError();
    openCart();
  });
  dom.cartClose.addEventListener('click', closeCart);
  dom.cartScrim.addEventListener('click', closeCart);
  dom.cartLines.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-action]');
    if (!btn) return;
    var itemId = Number(btn.getAttribute('data-item-id'));
    var action = btn.getAttribute('data-action');
    if (action === 'remove') setQuantity(itemId, 0);
    else if (action === 'dec') setQuantity(itemId, (state.cart.get(itemId) || 0) - 1);
    else setQuantity(itemId, (state.cart.get(itemId) || 0) + 1);
  });

  dom.notes.addEventListener('input', function () {
    state.notes = dom.notes.value;
    updateNotesCounter();
  });

  dom.placeOrder.addEventListener('click', placeOrder);
  dom.cartErrorRetry.addEventListener('click', function () {
    clearCartError();
    placeOrder();
  });

  // Tracking actions.
  dom.orderMore.addEventListener('click', function () {
    stopPolling();
    restoreCart();
    updateCartUI();
    showScreen('menu');
  });
  dom.requestBill.addEventListener('click', openBill);

  // Bill panel.
  dom.billClose.addEventListener('click', closeBillPanel);
  dom.billScrim.addEventListener('click', closeBillPanel);

  // Global banner / fatal retry.
  dom.bannerRetry.addEventListener('click', function () {
    var retry = state.bannerRetry;
    hideBanner();
    if (retry) retry();
  });
  dom.fatalRetry.addEventListener('click', function () {
    if (state.fatalRetry) state.fatalRetry();
  });

  // Escape closes whichever drawer is open.
  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Escape') return;
    if (dom.cartDrawer.classList.contains('is-open')) closeCart();
    else if (dom.billPanel.classList.contains('is-open')) closeBillPanel();
  });

  // Pause polling while the tab is hidden, resume when it comes back.
  document.addEventListener('visibilitychange', function () {
    if (!document.hidden && state.pollActive && !state.pollTimer) schedulePoll(0);
  });
}

/* ---------- Init ---------- */

function init() {
  wireEvents();
  var token = (new URLSearchParams(window.location.search).get('table') || '').trim();
  if (!token) {
    showScreen('scan');
    return;
  }
  state.token = token;
  boot();
}

init();
