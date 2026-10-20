/* ==========================================================================
   Manager Console — vanilla ES2020 client for the /api/admin/** endpoints.
   Same-origin session cookie (JSESSIONID) is sent with every request.
   ========================================================================== */

'use strict';

const TABS = {
  menu: { title: 'Menu', subtitle: 'Create, edit, price and 86 menu items.' },
  categories: { title: 'Categories', subtitle: 'Group menu items and control their display order.' },
  tables: { title: 'Tables', subtitle: 'Manage the dining tables and print their ordering QR codes.' },
  reports: { title: 'Reports', subtitle: 'Sales, item velocity, table utilisation and kitchen prep time.' }
};

const state = {
  staff: null,
  activeTab: 'menu',
  menu: [],
  categories: [],
  tables: [],
  dailyDefaultsSet: false
};

const $ = (selector) => document.querySelector(selector);

/* --------------------------------------------------------------- helpers */

function escapeHtml(value) {
  return String(value == null ? '' : value).replace(/[&<>"']/g, (ch) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[ch]));
}

/** Money is DECIMAL(10,2); formatting only, never used for requests. */
function money(value) {
  const n = Number(value);
  return Number.isFinite(n) ? '\u20B9' + n.toFixed(2) : '\u2014';
}

/** Editable decimal string (prefill / display only). */
function decimalString(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n.toFixed(2) : '';
}

/**
 * Serialises a body while keeping `key` as the exact typed numeric literal.
 * JSON.stringify would render a numeric string as a quoted string, and Number()
 * would risk binary float rounding, so the raw decimal text is injected as a
 * JSON number token instead. `rawNumber` must already be validated as digits/dot.
 */
function stringifyWithRawNumber(object, key, rawNumber) {
  const placeholder = '@@RAW_' + key.toUpperCase() + '_VALUE@@';
  const clone = Object.assign({}, object);
  clone[key] = placeholder;
  return JSON.stringify(clone).replace('"' + placeholder + '"', rawNumber);
}

function setBusy(control, busy) {
  if (!control) return;
  control.disabled = busy;
  control.setAttribute('aria-busy', busy ? 'true' : 'false');
}

function toast(message, kind) {
  const node = document.createElement('div');
  node.className = 'toast toast-' + (kind || 'info');
  node.setAttribute('role', kind === 'error' ? 'alert' : 'status');
  node.textContent = message;
  $('#toast-root').appendChild(node);
  window.setTimeout(() => {
    node.classList.add('toast-out');
    window.setTimeout(() => node.remove(), 260);
  }, 4200);
}

function reportError(error) {
  // A 401/403 already returns the user to the login screen; avoid toast spam.
  if (error && (error.status === 401 || error.status === 403)) return;
  toast((error && error.message) || 'Something went wrong.', 'error');
}

/* ------------------------------------------------------------------ codec */

/**
 * Thin fetch wrapper: always same-origin credentials, JSON in/out, canonical
 * error body ({ error, message, statusCode, timestamp, details? }) surfaced
 * on the thrown Error as `body`.
 */
async function api(path, options) {
  const opts = options || {};
  const init = { method: opts.method || 'GET', credentials: 'same-origin' };
  if (opts.body !== undefined) {
    init.headers = { 'Content-Type': 'application/json' };
    init.body = typeof opts.body === 'string' ? opts.body : JSON.stringify(opts.body);
  }

  const res = await fetch('/api' + path, init);
  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch (_) { data = text; }
  }

  if (!res.ok) {
    const error = new Error((data && data.message) || ('Request failed with status ' + res.status));
    error.status = res.status;
    error.body = data;
    if ((res.status === 401 || res.status === 403) && !opts.skipAuthRedirect) {
      onSessionLost(res.status);
    }
    throw error;
  }
  return data;
}

/* ------------------------------------------------------------------- auth */

function onSessionLost(status) {
  state.staff = null;
  showLogin(status === 403
    ? 'Your account does not have admin access. Sign in with a MANAGER account.'
    : 'Your session has expired. Please sign in again.');
}

function showLogin(message) {
  $('#app-view').hidden = true;
  $('#login-view').hidden = false;
  const errorBox = $('#login-error');
  errorBox.textContent = message || '';
  errorBox.hidden = !message;
  const username = $('#login-username');
  username.focus();
}

async function doLogin(event) {
  event.preventDefault();
  const button = $('#login-submit');
  const username = $('#login-username').value.trim();
  const password = $('#login-password').value;
  const errorBox = $('#login-error');
  errorBox.hidden = true;

  setBusy(button, true);
  try {
    const result = await api('/auth/login', {
      method: 'POST',
      body: { username: username, password: password },
      skipAuthRedirect: true
    });

    if (result.staff.role !== 'MANAGER') {
      await api('/auth/logout', { method: 'POST', skipAuthRedirect: true }).catch(() => {});
      showLogin('Admin access requires the MANAGER role.');
      return;
    }

    state.staff = result.staff;
    $('#login-password').value = '';
    enterApp();
    toast('Signed in as ' + state.staff.fullName + '.', 'success');
  } catch (error) {
    errorBox.textContent = error.message || 'Unable to sign in.';
    errorBox.hidden = false;
  } finally {
    setBusy(button, false);
  }
}

async function doLogout() {
  try {
    await api('/auth/logout', { method: 'POST', skipAuthRedirect: true });
  } catch (_) { /* the session is going away regardless */ }
  state.staff = null;
  showLogin('You have been signed out.');
}

function enterApp() {
  $('#login-view').hidden = true;
  $('#app-view').hidden = false;
  $('#who-name').textContent = state.staff.fullName || state.staff.username;
  $('#who-role').textContent = state.staff.role;
  switchTab(validTab(location.hash.slice(1)) || 'menu', false);
}

function validTab(tab) {
  return Object.prototype.hasOwnProperty.call(TABS, tab) ? tab : null;
}

/* -------------------------------------------------------------- navigation */

function switchTab(tab, updateHash) {
  state.activeTab = tab;
  if (updateHash !== false && location.hash.slice(1) !== tab) {
    location.hash = tab;
  }
  document.querySelectorAll('.nav-item').forEach((button) => {
    button.classList.toggle('active', button.dataset.tab === tab);
  });
  document.querySelectorAll('.panel').forEach((panel) => {
    panel.hidden = panel.dataset.panel !== tab;
  });
  $('#tab-title').textContent = TABS[tab].title;
  $('#tab-subtitle').textContent = TABS[tab].subtitle;
  loadTab(tab);
}

function loadTab(tab) {
  if (tab === 'menu') loadMenu();
  else if (tab === 'categories') loadCategories();
  else if (tab === 'tables') loadTables();
  else if (tab === 'reports') loadReports();
}

/* ------------------------------------------------------------------ modal */

/** Generic form modal. `onSubmit(form, close)` should throw to keep it open. */
function openModal(options) {
  const root = $('#modal-root');
  root.innerHTML =
    '<div class="modal-backdrop" data-close="1"></div>' +
    '<div class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">' +
      '<form id="modal-form" novalidate>' +
        '<div class="modal-head"><h2 id="modal-title" class="modal-title">' + escapeHtml(options.title) + '</h2></div>' +
        '<div class="modal-body">' + options.bodyHtml +
          '<div id="modal-errors" class="modal-errors" hidden></div>' +
        '</div>' +
        '<div class="modal-actions">' +
          '<button type="button" class="btn btn-ghost" data-close="1">Cancel</button>' +
          '<button type="submit" id="modal-submit" class="btn ' + (options.danger ? 'btn-danger' : 'btn-primary') + '">' +
            escapeHtml(options.submitLabel) + '</button>' +
        '</div>' +
      '</form>' +
    '</div>';
  root.hidden = false;

  const close = () => {
    root.hidden = true;
    root.innerHTML = '';
    document.removeEventListener('keydown', onKey);
  };
  const onKey = (event) => { if (event.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  root.querySelectorAll('[data-close]').forEach((node) => node.addEventListener('click', close));

  const form = root.querySelector('#modal-form');
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = root.querySelector('#modal-submit');
    showModalErrors(null);
    setBusy(submit, true);
    try {
      await options.onSubmit(form);
      close();
    } catch (error) {
      showModalErrors(error);
      setBusy(submit, false);
    }
  });

  const firstField = form.querySelector('input, select, textarea');
  if (firstField) firstField.focus();
}

/** Renders the server's `details` array (per-field errors) or the plain message. */
function showModalErrors(error) {
  const box = $('#modal-errors');
  if (!box) return;
  if (!error) { box.hidden = true; box.textContent = ''; return; }

  const details = error.body && Array.isArray(error.body.details) ? error.body.details : null;
  if (details && details.length) {
    box.innerHTML = '<ul>' + details.map((line) => '<li>' + escapeHtml(line) + '</li>').join('') + '</ul>';
  } else {
    box.textContent = error.message || 'The request could not be completed.';
  }
  box.hidden = false;
}

function confirmDialog(options) {
  return new Promise((resolve) => {
    const root = $('#modal-root');
    root.innerHTML =
      '<div class="modal-backdrop" data-close="1"></div>' +
      '<div class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">' +
        '<h2 id="modal-title" class="modal-title">' + escapeHtml(options.title) + '</h2>' +
        '<p class="modal-text">' + escapeHtml(options.message) + '</p>' +
        '<div class="modal-actions">' +
          '<button type="button" class="btn btn-ghost" data-close="1">Cancel</button>' +
          '<button type="button" class="btn ' + (options.danger ? 'btn-danger' : 'btn-primary') + '" data-confirm="1">' +
            escapeHtml(options.confirmLabel) + '</button>' +
        '</div>' +
      '</div>';
    root.hidden = false;

    const done = (value) => {
      root.hidden = true;
      root.innerHTML = '';
      document.removeEventListener('keydown', onKey);
      resolve(value);
    };
    const onKey = (event) => { if (event.key === 'Escape') done(false); };
    document.addEventListener('keydown', onKey);
    root.querySelectorAll('[data-close]').forEach((node) => node.addEventListener('click', () => done(false)));
    root.querySelector('[data-confirm]').addEventListener('click', () => done(true));
    root.querySelector('[data-confirm]').focus();
  });
}

/* -------------------------------------------------------------------- menu */

async function loadMenu() {
  try {
    state.menu = await api('/admin/menu');
    renderMenu();
  } catch (error) {
    reportError(error);
  }
}

function renderMenu() {
  const tbody = $('#menu-tbody');
  tbody.innerHTML = state.menu.map(renderMenuRow).join('');
  $('#menu-empty').hidden = state.menu.length > 0;
}

function renderMenuRow(item) {
  const volume = item.volumeMl == null ? '\u2014' : escapeHtml(item.volumeMl) + ' ml';
  const spice = item.spiceLevel ? escapeHtml(item.spiceLevel) : '\u2014';
  return '' +
    '<tr data-id="' + item.itemId + '"' + (item.isAvailable ? '' : ' class="row-soldout"') + '>' +
      '<td>' + escapeHtml(item.name) + '</td>' +
      '<td>' + escapeHtml(item.categoryName || '') + '</td>' +
      '<td class="num">' + money(item.price) + '</td>' +
      '<td>' + escapeHtml(item.itemType) + '</td>' +
      '<td>' + spice + '</td>' +
      '<td>' + volume + '</td>' +
      '<td>' +
        '<button type="button" class="avail-toggle ' + (item.isAvailable ? 'is-on' : 'is-off') + '" ' +
          'data-action="toggle" aria-pressed="' + item.isAvailable + '">' +
          '<span class="avail-dot"></span>' +
          '<span class="avail-text">' + (item.isAvailable ? 'Available' : 'SOLD OUT') + '</span>' +
        '</button>' +
      '</td>' +
      '<td class="actions">' +
        '<button type="button" class="btn btn-ghost" data-action="edit">Edit</button>' +
        '<button type="button" class="btn btn-danger-ghost" data-action="delete">Delete</button>' +
      '</td>' +
    '</tr>';
}

function replaceMenuRow(item) {
  const row = $('#menu-tbody').querySelector('tr[data-id="' + item.itemId + '"]');
  if (!row) { renderMenu(); return; }
  row.outerHTML = renderMenuRow(item);
}

async function onMenuAction(event) {
  const button = event.target.closest('button[data-action]');
  if (!button) return;
  const row = button.closest('tr');
  const id = Number(row.dataset.id);
  const item = state.menu.find((entry) => entry.itemId === id);
  if (!item) return;

  if (button.dataset.action === 'toggle') await toggleAvailability(button, item);
  else if (button.dataset.action === 'edit') openMenuModal(item);
  else if (button.dataset.action === 'delete') await deleteMenuItem(item);
}

async function toggleAvailability(button, item) {
  const next = !item.isAvailable;
  setBusy(button, true);
  try {
    const updated = await api('/admin/menu/' + item.itemId + '/availability', {
      method: 'PATCH',
      body: { isAvailable: next }
    });
    Object.assign(item, updated);
    replaceMenuRow(item);
    toast(updated.isAvailable
      ? '"' + updated.name + '" is now available.'
      : '"' + updated.name + '" is now SOLD OUT and hidden from ordering.', 'success');
  } catch (error) {
    reportError(error);
  } finally {
    setBusy(button, false);
  }
}

async function deleteMenuItem(item) {
  const confirmed = await confirmDialog({
    title: 'Archive menu item',
    message: '"' + item.name + '" will be archived (soft-deleted) and hidden from the customer menu. ' +
      'Existing order history is preserved \u2014 the record is not permanently erased.',
    confirmLabel: 'Archive item',
    danger: true
  });
  if (!confirmed) return;

  try {
    const response = await api('/admin/menu/' + item.itemId, { method: 'DELETE' });
    state.menu = state.menu.filter((entry) => entry.itemId !== item.itemId);
    renderMenu();
    toast(response.message || ('"' + item.name + '" archived.'), 'success');
  } catch (error) {
    reportError(error);
  }
}

const ITEM_TYPES = ['FOOD', 'BEVERAGE', 'DESSERT'];
const SPICE_LEVELS = ['MILD', 'MEDIUM', 'HOT', 'EXTRA_HOT'];

function openMenuModal(item) {
  if (!state.categories.length) {
    toast('Create at least one category before adding menu items.', 'error');
    return;
  }
  const isEdit = Boolean(item);
  const categoryOptions = state.categories.map((category) =>
    '<option value="' + category.categoryId + '"' +
    (item && item.categoryId === category.categoryId ? ' selected' : '') + '>' +
    escapeHtml(category.name) + '</option>').join('');
  const typeOptions = ITEM_TYPES.map((type) =>
    '<option value="' + type + '"' + (item && item.itemType === type ? ' selected' : '') + '>' + type + '</option>').join('');
  const spiceOptions = ['<option value="">\u2014 none \u2014</option>'].concat(
    SPICE_LEVELS.map((level) =>
      '<option value="' + level + '"' + (item && item.spiceLevel === level ? ' selected' : '') + '>' + level + '</option>')
  ).join('');

  const bodyHtml =
    '<label class="field"><span class="field-label">Name</span>' +
      '<input id="f-name" type="text" maxlength="100" required value="' + (item ? escapeHtml(item.name) : '') + '" /></label>' +
    '<label class="field"><span class="field-label">Category</span>' +
      '<select id="f-category">' + categoryOptions + '</select></label>' +
    '<label class="field"><span class="field-label">Description</span>' +
      '<textarea id="f-description" rows="2">' + (item ? escapeHtml(item.description || '') : '') + '</textarea></label>' +
    '<div class="field-row">' +
      '<label class="field"><span class="field-label">Price (\u20B9)</span>' +
        '<input id="f-price" type="text" inputmode="decimal" required value="' + (item ? escapeHtml(decimalString(item.price)) : '') + '" placeholder="0.00" /></label>' +
      '<label class="field"><span class="field-label">Type</span><select id="f-type">' + typeOptions + '</select></label>' +
    '</div>' +
    '<div class="field-row">' +
      '<label class="field"><span class="field-label">Spice level</span><select id="f-spice">' + spiceOptions + '</select></label>' +
      '<label class="field"><span class="field-label">Volume (ml)</span>' +
        '<input id="f-volume" type="number" min="1" step="1" value="' + (item && item.volumeMl != null ? item.volumeMl : '') + '" /></label>' +
    '</div>' +
    '<label class="field-check"><input id="f-available" type="checkbox"' + (!item || item.isAvailable ? ' checked' : '') + ' />' +
      '<span>Available for ordering</span></label>';

  openModal({
    title: isEdit ? 'Edit menu item' : 'New menu item',
    bodyHtml: bodyHtml,
    submitLabel: isEdit ? 'Save changes' : 'Create item',
    onSubmit: async (form) => {
      const name = form.querySelector('#f-name').value.trim();
      if (!name) throw new Error('Name is required.');

      const priceText = form.querySelector('#f-price').value.trim();
      if (!/^\d+(\.\d{1,2})?$/.test(priceText) || Number(priceText) <= 0) {
        throw new Error('Enter a valid price greater than 0, e.g. 280 or 280.50.');
      }

      const volumeText = form.querySelector('#f-volume').value.trim();
      const payload = {
        categoryId: Number(form.querySelector('#f-category').value),
        name: name,
        description: form.querySelector('#f-description').value.trim() || null,
        itemType: form.querySelector('#f-type').value,
        spiceLevel: form.querySelector('#f-spice').value || null,
        volumeMl: volumeText ? Number(volumeText) : null,
        isAvailable: form.querySelector('#f-available').checked
      };
      // Keep the typed decimal text as an exact JSON number (no float rounding).
      const body = stringifyWithRawNumber(payload, 'price', priceText);

      if (isEdit) {
        const updated = await api('/admin/menu/' + item.itemId, { method: 'PUT', body: body });
        Object.assign(item, updated);
        replaceMenuRow(item);
        toast('"' + updated.name + '" updated.', 'success');
      } else {
        const created = await api('/admin/menu', { method: 'POST', body: body });
        state.menu.push(created);
        renderMenu();
        toast('"' + created.name + '" created.', 'success');
      }
    }
  });
}

/* -------------------------------------------------------------- categories */

async function loadCategories() {
  try {
    state.categories = await api('/admin/categories');
    renderCategories();
  } catch (error) {
    reportError(error);
  }
}

function renderCategories() {
  const tbody = $('#categories-tbody');
  tbody.innerHTML = state.categories.map((category) =>
    '<tr>' +
      '<td>' + escapeHtml(category.name) + '</td>' +
      '<td>' + escapeHtml(category.description || '\u2014') + '</td>' +
      '<td class="num">' + escapeHtml(category.displayOrder) + '</td>' +
      '<td><span class="pill ' + (category.isActive ? 'pill-on' : 'pill-neutral') + '">' +
        (category.isActive ? 'Active' : 'Inactive') + '</span></td>' +
    '</tr>').join('');
  $('#categories-empty').hidden = state.categories.length > 0;
}

function openCategoryModal() {
  const suggestedOrder = state.categories.length + 1;
  const bodyHtml =
    '<label class="field"><span class="field-label">Name</span>' +
      '<input id="f-cat-name" type="text" maxlength="50" required /></label>' +
    '<label class="field"><span class="field-label">Description</span>' +
      '<textarea id="f-cat-description" rows="2" maxlength="255"></textarea></label>' +
    '<label class="field"><span class="field-label">Display order</span>' +
      '<input id="f-cat-order" type="number" step="1" value="' + suggestedOrder + '" /></label>' +
    '<label class="field-check"><input id="f-cat-active" type="checkbox" checked /><span>Active</span></label>';

  openModal({
    title: 'New category',
    bodyHtml: bodyHtml,
    submitLabel: 'Create category',
    onSubmit: async (form) => {
      const name = form.querySelector('#f-cat-name').value.trim();
      if (!name) throw new Error('Name is required.');
      const orderText = form.querySelector('#f-cat-order').value.trim();

      const created = await api('/admin/categories', {
        method: 'POST',
        body: {
          name: name,
          description: form.querySelector('#f-cat-description').value.trim() || null,
          displayOrder: orderText ? Number(orderText) : null,
          isActive: form.querySelector('#f-cat-active').checked
        }
      });
      state.categories.push(created);
      renderCategories();
      toast('Category "' + created.name + '" created.', 'success');
    }
  });
}

/* ------------------------------------------------------------------ tables */

async function loadTables() {
  try {
    state.tables = await api('/admin/tables');
    renderTables();
  } catch (error) {
    reportError(error);
  }
}

function renderTables() {
  const tbody = $('#tables-tbody');
  tbody.innerHTML = state.tables.map((table) => '' +
    '<tr data-id="' + table.tableId + '">' +
      '<td>' + escapeHtml(table.tableNumber) + '</td>' +
      '<td class="num">' + escapeHtml(table.capacity) + '</td>' +
      '<td><code title="' + escapeHtml(table.qrToken) + '">' + escapeHtml(table.qrToken) + '</code></td>' +
      '<td><span class="pill ' + (table.isActive ? 'pill-on' : 'pill-neutral') + '">' +
        (table.isActive ? 'Active' : 'Inactive') + '</span></td>' +
      '<td>' + escapeHtml(table.currentSessionStatus || '\u2014') + '</td>' +
      '<td class="actions">' +
        '<button type="button" class="btn btn-ghost" data-action="edit">Edit</button>' +
        '<button type="button" class="btn btn-ghost" data-action="print">Print QR</button>' +
      '</td>' +
    '</tr>').join('');
  $('#tables-empty').hidden = state.tables.length > 0;
}

async function onTableAction(event) {
  const button = event.target.closest('button[data-action]');
  if (!button) return;
  const id = Number(button.closest('tr').dataset.id);
  const table = state.tables.find((entry) => entry.tableId === id);
  if (!table) return;

  if (button.dataset.action === 'edit') openTableModal(table);
  else if (button.dataset.action === 'print') {
    setBusy(button, true);
    try { await printTables([table]); } finally { setBusy(button, false); }
  }
}

function openTableModal(table) {
  const isEdit = Boolean(table);
  const bodyHtml =
    '<label class="field"><span class="field-label">Table number</span>' +
      '<input id="f-table-number" type="text" maxlength="20" required value="' + (table ? escapeHtml(table.tableNumber) : '') + '" /></label>' +
    '<label class="field"><span class="field-label">Capacity</span>' +
      '<input id="f-table-capacity" type="number" min="1" step="1" required value="' + (table ? escapeHtml(table.capacity) : '') + '" /></label>' +
    '<label class="field-check"><input id="f-table-active" type="checkbox"' + (!table || table.isActive ? ' checked' : '') + ' />' +
      '<span>Active</span></label>';

  openModal({
    title: isEdit ? 'Edit table' : 'New table',
    bodyHtml: bodyHtml,
    submitLabel: isEdit ? 'Save changes' : 'Create table',
    onSubmit: async (form) => {
      const tableNumber = form.querySelector('#f-table-number').value.trim();
      if (!tableNumber) throw new Error('Table number is required.');
      const capacity = Number(form.querySelector('#f-table-capacity').value);
      if (!Number.isInteger(capacity) || capacity < 1) throw new Error('Capacity must be a whole number of at least 1.');

      const body = { tableNumber: tableNumber, capacity: capacity, isActive: form.querySelector('#f-table-active').checked };

      if (isEdit) {
        const updated = await api('/admin/tables/' + table.tableId, { method: 'PUT', body: body });
        Object.assign(table, updated);
        renderTables();
        toast('Table ' + updated.tableNumber + ' updated.', 'success');
      } else {
        const created = await api('/admin/tables', { method: 'POST', body: body });
        state.tables.push(created);
        renderTables();
        toast('Table ' + created.tableNumber + ' created.', 'success');
      }
    }
  });
}

/* -------------------------------------------------------------- QR printing */

/**
 * Builds one printable card per table. Uses the server-rendered `qrCodeDataUrl`
 * when present, otherwise draws the QR locally from `menuUrl` with the vendored
 * QRCode library.
 */
async function printTables(tables) {
  const root = $('#print-root');
  root.innerHTML = '';

  for (const table of tables) {
    let qr;
    try {
      qr = await api('/admin/tables/' + table.tableId + '/qr');
    } catch (error) {
      reportError(error);
      continue;
    }
    root.appendChild(buildPrintCard(qr));
  }

  if (!root.children.length) {
    toast('No QR codes available to print.', 'error');
    return;
  }
  window.print();
}

function buildPrintCard(qr) {
  const card = document.createElement('div');
  card.className = 'print-card';

  const brand = document.createElement('div');
  brand.className = 'print-brand';
  brand.textContent = 'Restaurant \u00B7 Order here';

  const number = document.createElement('div');
  number.className = 'print-table-number';
  number.textContent = 'Table ' + qr.tableNumber;

  const qrBox = document.createElement('div');
  qrBox.className = 'print-qr';

  const hint = document.createElement('div');
  hint.className = 'print-hint';
  hint.textContent = 'Scan to view the menu & place an order';

  const url = document.createElement('div');
  url.className = 'print-url';
  url.textContent = qr.menuUrl || '';

  card.appendChild(brand);
  card.appendChild(number);
  card.appendChild(qrBox);
  card.appendChild(hint);
  card.appendChild(url);

  if (qr.qrCodeDataUrl) {
    const img = document.createElement('img');
    img.src = qr.qrCodeDataUrl;
    img.alt = 'QR code for table ' + qr.tableNumber;
    qrBox.appendChild(img);
  } else if (typeof QRCode === 'function' && qr.menuUrl) {
    // Draws an SVG or canvas node into qrBox.
    new QRCode(qrBox, {
      text: qr.menuUrl,
      width: 240,
      height: 240,
      correctLevel: QRCode.CorrectLevel.H
    });
  }

  return card;
}

/* ----------------------------------------------------------------- reports */

const DAY_MS = 24 * 60 * 60 * 1000;

function isoDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return y + '-' + m + '-' + d;
}

