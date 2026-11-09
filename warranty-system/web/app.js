/* ===== WarrantyHub frontend: talks to the Java REST API ===== */
const state = { role: 'shop', page: 'dashboard', wFilter: '', kFilter: '', seen: null, products: [], customers: [], checked: null };
const $ = id => document.getElementById(id);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmtDate = d => new Date(d + 'T00:00:00').toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });

async function api(path, method = 'GET', body) {
  const res = await fetch('/api' + path, { method, body: body ? JSON.stringify(body) : undefined });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || 'Something went wrong');
  return data;
}
function toast(msg) { const t = $('toast'); t.textContent = msg; t.classList.add('show'); clearTimeout(t._h); t._h = setTimeout(() => t.classList.remove('show'), 3500); }
const badge = s => `<span class="badge b-${s}">${s}</span>`;

/* ---------- navigation & roles ---------- */
const titles = { dashboard: 'Dashboard', products: 'Products', customers: 'Customers', register: 'Register Warranty', warranties: 'All Warranties', claims: 'Warranty Check & Claims' };
function setRole(r) {
  state.role = r;
  $('roleShop').classList.toggle('on', r === 'shop'); $('roleCust').classList.toggle('on', r === 'customer');
  document.querySelectorAll('#nav a').forEach(a => a.style.display = (r === 'customer' && a.dataset.role === 'shop') ? 'none' : 'block');
  setPage(r === 'customer' ? 'claims' : 'dashboard');
}
function setPage(p) {
  state.page = p;
  document.querySelectorAll('.page').forEach(s => s.classList.toggle('on', s.id === 'page-' + p));
  document.querySelectorAll('#nav a').forEach(a => a.classList.toggle('on', a.dataset.page === p));
  $('pageTitle').textContent = titles[p];
  refresh(true);
}
document.querySelectorAll('#nav a').forEach(a => a.onclick = () => setPage(a.dataset.page));

/* ---------- refresh (also runs every 4s = live updates) ---------- */
async function refresh(first) {
  try {
    if (state.page === 'dashboard') await loadDashboard();
    if (state.page === 'products') await loadProducts();
    if (state.page === 'customers') await loadCustomers();
    if (state.page === 'register' && first) await loadRegisterForm();
    if (state.page === 'warranties') await loadWarranties();
    if (state.page === 'claims') { await loadClaims(); if (state.checked && !first) liveCheck(true); }
  } catch (e) { console.error(e); }
}
setInterval(refresh, 4000);
setInterval(() => $('clock').textContent = new Date().toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'medium' }), 1000);

/* ---------- dashboard ---------- */
async function loadDashboard() {
  const s = await api('/stats');
  const card = (n, label, cls) => `<div class="stat ${cls}"><b>${n}</b><span>${label}</span></div>`;
  $('statCards').innerHTML = card(s.products, 'Products', '') + card(s.customers, 'Customers', 'info') +
    card(s.active, 'Active warranties', 'ok') + card(s.expired, 'Expired warranties', 'bad') +
    card(s.expiringSoon, 'Expiring in 30 days', 'warn') + card(s.pending, 'Pending claims', 'warn') +
    card(s.approved, 'Approved claims', 'ok') + card(s.rejected, 'Rejected claims', 'bad');
  const regs = await api('/registrations?status=Active');
  const soon = regs.filter(r => r.expiringSoon);
  $('soonList').innerHTML = soon.length ? soon.map(r => `<div><span>${esc(r.productName)}<small>${esc(r.customerName)} &middot; ${esc(r.serial)}</small></span><span class="badge b-soon">${r.daysLeft} days left</span></div>`).join('') : '<div class="muted">Nothing expiring soon.</div>';
  const cl = (await api('/claims')).slice(0, 5);
  $('recentClaims').innerHTML = cl.length ? cl.map(c => `<div><span>${esc(c.productName)}<small>${esc(c.customerName)} &middot; ${esc(c.issue).slice(0, 40)}</small></span>${badge(c.status)}</div>`).join('') : '<div class="muted">No claims yet.</div>';
}

