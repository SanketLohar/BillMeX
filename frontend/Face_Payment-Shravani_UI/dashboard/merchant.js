// ============================================================
//  BillMe — Merchant Dashboard Logic  (merchant.js)
// ============================================================
'use strict';

// ── State ────────────────────────────────────────────────────
let merchantProfile = null;
let productList = [];
let invoiceList = [];
let txPage = 0;
let txTotalPages = 1;
let currentInvId = null;  // for modal

// ── Chart instances ──────────────────────────────────────────
let chartStatus = null, chartPayment = null, chartActivity = null;

// ── ✅ CRITICAL: toggleSidebar defined at TOP-LEVEL scope ────────
// Must be here BEFORE DOMContentLoaded so inline onclick="toggleSidebar()"
// on the menu button works on the FIRST click without any race condition.
window.toggleSidebar = function (forcedState) {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebarOverlay');
    console.log('Sidebar toggle clicked', 'forcedState:', forcedState, 'sidebar found:', !!sidebar);

    if (!sidebar) {
        console.error('[Merchant] Sidebar element not found! Check id="sidebar" in HTML.');
        return;
    }

    if (typeof forcedState === 'boolean') {
        sidebar.classList.toggle('open', forcedState);
        if (overlay) overlay.classList.toggle('active', forcedState);
    } else {
        sidebar.classList.toggle('open');
        if (overlay) overlay.classList.toggle('active');
    }
    console.log('[Merchant] Sidebar state:', sidebar.classList.contains('open') ? 'OPEN' : 'CLOSED');
};

// ── Init ─────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
    // Dynamic year
    const yearEl = document.getElementById('currentYear');
    if (yearEl) yearEl.textContent = new Date().getFullYear();

    // Failsafe loader hide
    setTimeout(() => {
        const loader = document.getElementById('pageLoader');
        const dash = document.getElementById('dashLayout');
        if (loader) loader.style.display = 'none';
        if (dash && dash.style.display !== 'flex') dash.style.display = 'flex';
    }, 5000);

    await loadDashboard();
    bindNav();
    bindSidebarToggle();
    bindProfileForm();
    bindProductForm();
    bindInvoiceForm();
    bindTransactions();
    bindMisc();

    // Export global functions heavily used by row buttons
    window.previewInvoice = previewInvoice;
    window.downloadInvoicePdf = downloadInvoicePdf;
    window.deleteProduct = deleteProduct;
    window.approveRefund = approveRefund;
    window.rejectRefund = rejectRefund;

    if (window.applyTranslations) {
        const lang = localStorage.getItem('billme_lang') || 'en';
        window.applyTranslations(lang);
    }

    // 🔄 Auto-refresh dashboard stats every 60 seconds for "real-time" feel
    setInterval(() => {
        console.log("🔄 Auto-refreshing dashboard stats...");
        loadDashboard();
    }, 60000);
});

// ── Error Handling Utilities ──────────────────────────────────
function safeErrorMessage(e) {
    if (!e) return "Unknown error";
    if (typeof e === "string") return e;
    if (e.message) return e.message;
    if (e.error) return e.error;
    if (typeof e === 'object') {
        try { return JSON.stringify(e); } catch (s) { return "Something went wrong"; }
    }
    return "Something went wrong";
}

window.addEventListener("error", function (e) {
    console.error("GLOBAL ERROR:", e.error || e.message);
});

async function loadDashboard() {
    try {
        const [profile, products, invoices, balanceSheet, wallet, paymentMethods] = await Promise.all([
            API.merchant.getProfile().catch(() => null),
            API.merchant.getProducts().catch(() => []),
            API.merchant.getInvoices().catch(() => []),
            API.wallet.getBalanceSheet().catch(() => null),
            API.wallet.getWallet().catch(() => ({ balance: 0, escrowBalance: 0 })),
            API.merchant.getPaymentMethods().catch(() => ({ UPI: 0, FACE_PAY: 0, CARD: 0 }))
        ]);

        merchantProfile = profile;
        productList = products || [];
        invoiceList = invoices || [];

        renderSidebar(profile);
        renderProfileBanner(profile);
        populateProfile(profile);
        renderStatCards(productList, invoiceList, balanceSheet, wallet);
        renderCharts(invoiceList, paymentMethods);
        renderProducts();
        renderInvoices();

    } catch (e) {
        showToast(`Failed to load dashboard: ${safeErrorMessage(e)}`, 'error');
    } finally {
        const loader = document.getElementById('pageLoader');
        const dash = document.getElementById('dashLayout');
        if (loader) loader.style.display = 'none';
        if (dash) dash.style.display = 'flex';
    }
}

// ── Sidebar data ─────────────────────────────────────────────
function renderSidebar(profile) {

    if (!profile) {
        console.warn("Profile is undefined");
        return;
    }

    const sbName = document.getElementById('sb-name');
    const sbEmail = document.getElementById('sb-email');
    const sbUpi = document.getElementById('sb-upi');
    const topAvatar = document.getElementById('topbarAvatar');
    const badge = document.getElementById('sb-badge');

    // ✅ SAFE ACCESS EVERYWHERE
    const businessName = profile?.businessName || 'Merchant';
    const ownerName = profile?.ownerName || '';
    const upiId = profile?.upiId || 'Not set';
    const isComplete = profile?.profileCompleted === true;

    if (sbName) sbName.textContent = businessName;
    if (sbEmail) sbEmail.textContent = ownerName;
    if (sbUpi) sbUpi.textContent = upiId;

    if (topAvatar) {
        if (profile && profile.profileImageUrl) {
            topAvatar.innerHTML = `<img src="${profile.profileImageUrl}" style="width:100%; height:100%; border-radius:50%; object-fit:cover;">`;
            topAvatar.style.background = 'none';
        } else {
            topAvatar.textContent = businessName.charAt(0).toUpperCase();
        }
    }

    if (badge) {
        if (isComplete) {
            badge.textContent = 'Profile Completed';
            badge.className = 'profile-badge complete';
        } else {
            badge.textContent = 'Incomplete';
            badge.className = 'profile-badge incomplete';
        }
    }
}