function initDailyDefaults() {
  const end = new Date();
  const start = new Date(end.getTime() - 6 * DAY_MS);
  $('#daily-start').value = isoDate(start);
  $('#daily-end').value = isoDate(end);
}

function loadReports() {
  if (!state.dailyDefaultsSet) {
    initDailyDefaults();
    state.dailyDefaultsSet = true;
  }
  runDaily(null);
  runTop(null);
  runBusiest(null);
  runPrep(null);
}

async function runDaily(button) {
  const start = $('#daily-start').value;
  const end = $('#daily-end').value;
  if (!start || !end) {
    toast('Choose both a start and an end date.', 'error');
    return;
  }
  setBusy(button, true);
  try {
    const rows = await api('/admin/reports/daily-sales?startDate=' + encodeURIComponent(start) + '&endDate=' + encodeURIComponent(end));
    renderDaily(rows);
  } catch (error) {
    reportError(error);
  } finally {
    setBusy(button, false);
  }
}

function renderDaily(rows) {
  const tbody = $('#daily-tbody');
  tbody.innerHTML = (rows || []).map((row) => '' +
    '<tr>' +
      '<td>' + escapeHtml(row.salesDate) + '</td>' +
      '<td class="num">' + escapeHtml(row.totalTicketsPlaced) + '</td>' +
      '<td class="num">' + escapeHtml(row.completedTickets) + '</td>' +
      '<td class="num">' + escapeHtml(row.cancelledTickets) + '</td>' +
      '<td class="num">' + money(row.dailyGrossRevenue) + '</td>' +
      '<td class="num">' + money(row.avgTicketSize) + '</td>' +
    '</tr>').join('');
  $('#daily-empty').hidden = Boolean(rows && rows.length);
}