/* ---------- Requirement 1: records ---------- */
async function loadProducts() {
  state.products = await api('/products');
  $('productRows').innerHTML = state.products.map(p => `<tr><td><b>${esc(p.name)}</b></td><td>${esc(p.brand)}</td><td>${esc(p.category)}</td><td>${p.defaultMonths} months</td><td><button class="del" onclick="delItem('products',${p.id})">Delete</button></td></tr>`).join('') || '<tr><td colspan="5" class="muted">No products yet.</td></tr>';
}
async function addProduct() {
  try {
    await api('/products', 'POST', { name: $('pName').value, brand: $('pBrand').value, category: $('pCat').value, defaultMonths: $('pMonths').value });
    ['pName', 'pBrand', 'pCat'].forEach(i => $(i).value = ''); toast('Product added'); loadProducts();
  } catch (e) { toast(e.message); }
}
async function loadCustomers() {
  state.customers = await api('/customers');
  $('customerRows').innerHTML = state.customers.map(c => `<tr><td><b>${esc(c.name)}</b></td><td>${esc(c.phone)}</td><td>${esc(c.email)}</td><td><button class="del" onclick="delItem('customers',${c.id})">Delete</button></td></tr>`).join('') || '<tr><td colspan="4" class="muted">No customers yet.</td></tr>';
}
async function addCustomer() {
  try {
    await api('/customers', 'POST', { name: $('cName').value, phone: $('cPhone').value, email: $('cEmail').value });
    ['cName', 'cPhone', 'cEmail'].forEach(i => $(i).value = ''); toast('Customer added'); loadCustomers();
  } catch (e) { toast(e.message); }
}
async function delItem(kind, id) {
  try { await api(`/${kind}/${id}`, 'DELETE'); toast('Deleted'); kind === 'products' ? loadProducts() : loadCustomers(); } catch (e) { toast(e.message); }
}

/* ---------- Requirement 2: registration with live preview ---------- */
async function loadRegisterForm() {
  state.products = await api('/products'); state.customers = await api('/customers');
  $('rProduct').innerHTML = state.products.map(p => `<option value="${p.id}">${esc(p.name)} (${esc(p.brand)})</option>`).join('');
  $('rCustomer').innerHTML = state.customers.map(c => `<option value="${c.id}">${esc(c.name)} - ${esc(c.phone)}</option>`).join('');
  $('rDate').max = new Date().toISOString().slice(0, 10);
  if (!$('rDate').value) $('rDate').value = new Date().toISOString().slice(0, 10);
  onProductPick();
}
function onProductPick() {
  const p = state.products.find(x => x.id == $('rProduct').value);
  if (p) $('rMonths').value = p.defaultMonths;   // auto-fill duration from product
  previewReg();
}
function previewReg() {
  const d = $('rDate').value, m = parseInt($('rMonths').value || 0);
  if (!d || m < 1) { $('regPreview').innerHTML = 'Pick a purchase date to preview the warranty.'; return; }
  const exp = new Date(d + 'T00:00:00'); exp.setMonth(exp.getMonth() + m);
  const days = Math.ceil((exp - new Date().setHours(0, 0, 0, 0)) / 864e5);
  $('regPreview').innerHTML = `<div class="ring-wrap">${ring(days, m * 30)}<div><p>${days >= 0 ? badge('Active') : badge('Expired')}</p><p style="margin-top:8px"><b>Expires:</b> ${exp.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })}</p><p class="muted">${days >= 0 ? days + ' days of cover remaining' : 'Expired ' + (-days) + ' days ago'}</p></div></div>`;
}
async function addRegistration() {
  try {
    const r = await api('/registrations', 'POST', { productId: $('rProduct').value, customerId: $('rCustomer').value, serial: $('rSerial').value, purchaseDate: $('rDate').value, months: $('rMonths').value });
    $('rSerial').value = ''; toast('Warranty registered for ' + r.serial); previewReg();
  } catch (e) { toast(e.message); }
}