function renderProfileBanner(profile) {
    const banner = document.getElementById('profileBanner');
    if (!banner) return;
    if (profile && profile.profileCompleted) {
        banner.style.display = 'none';
    } else {
        banner.style.display = 'flex';
    }
}

// ── Stat cards (real data from balance sheet & wallet) ─────────
function renderStatCards(products, invoices, balanceSheet, wallet) {
    const revenue = balanceSheet ? balanceSheet.totalRevenue : 0;
    const balance = wallet ? (wallet.currentBalance || wallet.balance || 0) : 0;
    const escrow = wallet ? (wallet.escrowBalance || 0) : 0;
    const withdrawals = wallet ? (wallet.totalWithdrawn || 0) : 0;
    const fees = wallet ? (wallet.platformFee || 0) : 0;

    const revEl = document.getElementById('stat-revenue');
    const balEl = document.getElementById('stat-balance');
    const escEl = document.getElementById('stat-escrow');
    const prodEl = document.getElementById('stat-products');
    const invEl = document.getElementById('stat-invoices');

    if (revEl) revEl.textContent = `₹${(revenue || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    if (balEl) balEl.textContent = `₹${(balance || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    if (escEl) escEl.textContent = `₹${(escrow || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    if (prodEl) prodEl.textContent = products ? products.length : 0;
    if (invEl) invEl.textContent = invoices ? invoices.length : 0;

    // Update Balance Sheet Section
    const bsRev = document.getElementById('bs-revenue');
    const bsEsc = document.getElementById('bs-escrow');
    const bsWd = document.getElementById('bs-withdrawals');
    const bsFees = document.getElementById('bs-fees');

    if (bsRev) bsRev.textContent = `₹${(revenue || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    if (bsEsc) bsEsc.textContent = `₹${(escrow || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    if (bsWd) bsWd.textContent = `₹${(withdrawals || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    if (bsFees) bsFees.textContent = `₹${(fees || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;

    const bstRev = document.getElementById('bst-rev');
    const bstEsc = document.getElementById('bst-escrow');
    const bstWd = document.getElementById('bst-wd');
    const bstFees = document.getElementById('bst-fees');
    const bstNet = document.getElementById('bst-net');

    if (bstRev) bstRev.textContent = (revenue || 0).toFixed(2);
    if (bstEsc) bstEsc.textContent = (escrow || 0).toFixed(2);
    if (bstWd) bstWd.textContent = (withdrawals || 0).toFixed(2);
    if (bstFees) bstFees.textContent = (fees || 0).toFixed(2);
    if (bstNet) {
        const net = revenue + escrow - withdrawals - fees;
        bstNet.textContent = net.toFixed(2);
    }
}

// ── Charts (use API payment methods and invoice counts) ─
function renderCharts(invoices, paymentMethods) {
    // Invoice status distribution
    const statusCounts = { PAID: 0, PENDING: 0, UNPAID: 0, CANCELLED: 0 };
    invoices.forEach(inv => {
        const s = inv.status?.toUpperCase() || 'UNPAID';
        if (s in statusCounts) statusCounts[s]++;
        else statusCounts.UNPAID++;
    });

    // Payment method distribution from API (SAFE ACCESS)
   const methodCounts = {
    UPI: paymentMethods?.UPI 
        || paymentMethods?.upi 
        || paymentMethods?.UPI_PAY 
        || 0,

    FacePay: paymentMethods?.FACE_PAY 
        || paymentMethods?.facepay 
        || 0,

    Card: paymentMethods?.CARD 
        || paymentMethods?.card 
        || 0
};

    // Monthly activity
    const monthLabels = [];
    const monthData = {};
    const now = new Date();
    for (let i = 11; i >= 0; i--) {
        const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        monthLabels.push(d.toLocaleString('default', { month: 'short', year: '2-digit' }));
        monthData[key] = 0;
    }
    invoices.forEach(inv => {
        if (!inv.issuedAt) return;
        const d = new Date(inv.issuedAt);
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        if (key in monthData) monthData[key]++;
    });

    if (chartStatus) chartStatus.destroy();
    chartStatus = new Chart(document.getElementById('chartInvoiceStatus'), {
        type: 'doughnut',
        data: {
            labels: Object.keys(statusCounts),
            datasets: [{
                data: Object.values(statusCounts),
                backgroundColor: ['#34a853', '#fbbc04', '#ea4335', '#9aa0a6'],
                borderWidth: 2, borderColor: '#fff'
            }]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            plugins: { legend: { position: 'bottom' } }
        }
    });

    // ✅ SAFE Payment Chart Rendering (Production-proof)
    const paymentCanvas = document.getElementById('chartPaymentMethods');

    if (!paymentCanvas) {
    console.warn('[Chart] Payment canvas not found');
    return;
}

const safeMethodCounts = {
    UPI: Number(methodCounts?.UPI || 0),
    FacePay: Number(methodCounts?.FacePay || 0),
    Card: Number(methodCounts?.Card || 0)
};

const hasData = Object.values(safeMethodCounts).some(v => v > 0);

// Log only (no blocking)
if (!hasData) {
    console.warn('[Chart] No payment data available — rendering empty chart');
}

// Always render chart (even if empty)
if (chartPayment) chartPayment.destroy();

chartPayment = new Chart(paymentCanvas, {
    type: 'pie',
    data: {
        labels: Object.keys(safeMethodCounts),
        datasets: [{
            data: Object.values(safeMethodCounts),
            backgroundColor: ['#1a73e8', '#34a853', '#fbbc04'],
            borderWidth: 2,
            borderColor: '#fff'
        }]
    },
    options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: { position: 'bottom' }
        }
    }
});

    if (chartActivity) chartActivity.destroy();
    chartActivity = new Chart(document.getElementById('chartActivity'), {
        type: 'bar',
        data: {
            labels: monthLabels,
            datasets: [{
                label: 'Invoices Issued',
                data: Object.values(monthData),
                backgroundColor: 'rgba(26,115,232,0.7)',
                borderRadius: 4
            }]
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
        }
    });
}

// ── Profile form ─────────────────────────────────────────────
function populateProfile(profile) {
    if (!profile) return;
    const fields = ['email', 'businessName', 'ownerName', 'phone', 'address', 'city', 'state', 'pinCode',
        'upiId', 'bankName', 'accountHolderName', 'accountNumber', 'ifscCode'];

    fields.forEach(k => {
        const el = document.getElementById(`p-${k}`);
        if (!el) return;

        el.value = profile[k] || '';

        const nonEditableFields = ["email", "businessName"];
        const bankLockedFields = ["accountNumber", "ifscCode"];

        const hasValue = profile[k] !== null && profile[k] !== '';

        if (nonEditableFields.includes(k)) {
            el.disabled = true;
        }
        // 🔐 lock bank fields ONLY if already filled
        else if (bankLockedFields.includes(k) && hasValue) {
            el.disabled = true;
        }
        else {
            el.disabled = false;
        }
    });

    const gstEl = document.getElementById('p-gstRegistered');
    if (gstEl) {
        gstEl.checked = !!profile.gstRegistered;
        toggleGstinField(!!profile.gstRegistered);
    }
    const gstinEl = document.getElementById('p-gstin');
    if (gstinEl) gstinEl.value = profile.gstin || '';

    updateProfileSectionStatuses(profile);
}

function updateProfileSectionStatuses(profile) {
    const isCompleted = profile && profile.profileCompleted;

    const lang = localStorage.getItem('billme_lang') || 'en';
    const dict = typeof translations !== 'undefined' ? (translations[lang] || translations.en) : null;

    const cSt = document.getElementById('psc-contact-status');
    const bSt = document.getElementById('psc-bank-status');

    if (cSt) {
        cSt.textContent = dict ? (isCompleted ? dict.dash_status_complete : dict.dash_status_incomplete) : (isCompleted ? 'Complete' : 'Incomplete');
        cSt.className = `psc-status${isCompleted ? ' ok' : ''}`;
    }
    if (bSt) {
        bSt.textContent = dict ? (isCompleted ? dict.dash_status_complete : dict.dash_status_incomplete) : (isCompleted ? 'Complete' : 'Incomplete');
        bSt.className = `psc-status${isCompleted ? ' ok' : ''}`;
    }
}

function toggleGstinField(on) {
    const grp = document.getElementById('gstin-group');
    const msg = document.getElementById('gst-info-msg');
    if (grp) grp.style.display = on ? 'block' : 'none';
    if (msg) {
        msg.style.display = on ? 'none' : 'flex';
    }
}

function bindProfileForm() {
    document.getElementById('p-gstRegistered')?.addEventListener('change', e => {
        toggleGstinField(e.target.checked);
    });

    document.getElementById('saveProfileBtn')?.addEventListener('click', async () => {
        const btn = document.getElementById('saveProfileBtn');
        btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
        try {
            const payload = {
                phone: document.getElementById('p-phone').value.trim(),
                address: document.getElementById('p-address').value.trim(),
                city: document.getElementById('p-city').value.trim(),
                state: document.getElementById('p-state').value.trim(),
                pinCode: document.getElementById('p-pinCode').value.trim(),
                upiId: document.getElementById('p-upiId').value.trim(),
                bankName: document.getElementById('p-bankName').value.trim(),
                accountHolderName: document.getElementById('p-accountHolderName').value.trim(),
                accountNumber: document.getElementById('p-accountNumber').value.trim(),
                ifscCode: document.getElementById('p-ifscCode').value.trim(),
                gstRegistered: document.getElementById('p-gstRegistered').checked,
                gstin: document.getElementById('p-gstin').value.trim() || null,
            };
            const updated = await API.merchant.updateProfile(payload);
            merchantProfile = updated;
            renderSidebar(updated);
            renderProfileBanner(updated);
            updateProfileSectionStatuses(updated);
            showToast('Profile updated successfully!', 'success');
        } catch (e) {
            showToast(safeErrorMessage(e), 'error');
        } finally {
            btn.disabled = false; btn.innerHTML = '<i class="fas fa-save"></i> Save Changes';
        }
    });
}

// ── Products ─────────────────────────────────────────────────
function renderProducts() {
    const tbody = document.getElementById('productsBody');
    if (!productList.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted" style="padding:32px;"><i class="fas fa-box" style="font-size:32px;display:block;margin-bottom:8px;opacity:0.3;"></i>No products yet. Click "Add Product" to get started.</td></tr>';
        return;
    }
    tbody.innerHTML = productList.map(p => `
        <tr>
            <td><strong>${esc(p.name)}</strong></td>
            <td>₹${(p.price || 0).toFixed(2)}</td>
            <td><span class="badge badge-info">${p.gstRate || 0}%</span></td>
            <td>${p.barcode ? esc(p.barcode) : '<span class="text-muted">—</span>'}</td>
            <td>
                <button class="action-btn red" onclick="deleteProduct(${p.id})" title="Delete">
                    <i class="fas fa-trash"></i>
                </button>
            </td>
        </tr>
    `).join('');
}

function bindProductForm() {
    document.getElementById('addProductBtn')?.addEventListener('click', () => {
        const form = document.getElementById('addProductForm');
        form.style.display = form.style.display === 'none' ? 'block' : 'none';
        applyGstRuleToProductForm();
    });

    document.getElementById('cancelProdBtn')?.addEventListener('click', () => {
        document.getElementById('addProductForm').style.display = 'none';
    });

    document.getElementById('saveProdBtn')?.addEventListener('click', saveProduct);
}

function applyGstRuleToProductForm() {
    const gstSelect = document.getElementById('prod-gstRate');
    const hint = document.getElementById('prod-gst-hint');
    const warning = document.getElementById('prod-gst-warning');
    const isGst = merchantProfile?.gstRegistered === true;

    if (isGst) {
        gstSelect.disabled = false;
        hint.textContent = 'Select the GST slab applicable to this product per government regulations.';
        warning.style.display = 'none';
    } else {
        gstSelect.disabled = true;
        gstSelect.value = '0';
        hint.textContent = 'GST is not applicable because your business is not GST registered.';
        warning.style.display = 'none';
    }

    // If someone somehow manually puts gstRate > 0 while not registered, warn
    gstSelect.addEventListener('change', () => {
        if (!isGst && parseInt(gstSelect.value) > 0) {
            warning.style.display = 'flex';
        } else {
            warning.style.display = 'none';
        }
    });
}

async function saveProduct() {
    const name = document.getElementById('prod-name').value.trim();
    const price = parseFloat(document.getElementById('prod-price').value);
    const barcode = document.getElementById('prod-barcode').value.trim();
    const gstRate = merchantProfile?.gstRegistered
        ? parseInt(document.getElementById('prod-gstRate').value)
        : 0;

    if (!name) { showToast('Product name is required', 'warning'); return; }
    if (isNaN(price) || price < 0) { showToast('Enter a valid price', 'warning'); return; }

    const btn = document.getElementById('saveProdBtn');
    btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
    try {
        const payload = { name, price, barcode: barcode || null, gstRate };
        const newProd = await API.merchant.createProduct(payload);
        productList.push(newProd);
        renderProducts();
        document.getElementById('stat-products').textContent = productList.length;
        document.getElementById('addProductForm').style.display = 'none';
        ['prod-name', 'prod-price', 'prod-barcode'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.value = '';
        });
        const gstSelect = document.getElementById('prod-gstRate');
        if (gstSelect) gstSelect.value = '0';
        showToast('Product created!', 'success');
    } catch (e) {
        showToast(safeErrorMessage(e), 'error');
    } finally {
        btn.disabled = false; btn.innerHTML = '<i class="fas fa-save"></i> Save Product';
    }
}

async function deleteProduct(id) {
    if (!confirm('Delete this product?')) return;
    try {
        await API.merchant.deleteProduct(id);
        productList = productList.filter(p => p.id !== id);
        renderProducts();
        document.getElementById('stat-products').textContent = productList.length;
        showToast('Product deleted', 'success');
    } catch (e) {
        showToast(e.message || 'Failed to delete product', 'error');
    }
}

// ── Invoices ─────────────────────────────────────────────────
function renderInvoices() {
    const tbody = document.getElementById('invoicesBody');
    if (!invoiceList.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted" style="padding:32px;"><i class="fas fa-file-invoice" style="font-size:32px;display:block;margin-bottom:8px;opacity:0.3;"></i>No invoices yet.</td></tr>';
        return;
    }
    tbody.innerHTML = invoiceList.map(inv => `
        <tr>
            <td><strong>${esc(inv.invoiceNumber || '#' + inv.invoiceId)}</strong></td>
            <td>₹${(inv.amount || 0).toFixed(2)}</td>
            <td><span class="invoice-status inv-${inv.status}">${inv.status || '—'}</span></td>
            <td>${inv.paymentMethod || '<span class="text-muted">—</span>'}</td>
            <td>${inv.issuedAt ? new Date(inv.issuedAt).toLocaleDateString('en-IN') : '—'}</td>
            <td>
                <button class="action-btn blue" onclick="previewInvoice(${inv.invoiceId})" title="View Details"><i class="fas fa-info-circle"></i></button>
                <button class="action-btn purple" onclick="copyPaymentLink('${inv.invoiceNumber}', '${inv.paymentToken}')" title="Copy Payment Link"><i class="fas fa-copy"></i></button>
                ${inv.status === 'UNPAID' ? `<button class="action-btn orange" onclick="editInvoice(${inv.invoiceId})" title="Edit"><i class="fas fa-edit"></i></button>` : ''}
                <button class="action-btn green" onclick="downloadInvoicePdf(${inv.invoiceId},'${esc(inv.invoiceNumber || inv.invoiceId)}')" title="Download PDF"><i class="fas fa-download"></i></button>
            </td>
        </tr>
    `).join('');

    renderRefundRequests();
}

function renderRefundRequests() {
    const tbody = document.getElementById('refundRequestsBody');
    if (!tbody) return;

    const reqs = invoiceList.filter(inv => inv.status === 'REFUND_REQUESTED');
    if (!reqs.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted" style="padding:16px;">No pending refund requests.</td></tr>';
        return;
    }

    tbody.innerHTML = reqs.map(inv => `
        <tr>
            <td><strong>${esc(inv.invoiceNumber || '#' + inv.invoiceId)}</strong></td>
            <td>₹${(inv.amount || 0).toFixed(2)}</td>
            <td>${inv.paymentMethod || '—'}</td>
            <td>${inv.issuedAt ? new Date(inv.issuedAt).toLocaleDateString() : '—'}</td>
            <td>
                <button class="btn btn-success btn-sm" style="margin-right: 5px;" onclick="approveRefund(${inv.invoiceId})">Approve</button>
                <button class="btn btn-danger btn-sm" onclick="rejectRefund(${inv.invoiceId})">Reject</button>
            </td>
        </tr>
    `).join('');
}

async function approveRefund(id) {
    const inv = invoiceList.find(i => i.invoiceId === id);
    const method = inv?.paymentMethod || 'payment';
    const methodLabel = method === 'FACE_PAY' ? 'FACE PAY (wallet refund)' :
        method === 'UPI_PAY' ? 'UPI (Razorpay refund)' :
            method === 'CARD' ? 'Card (Razorpay refund)' : method;

    if (!confirm(`Approve refund for Invoice ${inv?.invoiceNumber || '#' + id}?\n\nMethod: ${methodLabel}\n\nThis will reverse the payment to the customer.`)) return;

    try {
        await API.payment.approveRefund(id);
        showToast('Refund approved and processed successfully!', 'success');
        loadDashboard();
    } catch (e) {
        console.error('[approveRefund] error:', e);
        const msg = e.message || 'Error occurred while approving refund';
        showToast(msg, 'error');
    }
}

async function rejectRefund(id) {
    const inv = invoiceList.find(i => i.invoiceId === id);
    if (!confirm(`Reject refund request for Invoice ${inv?.invoiceNumber || '#' + id}?\n\nThe customer will be notified.`)) return;

    try {
        await API.payment.rejectRefund(id);
        showToast('Refund request rejected. Customer has been notified.', 'info');
        loadDashboard();
    } catch (e) {
        console.error('[rejectRefund] error:', e);
        const msg = e.message || 'Error occurred while rejecting refund';
        showToast(msg, 'error');
    }
}

function previewInvoice(id) {
    const inv = invoiceList.find(i => i.invoiceId === id);
    if (!inv) return;
    currentInvId = id;

    document.getElementById('modalInvTitle').textContent = `Invoice ${inv.invoiceNumber || '#' + id}`;

    const items = (inv.items || []).map(item => `
        <tr>
            <td>${esc(item.productName || 'Item')}</td>
            <td class="text-right">${item.quantity || 1}</td>
            <td class="text-right">₹${(item.unitPrice || 0).toFixed(2)}</td>
            <td class="text-right">${item.gstRate || 0}%</td>
            <td class="text-right fw-600">₹${(item.lineTotal || item.unitPrice || 0).toFixed(2)}</td>
        </tr>
    `).join('');

    document.getElementById('modalInvBody').innerHTML = `
        <div class="inv-detail-row"><span class="inv-detail-row__label">Invoice #</span><span class="inv-detail-row__val">${inv.invoiceNumber || id}</span></div>
        <div class="inv-detail-row"><span class="inv-detail-row__label">Customer</span><span class="inv-detail-row__val">${esc(inv.customerName || '—')} (${esc(inv.customerEmail || '—')})</span></div>
        <div class="inv-detail-row"><span class="inv-detail-row__label">Status</span><span class="invoice-status inv-${inv.status}">${inv.status}</span></div>
        <div class="inv-detail-row"><span class="inv-detail-row__label">Issued At</span><span class="inv-detail-row__val">${inv.issuedAt ? new Date(inv.issuedAt).toLocaleString('en-IN') : '—'}</span></div>
        ${inv.paidAt ? `<div class="inv-detail-row"><span class="inv-detail-row__label">Paid At</span><span class="inv-detail-row__val">${new Date(inv.paidAt).toLocaleString('en-IN')}</span></div>` : ''}
        ${items.length ? `
        <div style="margin:16px 0 8px;font-weight:600;font-size:14px;">Items</div>
        <div class="data-table-wrapper"><table class="data-table">
            <thead><tr><th>Product</th><th>Qty</th><th>Unit Price</th><th>GST</th><th>Total</th></tr></thead>
            <tbody>${items}</tbody>
        </table></div>` : ''}
        <div class="mt-2">
            <div class="inv-detail-row"><span class="inv-detail-row__label">Subtotal</span><span class="inv-detail-row__val">₹${(inv.subtotal || 0).toFixed(2)}</span></div>
            ${inv.cgstAmount ? `<div class="inv-detail-row"><span class="inv-detail-row__label">CGST</span><span class="inv-detail-row__val">₹${(inv.cgstAmount || 0).toFixed(2)}</span></div>` : ''}
            ${inv.sgstAmount ? `<div class="inv-detail-row"><span class="inv-detail-row__label">SGST</span><span class="inv-detail-row__val">₹${(inv.sgstAmount || 0).toFixed(2)}</span></div>` : ''}
            ${inv.igstAmount ? `<div class="inv-detail-row"><span class="inv-detail-row__label">IGST</span><span class="inv-detail-row__val">₹${(inv.igstAmount || 0).toFixed(2)}</span></div>` : ''}
            ${inv.processingFee ? `<div class="inv-detail-row"><span class="inv-detail-row__label">Processing Fee</span><span class="inv-detail-row__val">₹${(inv.processingFee || 0).toFixed(2)}</span></div>` : ''}
        </div>
        <div class="inv-total-row">
            <span>Total Payable</span>
            <span>₹${(inv.totalPayable || inv.amount || 0).toFixed(2)}</span>
        </div>
    `;

    openModal('invoiceModal');
}

async function downloadInvoicePdf(id, invNum) {
    try {
        showToast('Downloading PDF...', 'info');
        const blob = await API.merchant.downloadInvoicePdf(id);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = `invoice-${invNum}.pdf`;
        document.body.appendChild(a); a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        showToast('PDF downloaded!', 'success');
    } catch (e) {
        showToast(e.message || 'PDF download failed', 'error');
    }
}

function payInvoice(id) {
    // Merchants cannot pay customer invoices directly from the dashboard anymore.
    // Preserved method for backward compatibility if any hidden reference exists.
    showToast("Payment must be initiated by the Customer.", "info");
}

function bindInvoiceForm() {
    document.getElementById('createInvoiceBtn')?.addEventListener('click', () => {
        document.getElementById('invoiceListCard').style.display = 'none';
        document.getElementById('createInvoiceForm').style.display = 'block';
        renderInvoiceItemRow(); // add first row
    });

    document.getElementById('backToListBtn')?.addEventListener('click', () => {
        document.getElementById('invoiceListCard').style.display = 'block';
        document.getElementById('createInvoiceForm').style.display = 'none';
    });
    document.getElementById('cancelInvoiceBtn')?.addEventListener('click', () => {
        document.getElementById('invoiceListCard').style.display = 'block';
        document.getElementById('createInvoiceForm').style.display = 'none';
    });

    document.getElementById('addInvItemBtn')?.addEventListener('click', renderInvoiceItemRow);
    document.getElementById('submitInvoiceBtn')?.addEventListener('click', submitInvoice);

    document.getElementById('modalDownloadBtn')?.addEventListener('click', () => {
        if (currentInvId) {
            const inv = invoiceList.find(i => i.invoiceId === currentInvId);
            downloadInvoicePdf(currentInvId, inv?.invoiceNumber || currentInvId);
        }
    });

    document.getElementById('exportBalanceSheetBtn')?.addEventListener('click', async () => {
        try {
            showToast('Generating POI Balance Sheet PDF...', 'info');
            const blob = await API.wallet.exportBalanceSheet();
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url; a.download = `balance_sheet_${new Date().getTime()}.xlsx`;
            document.body.appendChild(a); a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
            showToast('Excel document exported!', 'success');
        } catch (e) {
            showToast(e.message || 'Export failed', 'error');
        }
    });
}

function renderInvoiceItemRow() {
    const container = document.getElementById('inv-items-container');
    const row = document.createElement('div');
    row.className = 'grid-2 mb-2';
    row.style.cssText = 'grid-template-columns:2fr 1fr 1fr auto;gap:8px;align-items:end;';
    row.innerHTML = `
        <div class="form-group mb-0">
            <label class="form-label">Product</label>
            <select class="form-input inv-prod-select" onchange="updateRowPrice(this)">
                <option value="">— Select —</option>
                ${productList.map(p => `<option value="${p.id}" data-price="${p.price}" data-gst="${p.gstRate || 0}">${esc(p.name)} (₹${p.price})</option>`).join('')}
            </select>
        </div>
        <div class="form-group mb-0">
            <label class="form-label">Qty</label>
            <input type="number" class="form-input inv-qty" value="1" min="1" onchange="calculateDraftTotal()">
        </div>
        <div class="form-group mb-0">
            <label class="form-label">Line Total (Auto)</label>
            <div class="form-input inv-unit-price-display" style="background:#f1f3f4; font-weight:600;">₹0.00</div>
            <input type="hidden" class="inv-unit-price" value="0">
        </div>
        <button type="button" class="btn btn-danger btn-sm" style="margin-bottom:20px;" onclick="this.closest('.grid-2').remove(); calculateDraftTotal();"><i class="fas fa-times"></i></button>
    `;
    container.appendChild(row);
}

window.updateRowPrice = function (sel) {
    const row = sel.closest('.grid-2');
    const price = parseFloat(sel.selectedOptions[0]?.dataset.price || 0);
    const display = row.querySelector('.inv-unit-price-display');
    const hidden = row.querySelector('.inv-unit-price');
    if (display) display.textContent = `₹${price.toFixed(2)}`;
    if (hidden) hidden.value = price;
    calculateDraftTotal();
};

window.calculateDraftTotal = function () {
    let total = 0;
    document.querySelectorAll('#inv-items-container .grid-2').forEach(row => {
        const qty = parseInt(row.querySelector('.inv-qty').value) || 0;
        const price = parseFloat(row.querySelector('.inv-unit-price').value) || 0;
        const lineTotal = qty * price;
        total += lineTotal;

        const display = row.querySelector('.inv-unit-price-display');
        if (display) {
            display.textContent = `₹${lineTotal.toFixed(2)}`;
        }
    });
    // Optional: show a live draft total somewhere if UI has it
};

async function submitInvoice() {
    console.log("🚀 submitInvoice triggered");

    const btn = document.getElementById('submitInvoiceBtn');
    if (!btn) return;

    const custEmailEl = document.getElementById('inv-custEmail');
    const custNameEl = document.getElementById('inv-custName');
    if (!custEmailEl) return;

    const custEmail = custEmailEl.value.trim();
    const custName = custNameEl ? custNameEl.value.trim() : "";

    const itemRows = document.querySelectorAll('#inv-items-container .grid-2');
    const items = [];

    itemRows.forEach(row => {
        const sel = row.querySelector('.inv-prod-select');
        const qtyInput = row.querySelector('.inv-qty');
        if (!sel || !qtyInput) return;

        const productId = parseInt(sel.value);
        const qty = parseInt(qtyInput.value);

        if (!productId || isNaN(productId)) return;
        if (!qty || qty <= 0) return;

        items.push({
            productId: productId,
            quantity: qty
        });
    });

    if (!custEmail) {
        showToast('Customer email is required', 'warning');
        return;
    }

    if (items.length === 0) {
        showToast('Add at least one valid product', 'warning');
        return;
    }

    const payload = {
        customerEmail: custEmail,
        customerName: custName,
        items: items
    };

    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Processing...';

    try {
        const response = await fetch(window.API_BASE_URL + "/merchant/invoices", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": "Bearer " + localStorage.getItem("billme_token")
            },
            body: JSON.stringify(payload)
        });

        const responseText = await response.text();
        let responseData = null;
        try {
            responseData = responseText ? JSON.parse(responseText) : null;
        } catch (e) {
            console.warn("Non-JSON response received:", responseText);
        }

        if (!response.ok) {
            const isEmailError = responseText.includes("Failed to send invoice email") ||
                responseText.includes("Too many emails") ||
                responseText.includes("Email error");

            if (isEmailError) {
                console.warn("⚠️ Email failed but invoice created successfully");
                showToast("Invoice created but email failed ⚠️", "warning");
                // Continue to success UI flow
            } else {
                throw new Error(responseData?.message || responseText || `Error ${response.status}`);
            }
        } else {
            showToast('Invoice created successfully!', 'success');
        }

        // --- SUCCESS UI FLOW (ALWAYS RUN IF CREATED) ---

        // 1. Reset Form
        custEmailEl.value = '';
        if (custNameEl) custNameEl.value = '';
        const itemsContainer = document.getElementById('inv-items-container');
        if (itemsContainer) {
            itemsContainer.innerHTML = '';
            renderInvoiceItemRow(); // Add one fresh row
        }

        // 2. Refresh Dashboard Data
        try {
            const [newInvoices, newPm] = await Promise.all([
                API.merchant.getInvoices().catch(() => []),
                API.merchant.getPaymentMethods().catch(() => ({ UPI: 0, FACE_PAY: 0, CARD: 0 }))
            ]);
            invoiceList = newInvoices || [];
            renderInvoices();
            renderCharts(invoiceList, newPm);
        } catch (refreshErr) {
            console.error("Dashboard refresh failed:", refreshErr);
        }

        // 3. Navigate back to list
        const listCard = document.getElementById('invoiceListCard');
        const formSection = document.getElementById('createInvoiceForm');
        if (listCard) listCard.style.display = 'block';
        if (formSection) formSection.style.display = 'none';

    } catch (e) {
        console.error("🔥 Invoice Creation Error:", e);
        showToast(safeErrorMessage(e), 'error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-paper-plane"></i> Create Invoice';
    }
}

// ── Transactions ─────────────────────────────────────────────
function bindTransactions() {
    document.getElementById('applyTxFilter')?.addEventListener('click', () => {
        txPage = 0; loadTransactions();
    });
    document.getElementById('txPrev')?.addEventListener('click', () => {
        console.log("PREV CLICKED");
        if (txPage > 0) { txPage--; loadTransactions(); }
    });
    document.getElementById('txNext')?.addEventListener('click', () => {
        console.log("NEXT CLICKED");
        if (txPage < txTotalPages - 1) { txPage++; loadTransactions(); }
    });
}

let isTxLoading = false;
async function loadTransactions() {
    if (isTxLoading) return;

    isTxLoading = true;
    const btnNext = document.getElementById('txNext');
    const btnPrev = document.getElementById('txPrev');
    if (btnPrev) btnPrev.disabled = true;
    if (btnNext) btnNext.disabled = true;

    const tbody = document.getElementById('txBody');
    tbody.innerHTML = '<tr><td colspan="7" class="text-center"><div class="spinner spinner-sm" style="margin:20px auto;"></div></td></tr>';
    try {
        const params = {
            page: txPage, size: 10,
            type: document.getElementById('txFilter-type').value || undefined,
            status: document.getElementById('txFilter-status').value || undefined,
        };
        const data = await API.wallet.getTransactions(params);
        console.log("RAW RESPONSE:", data);
        console.log("txPage:", txPage, "totalPages:", data?.totalPages);

        txTotalPages = Math.max(1, data?.totalPages || 1);
        document.getElementById('txPageInfo').textContent =
            `Page ${txPage + 1} of ${txTotalPages} (${data?.totalElements || 0} total)`;

        if (btnPrev) btnPrev.disabled = txPage === 0;
        if (btnNext) btnNext.disabled = txPage >= txTotalPages - 1;

        console.log("Next disabled (before force):", btnNext ? btnNext.disabled : "null");
        // txTotalPages = 5; // FORCE TEST
        if (btnNext) btnNext.disabled = txPage >= txTotalPages - 1;
        console.log("Forced txTotalPages to 5, Next disabled:", btnNext ? btnNext.disabled : "null");

        if (!data || !Array.isArray(data.content) || data.content.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted" style="padding:32px;">No transactions found.</td></tr>';
            return;
        }

        tbody.innerHTML = data.content.map(tx => {
            const isCredit = tx.direction === 'CREDIT';
            return `
            <tr>
                <td><code style="font-size:12px;">#${tx.transactionId || '—'}</code></td>
                <td>
                    <span class="badge ${isCredit ? 'badge-success' : 'badge-danger'}">
                        <i class="fas fa-arrow-${isCredit ? 'down' : 'up'}"></i>
                        ${tx.direction || '—'}
                    </span>
                </td>
                <td><strong style="color:${isCredit ? 'var(--secondary)' : 'var(--danger)'};">
                    ${isCredit ? '+' : '-'}₹${(tx.amount || 0).toFixed(2)}
                </strong></td>
                <td>${tx.type || '—'}</td>
                <td><span class="badge ${tx.status === 'SUCCESS' ? 'badge-success' : tx.status === 'PENDING' ? 'badge-warning' : 'badge-danger'}">${tx.status || '—'}</span></td>
                <td>${esc(tx.counterparty || '—')}</td>
                <td>${tx.timestamp ? new Date(tx.timestamp).toLocaleString('en-IN') : '—'}</td>
            </tr>`;
        }).join('');
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center text-muted" style="padding:32px;">Failed to load transactions: ${e.message}</td></tr>`;
        if (btnPrev) btnPrev.disabled = txPage === 0;
        if (btnNext) btnNext.disabled = true;
    } finally {
        isTxLoading = false;
    }
}

// ── Section navigation ────────────────────────────────────────
function bindNav() {
    document.querySelectorAll('.nav-item a').forEach(link => {
        link.addEventListener('click', function (e) {
            e.preventDefault();

            const parent = this.closest('.nav-item');
            const section = parent?.getAttribute('data-section');

            console.log('[Merchant] Navigate →', section);

            if (!section) return;

            // remove active from all
            document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
            parent.classList.add('active');

            // show correct section
            document.querySelectorAll('.section-page').forEach(sec => sec.classList.remove('active'));

            const target = document.getElementById(`sec-${section}`);
            if (target) target.classList.add('active');

            // update title
            const title = document.getElementById('pageTitle');
            if (title) title.textContent = section.charAt(0).toUpperCase() + section.slice(1);

            // close sidebar on mobile
            toggleSidebar(false);

            // 🔄 LOAD TRANSACTIONS IF SECTION OPENED
            if (section === 'transactions') {
                loadTransactions();
            }
        });
    });

    // Handle initial section state (on load)
    const activeSection = document.querySelector('.nav-item.active')?.getAttribute('data-section');
    if (activeSection === 'transactions') {
        loadTransactions();
    }
}

// toggleSidebar is defined at the TOP of this file — see above.
// No duplicate definition here.

function bindSidebarToggle() {
    console.log('[Merchant] Binding sidebar toggle...');

    const menuBtn = document.getElementById('menuToggleBtn');
    const overlay = document.getElementById('sidebarOverlay');
    const sidebar = document.getElementById('sidebar');

    console.log('menuBtn:', menuBtn);
    console.log('overlay:', overlay);
    console.log('sidebar:', sidebar);

    // ✅ MENU BUTTON FIX
    if (menuBtn) {
        menuBtn.addEventListener('click', (e) => {
            e.preventDefault();
            console.log('[Merchant] Menu clicked');
            toggleSidebar();
        });
    } else {
        console.error('[Merchant] menuToggleBtn NOT FOUND');
    }

    // ✅ OVERLAY FIX
    if (overlay) {
        overlay.addEventListener('click', () => {
            console.log('[Merchant] Overlay clicked');
            toggleSidebar(false);
        });
    }

    // ✅ SAFETY: close sidebar on resize (optional but pro)
    window.addEventListener('resize', () => {
        if (window.innerWidth > 768 && sidebar) {
            sidebar.classList.remove('open');
            overlay?.classList.remove('active');
        }
    });
}

function bindMisc() {
    document.getElementById('logoutBtn')?.addEventListener('click', async () => {
        const rt = localStorage.getItem('billme_refresh');
        if (rt) {
            try {
                const logoutResult = API.auth.logout(rt);
                if (logoutResult && typeof logoutResult.then === 'function') {
                    await logoutResult;
                }
            } catch (e) { /* logout errors are non-critical */ }
        }
        clearAuth();
        window.location.href = '../index.html';
    });
    document.getElementById('refreshBtn')?.addEventListener('click', () => {
        loadDashboard();
    });
}

// ── Collapse accordion ────────────────────────────────────────
window.toggleSection = function (header) {
    const body = header.nextElementSibling;
    body.classList.toggle('open');
};

// ── Modal helpers ─────────────────────────────────────────────
window.openModal = function (id) {
    const m = document.getElementById(id);
    m.style.display = 'flex';
    setTimeout(() => m.classList.add('active'), 10);
};
window.closeModal = function (id) {
    const m = document.getElementById(id);
    m.classList.remove('active');
    setTimeout(() => m.style.display = 'none', 250);
};
document.querySelectorAll('.modal-backdrop').forEach(m => {
    m.addEventListener('click', e => { if (e.target === m) closeModal(m.id); });
});

// ── Safe HTML escape ──────────────────────────────────────────
function esc(str) {
    if (str == null) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// Expose globals used in inline HTML
window.previewInvoice = previewInvoice;
window.editInvoice = function (id) {
    const inv = invoiceList.find(i => i.invoiceId === id);
    if (!inv) return;
    if (inv.status !== 'UNPAID') {
        showToast('Only UNPAID invoices can be edited.', 'warning');
        return;
    }
    currentInvId = id;

    // Switch to form
    document.getElementById('invoiceListCard').style.display = 'none';
    const form = document.getElementById('createInvoiceForm');
    form.style.display = 'block';
    form.querySelector('h3').textContent = `Edit Invoice ${inv.invoiceNumber || '#' + id}`;
    document.getElementById('submitInvoiceBtn').innerHTML = '<i class="fas fa-save"></i> Update Invoice';

    // Populate
    document.getElementById('inv-custEmail').value = inv.customerEmail || '';
    document.getElementById('inv-custName').value = inv.customerName || '';

    const container = document.getElementById('inv-items-container');
    container.innerHTML = '';

    if (inv.items && inv.items.length) {
        inv.items.forEach(item => {
            renderInvoiceItemRow();
            const row = container.lastElementChild;
            const sel = row.querySelector('.inv-prod-select');

            if (item.productId) {
                sel.value = item.productId;
            } else {
                for (let opt of sel.options) {
                    if (opt.text.startsWith(item.productName)) {
                        sel.value = opt.value;
                        break;
                    }
                }
            }
            row.querySelector('.inv-qty').value = item.quantity || 1;
            updateRowPrice(sel);
        });
    } else {
        renderInvoiceItemRow();
    }
};
window.copyPaymentLink = function (num, token) {
    if (!num || !token) {
        showToast('Cannot generate link for this invoice', 'warning');
        return;
    }
    const link = `${window.location.origin}/frontend/Face_Payment-Shravani_UI/pay-invoice.html?num=${num}&token=${token}`;

    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(link).then(() => {
            showToast('Payment link copied to clipboard!', 'success');
        }).catch(() => {
            prompt("Copy payment link:", link);
        });
    } else {
        prompt("Copy payment link:", link);
    }
};
window.downloadInvoicePdf = downloadInvoicePdf;
window.payInvoice = payInvoice;
window.deleteProduct = deleteProduct;
window.approveRefund = approveRefund;
window.rejectRefund = rejectRefund;