async function runTop(button) {
  const limit = $('#top-limit').value;
  setBusy(button, true);
  try {
    const rows = await api('/admin/reports/top-items?limit=' + encodeURIComponent(limit));
    renderTop(rows);
  } catch (error) {
    reportError(error);
  } finally {
    setBusy(button, false);
  }
}

function renderTop(rows) {
  const tbody = $('#top-tbody');
  tbody.innerHTML = (rows || []).map((row, index) => '' +
    '<tr>' +
      '<td class="num">' + (index + 1) + '</td>' +
      '<td>' + escapeHtml(row.itemName) + '</td>' +
      '<td>' + escapeHtml(row.category || '\u2014') + '</td>' +
      '<td>' + escapeHtml(row.itemType) + '</td>' +
      '<td class="num">' + escapeHtml(row.totalUnitsSold) + '</td>' +
      '<td class="num">' + money(row.totalRevenueGenerated) + '</td>' +
    '</tr>').join('');
  $('#top-empty').hidden = Boolean(rows && rows.length);
}

async function runBusiest(button) {
  setBusy(button, true);
  try {
    const rows = await api('/admin/reports/busiest-tables');
    renderBusiest(rows);
  } catch (error) {
    reportError(error);
  } finally {
    setBusy(button, false);
  }
}