/* ---------- Requirement 3 + additional feature: status, search ---------- */
function ring(daysLeft, totalDays) {
  const pct = daysLeft < 0 ? 100 : Math.max(0, Math.min(100, Math.round(100 - daysLeft * 100 / Math.max(totalDays, 1))));
  const color = daysLeft < 0 ? 'var(--bad)' : daysLeft <= 30 ? 'var(--warn)' : 'var(--ok)';
  const inner = daysLeft < 0 ? '<b>0</b>days left' : `<b>${daysLeft}</b>days left`;
  return `<div class="ring" style="--p:${pct};--c:${color}"><div><span>${inner}</span></div></div>`;
}
function setWFilter(btn) { document.querySelectorAll('#wChips .chip').forEach(c => c.classList.remove('on')); btn.classList.add('on'); state.wFilter = btn.dataset.v; loadWarranties(); }
async function loadWarranties() {
  const list = await api(`/registrations?q=${encodeURIComponent($('wSearch').value)}&status=${state.wFilter}`);
  $('warrantyCards').innerHTML = list.map(r => {
    const cls = r.status === 'Expired' ? 'bad' : r.expiringSoon ? 'warn' : '';
    return `<div class="wcard"><div class="head"><div><h4>${esc(r.productName)}</h4><small>${esc(r.brand)} &middot; ${esc(r.serial)}</small></div>${badge(r.status)}</div>
      <small>Owner: <b>${esc(r.customerName)}</b> (${esc(r.customerPhone)})</small>
      <div class="bar"><i class="${cls}" style="width:${r.percentUsed}%"></i></div>
      <div class="meta"><span>Bought ${fmtDate(r.purchaseDate)}</span><span>${r.status === 'Active' ? r.daysLeft + ' days left' : 'Expired'}</span><span>Ends ${fmtDate(r.expiryDate)}</span></div>
      ${r.expiringSoon ? '<div class="msg" style="background:var(--warnbg);color:var(--warn)">Expiring soon!</div>' : ''}
      <button class="mini" onclick='printCert(${JSON.stringify(r).replace(/'/g, "&#39;")})'>Print certificate</button></div>`;
  }).join('') || '<div class="empty">No warranties match your search.</div>';
}
function printCert(r) {
  $('certificate').innerHTML = `<h1>Warranty Certificate</h1><p>Electronics Shop &middot; WarrantyHub</p><table>
    <tr><td><b>Product</b></td><td>${esc(r.productName)} (${esc(r.brand)})</td></tr><tr><td><b>Serial No.</b></td><td>${esc(r.serial)}</td></tr>
    <tr><td><b>Customer</b></td><td>${esc(r.customerName)}</td></tr><tr><td><b>Purchased</b></td><td>${fmtDate(r.purchaseDate)}</td></tr>
    <tr><td><b>Valid until</b></td><td>${fmtDate(r.expiryDate)} (${r.months} months)</td></tr><tr><td><b>Status</b></td><td>${r.status}</td></tr></table><p>Please keep this certificate for claims.</p>`;
  window.print();
}

/* ---------- Requirement 4 + 5: live check, eligibility, claims ---------- */
let checkTimer;
function liveCheck(silent) {
  clearTimeout(checkTimer);
  checkTimer = setTimeout(async () => {
    const serial = $('kSerial').value.trim();
    if (!serial) { state.checked = null; $('kSubmit').disabled = true; $('checkResult').className = 'muted'; $('checkResult').textContent = 'Type a serial number. The status appears instantly.'; return; }
    const d = await api('/check?serial=' + encodeURIComponent(serial));
    if (!d.found) { state.checked = null; $('kSubmit').disabled = true; $('checkResult').innerHTML = '<div class="msg bad">No warranty registered for this serial number.</div>'; return; }
    const r = d.registration; state.checked = r.serial;
    $('checkResult').innerHTML = `<div class="ring-wrap">${ring(r.daysLeft, r.months * 30)}<div>${badge(r.status)}<h4 style="margin-top:8px">${esc(r.productName)}</h4><small class="muted">${esc(r.customerName)}<br>Valid until ${fmtDate(r.expiryDate)}</small></div></div>
      ${r.status === 'Active' ? '<div class="msg ok">Eligible: you can submit a claim.</div>' : '<div class="msg bad">Not eligible: warranty has expired, so claims are not accepted.</div>'}`;
    $('kSubmit').disabled = r.status !== 'Active';
  }, silent ? 0 : 250);
}
async function submitClaim() {
  const m = $('claimMsg');
  try {
    const c = await api('/claims', 'POST', { serial: $('kSerial').value, issue: $('kIssue').value });
    m.innerHTML = `<div class="msg ok">Claim #${c.id} submitted. Status: Pending. Track it below.</div>`; $('kIssue').value = ''; loadClaims();
  } catch (e) { m.innerHTML = `<div class="msg bad">${esc(e.message)}</div>`; }
}
function setKFilter(btn) { document.querySelectorAll('#kChips .chip').forEach(c => c.classList.remove('on')); btn.classList.add('on'); state.kFilter = btn.dataset.v; loadClaims(); }
async function loadClaims() {
  const list = await api(`/claims?q=${encodeURIComponent($('kSearch').value)}&status=${state.kFilter}`);
  const all = state.kFilter === '' && !$('kSearch').value ? list : null;
  if (all) {                       // live notification when a claim's status changes
    const now = Object.fromEntries(all.map(c => [c.id, c.status]));
    if (state.seen) all.forEach(c => { if (state.seen[c.id] && state.seen[c.id] !== c.status) toast(`Claim #${c.id} (${c.productName}) is now ${c.status}`); });
    state.seen = now;
  }
  $('claimCards').innerHTML = list.map(c => `<div class="ccard"><div class="head"><div><h4>#${c.id} &middot; ${esc(c.productName)}</h4><small>${esc(c.customerName)} &middot; ${esc(c.serial)}</small></div>${badge(c.status)}</div>
    <p style="font-size:14px">${esc(c.issue)}</p>
    <div class="timeline">${c.timeline.map(t => `<div class="t-${t.status}"><b>${t.status}</b> - ${esc(t.note)}<small>${t.at}</small></div>`).join('')}</div>
    ${state.role === 'shop' ? `<div class="actions">${c.status !== 'Approved' ? `<button class="ok" onclick="setClaim(${c.id},'Approved')">Approve</button>` : ''}${c.status !== 'Rejected' ? `<button class="no" onclick="setClaim(${c.id},'Rejected')">Reject</button>` : ''}${c.status !== 'Pending' ? `<button class="mini" style="margin:0" onclick="setClaim(${c.id},'Pending')">Back to Pending</button>` : ''}</div>` : ''}</div>`).join('') || '<div class="empty">No claims found.</div>';
}
async function setClaim(id, status) {
  const note = status === 'Pending' ? '' : (prompt(`Add a note for the customer (optional):`, status === 'Approved' ? 'Claim approved. Please visit the shop.' : 'Claim rejected after inspection.') ?? '');
  try { await api('/claims/' + id, 'PUT', { status, note }); state.seen = null; toast(`Claim #${id} marked ${status}`); loadClaims(); } catch (e) { toast(e.message); }
}

/* ---------- demo ---------- */
async function loadDemo() {
  if (!confirm('This replaces all data with sample demo data. Continue?')) return;
  await api('/demo', 'POST'); state.seen = null; toast('Demo data loaded'); refresh(true);
}
setRole('shop');