function renderBusiest(rows) {
  const tbody = $('#busy-tbody');
  tbody.innerHTML = (rows || []).map((row, index) => '' +
    '<tr>' +
      '<td class="num">' + (index + 1) + '</td>' +
      '<td>' + escapeHtml(row.tableNumber) + '</td>' +
      '<td class="num">' + escapeHtml(row.capacity) + '</td>' +
      '<td class="num">' + escapeHtml(row.sessionsHosted) + '</td>' +
      '<td class="num">' + escapeHtml(row.totalOrdersPlaced) + '</td>' +
      '<td class="num">' + money(row.grossRevenueEarned) + '</td>' +
    '</tr>').join('');
  $('#busy-empty').hidden = Boolean(rows && rows.length);
}

async function runPrep(button) {
  setBusy(button, true);
  try {
    const result = await api('/admin/reports/avg-prep-time');
    renderPrep(result);
  } catch (error) {
    reportError(error);
  } finally {
    setBusy(button, false);
  }
}

function renderPrep(result) {
  const body = $('#prep-body');
  const hasData = result && result.ordersMeasured > 0;
  $('#prep-empty').hidden = hasData;
  if (!hasData) { body.innerHTML = ''; return; }

  const stats = [
    { label: 'Orders measured', value: escapeHtml(result.ordersMeasured) },
    { label: 'Average prep (min)', value: escapeHtml(result.avgKitchenPrepMinutes) },
    { label: 'Fastest prep (min)', value: escapeHtml(result.fastestPrepMinutes) },
    { label: 'Slowest prep (min)', value: escapeHtml(result.slowestPrepMinutes) }
  ];
  body.innerHTML = stats.map((stat) =>
    '<div class="stat"><div class="stat-label">' + stat.label + '</div>' +
    '<div class="stat-value">' + stat.value + '</div></div>').join('');
}

/* -------------------------------------------------------------- boot wiring */

function wireEvents() {
  $('#login-form').addEventListener('submit', doLogin);
  $('#logout-btn').addEventListener('click', doLogout);

  document.querySelectorAll('.nav-item').forEach((button) => {
    button.addEventListener('click', () => switchTab(button.dataset.tab));
  });
  window.addEventListener('hashchange', () => {
    const tab = validTab(location.hash.slice(1));
    if (tab && tab !== state.activeTab) switchTab(tab, false);
  });

  $('#menu-new').addEventListener('click', () => openMenuModal(null));
  $('#menu-tbody').addEventListener('click', onMenuAction);

  $('#category-new').addEventListener('click', openCategoryModal);

  $('#table-new').addEventListener('click', () => openTableModal(null));
  $('#tables-tbody').addEventListener('click', onTableAction);
  $('#tables-print-all').addEventListener('click', async (event) => {
    const button = event.currentTarget;
    setBusy(button, true);
    try { await printTables(state.tables); } finally { setBusy(button, false); }
  });

  $('#daily-run').addEventListener('click', (event) => runDaily(event.currentTarget));
  $('#top-run').addEventListener('click', (event) => runTop(event.currentTarget));
  $('#busy-run').addEventListener('click', (event) => runBusiest(event.currentTarget));
  $('#prep-run').addEventListener('click', (event) => runPrep(event.currentTarget));
}

async function bootstrap() {
  wireEvents();
  try {
    const me = await api('/auth/me', { skipAuthRedirect: true });
    if (me.role !== 'MANAGER') {
      await api('/auth/logout', { method: 'POST', skipAuthRedirect: true }).catch(() => {});
      showLogin('Admin access requires the MANAGER role.');
      return;
    }
    state.staff = me;
    enterApp();
  } catch (_) {
    showLogin(null);
  }
}

document.addEventListener('DOMContentLoaded', bootstrap);
