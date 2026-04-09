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
let isPopulatingProfile = false; // Guard for programmatic updates
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
    bindProfileDirtyCheck();
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

    // 🔄 Auto-refresh dashboard stats every 60 seconds (Singleton Guarded)
    if (!window.pollingIntervalId) {
        window.pollingIntervalId = setInterval(() => {
            console.log("🔄 Auto-refreshing financial stats...");
            refreshWalletStats();
        }, 60000);
    }
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
            API.wallet.getSummary({ range: 'monthly' }).catch(() => null),
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
    const statusCounts = {
    PAID: 0,
    PENDING: 0,
    UNPAID: 0,
    CANCELLED: 0,
    REFUND_REQUESTED: 0,
    REFUNDED: 0
};

invoices.forEach(inv => {
    const s = inv.status?.toUpperCase();

    if (!s) {
        statusCounts.UNPAID++;
        return;
    }

    if (statusCounts.hasOwnProperty(s)) {
        statusCounts[s]++;
    } else {
        console.warn('[Chart] Unknown status:', s);
    }
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
               backgroundColor: [
    '#34a853', // PAID (green)
    '#fbbc04', // PENDING (yellow)
    '#ea4335', // UNPAID (red)
    '#9aa0a6', // CANCELLED (gray)
    '#4285f4', // REFUND_REQUESTED (blue)
    '#8e24aa'  // REFUNDED (purple)
],
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
    isPopulatingProfile = true; try {
    const fields = ['email', 'businessName', 'ownerName', 'phone', 'address', 'city', 'state', 'pinCode',
        'upiId', 'bankName', 'accountHolderName', 'accountNumber', 'ifscCode'];

    fields.forEach(k => {
        const el = document.getElementById(`p-${k}`);
        if (!el) return;

        // 🛡️ ZERO-REGRESSION: Protect unsaved user input (Dirty Check)
        if (el.dataset.dirty === "true" && el.value) return;

        el.value = profile[k] || '';

        const nonEditableFields = ["email", "businessName"];
        const bankLockedFields = ["bankName", "accountHolderName", "accountNumber", "ifscCode"];

        if (nonEditableFields.includes(k) || bankLockedFields.includes(k)) {
            el.disabled = true;
        } else {
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
    } finally { isPopulatingProfile = false; }
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

            // 🛡️ ZERO-REGRESSION: Non-destructive Validation
            const phoneRegex = /^\d{10,13}$/;
            const accRegex = /^\d+$/;
            const ifscRegex = /^[A-Z]{4}0[A-Z0-9]{6}$/i;

            if (payload.phone && !phoneRegex.test(payload.phone)) {
                showToast("Please enter a valid phone number (10-13 digits).", "warning");
                btn.disabled = false; btn.innerHTML = '<i class="fas fa-save"></i> Save Changes';
                return;
            }
            if (payload.accountNumber && !accRegex.test(payload.accountNumber)) {
                showToast("Account number must contain only digits.", "warning");
                btn.disabled = false; btn.innerHTML = '<i class="fas fa-save"></i> Save Changes';
                return;
            }
            if (payload.ifscCode && !ifscRegex.test(payload.ifscCode)) {
                showToast("Invalid IFSC format (e.g., SBIN0001234).", "warning");
                btn.disabled = false; btn.innerHTML = '<i class="fas fa-save"></i> Save Changes';
                return;
            }
            const updated = await API.merchant.updateProfile(payload);
            merchantProfile = updated;
            
            // ✅ SUCCESS: Reset dirty state
            document.querySelectorAll('#sec-profile .form-input').forEach(el => el.dataset.dirty = "");
            
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

/**
 * Tracks user input to avoid overwriting modified fields during API sync.
 */
function bindProfileDirtyCheck() {
    console.log("[Merchant] Binding Profile dirty-check listeners...");
    const inputs = document.querySelectorAll('#sec-profile .form-input');
    inputs.forEach(el => {
        el.addEventListener('input', () => {
            if (!isPopulatingProfile) {
                el.dataset.dirty = "true";
            }
        });
    });
}

// ── Products ─────────────────────────────────────────────────
function renderProducts() {
    const tbody = document.getElementById('productsBody');
    if (!productList.length) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted" style="padding:32px;"><i class="fas fa-box" style="font-size:32px;display:block;margin-bottom:8px;opacity:0.3;"></i>No products yet. Click "Add Product" to get started.</td></tr>';
        return;
    }
    tbody.innerHTML = productList.map(p => {
        const stockQty = p.stockQuantity !== undefined ? p.stockQuantity : (p.quantity || 0);
        let stockHtml = `<span>${stockQty}</span>`;
        if (stockQty < 5) {
            stockHtml = `<span style="color:var(--danger);font-weight:700;">${stockQty} <i class="fas fa-circle-exclamation" title="Critical Stock"></i></span>`;
        } else if (stockQty < 10) {
            stockHtml = `<span style="color:#e67e22;font-weight:600;">${stockQty} <i class="fas fa-triangle-exclamation" title="Low Stock"></i></span>`;
        }
        return `
        <tr>
            <td><strong>${esc(p.name)}</strong></td>
            <td>₹${(p.price || 0).toFixed(2)}</td>
            <td>₹${(p.costPrice || 0).toFixed(2)}</td>
            <td>${stockHtml}</td>
            <td><span class="badge badge-info">${p.gstRate || 0}%</span></td>
            <td>${p.barcode ? esc(p.barcode) : '<span class="text-muted">—</span>'}</td>
            <td>
                <button class="action-btn red" onclick="deleteProduct(${p.id})" title="Delete">
                    <i class="fas fa-trash"></i>
                </button>
            </td>
        </tr>
    `}).join('');
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
    const costPrice = parseFloat(document.getElementById('prod-costPrice').value);
    const barcode = document.getElementById('prod-barcode').value.trim();
    const quantity = parseInt(document.getElementById('prod-quantity').value) || 0;
    const gstRate = merchantProfile?.gstRegistered
        ? parseInt(document.getElementById('prod-gstRate').value)
        : 0;

    if (!name) { showToast('Product name is required', 'warning'); return; }
    if (isNaN(price) || price < 0) { showToast('Enter a valid price', 'warning'); return; }
    if (isNaN(costPrice) || costPrice < 0) { showToast('Enter a valid cost price', 'warning'); return; }

    const btn = document.getElementById('saveProdBtn');
    btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
    try {
        const payload = { name, price, costPrice, quantity, stockQuantity: quantity, barcode: barcode || null, gstRate };
        const newProd = await API.merchant.createProduct(payload);
        productList.push(newProd);
        renderProducts();
        document.getElementById('stat-products').textContent = productList.length;
        document.getElementById('addProductForm').style.display = 'none';
        ['prod-name', 'prod-price', 'prod-costPrice', 'prod-barcode'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.value = '';
        });
        const qtyEl = document.getElementById('prod-quantity');
        if (qtyEl) qtyEl.value = '0';
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
    tbody.innerHTML = invoiceList.map(inv => {
        const id = (inv.id || inv.invoiceId)?.toString();
        if (!id) return '';
        return `
        <tr>
            <td><strong>${esc(inv.invoiceNumber || '#' + id)}</strong></td>
            <td>₹${(inv.amount || 0).toFixed(2)}</td>
            <td><span class="invoice-status inv-${inv.status}">${inv.status || '—'}</span></td>
            <td>${inv.paymentMethod || '<span class="text-muted">—</span>'}</td>
            <td>${inv.issuedAt ? new Date(inv.issuedAt).toLocaleDateString('en-IN') : '—'}</td>
            <td>
                <button class="action-btn blue" onclick="previewInvoice('${id}')" title="View Details"><i class="fas fa-info-circle"></i></button>
                <button class="action-btn purple" onclick="copyPaymentLink('${inv.invoiceNumber}', '${inv.paymentToken}')" title="Copy Payment Link"><i class="fas fa-copy"></i></button>
                ${inv.status === 'UNPAID' ? `<button class="action-btn orange" onclick="editInvoice('${id}')" title="Edit"><i class="fas fa-edit"></i></button>` : ''}
                <button class="action-btn green" onclick="downloadInvoicePdf('${id}','${esc(inv.invoiceNumber || id)}')" title="Download PDF"><i class="fas fa-download"></i></button>
            </td>
        </tr>
    `}).join('');

    renderRefundRequests();
}

function renderRefundRequests() {
    const tbody = document.getElementById('refundRequestsBody');
    if (!tbody) return;

    const reqs = invoiceList.filter(inv => inv.status === 'REFUND_REQUESTED');
    if (!reqs.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted" style="padding:16px;">No pending refund requests.</td></tr>';
        return;
    }

    tbody.innerHTML = reqs.map(inv => {
        const id = (inv.id || inv.invoiceId)?.toString();
        if (!id) return '';
        return `
        <tr>
            <td><strong>${esc(inv.invoiceNumber || '#' + id)}</strong></td>
            <td>₹${(inv.amount || 0).toFixed(2)}</td>
            <td>${inv.paymentMethod || '—'}</td>
            <td>${esc(inv.refundReason || '—')}</td>
            <td>${inv.refundRequestedAt ? new Date(inv.refundRequestedAt).toLocaleDateString() : (inv.issuedAt ? new Date(inv.issuedAt).toLocaleDateString() : '—')}</td>
            <td>
                <button class="btn btn-success btn-sm" style="margin-right: 5px;" onclick="approveRefund('${id}')">Approve</button>
                <button class="btn btn-danger btn-sm" onclick="rejectRefund('${id}')">Reject</button>
            </td>
        </tr>
    `}).join('');
}

async function approveRefund(id) {
    const invIndex = invoiceList.findIndex(i => (i.id || i.invoiceId)?.toString() === id.toString());
    const inv = invoiceList[invIndex];
    if (!inv) return;

    const snapshotStatus = inv.status;
    const method = inv?.paymentMethod || 'payment';
    const methodLabel = method === 'FACE_PAY' ? 'FACE PAY (wallet refund)' :
        method === 'UPI_PAY' ? 'UPI (Razorpay refund)' :
            method === 'CARD' ? 'Card (Razorpay refund)' : method;

    if (!confirm(`Approve refund for Invoice ${inv?.invoiceNumber || '#' + id}?\n\nMethod: ${methodLabel}\n\nThis will reverse the payment to the customer.`)) return;

    // Snapshot for possible rollback
    const prevInvoices = JSON.parse(JSON.stringify(invoiceList));

    try {
        // ⚡ CONTROLLED OPTIMISTIC UPDATE
        inv.status = 'REFUNDED';
        renderInvoices();

        await API.payment.approveRefund(id);
        showToast('Refund approved successfully!', 'success');
        
        // Lightweight sync for financial stats & transactions
        await refreshWalletStats();
        if (typeof loadTransactions === 'function') {
            await loadTransactions();
        }
    } catch (e) {
        console.error('[Merchant] approveRefund failed:', e);
        // 🔙 ROLLBACK
        invoiceList = prevInvoices;
        renderInvoices();
        showToast(e.message || 'Error occurred while approving refund', 'error');
    }
}

async function rejectRefund(id) {
    const invIndex = invoiceList.findIndex(i => (i.id || i.invoiceId)?.toString() === id.toString());
    const inv = invoiceList[invIndex];
    if (!inv) return;

    if (!confirm(`Reject refund request for Invoice ${inv?.invoiceNumber || '#' + id}?\n\nThe customer will be notified.`)) return;

    const prevInvoices = JSON.parse(JSON.stringify(invoiceList));

    try {
        // ⚡ CONTROLLED OPTIMISTIC UPDATE
        inv.status = 'REFUND_REJECTED';
        renderInvoices();

        await API.payment.rejectRefund(id);
        showToast('Refund request rejected.', 'info');
        
        // Lightweight sync for financial stats & transactions
        await refreshWalletStats();
        if (typeof loadTransactions === 'function') {
            await loadTransactions();
        }
    } catch (e) {
        console.error('[Merchant] rejectRefund failed:', e);
        // 🔙 ROLLBACK
        invoiceList = prevInvoices;
        renderInvoices();
        showToast(e.message || 'Error occurred while rejecting refund', 'error');
    }
}

/**
 * Lightweight Wallet Refresh (Zero-Regression)
 */
async function refreshWalletStats() {
    try {
        console.log("[Merchant] [API] Refreshing wallet stats...");
        const [bs, wl] = await Promise.all([
            API.wallet.getSummary({ range: 'monthly' }).catch(() => null),
            API.wallet.getWallet().catch(() => ({ balance: 0, escrowBalance: 0 }))
        ]);
        renderStatCards(productList, invoiceList, bs, wl);
        console.log("[Merchant] [API] Wallet stats refreshed.");
    } catch (e) {
        console.error("[Merchant] [API] Failed to refresh wallet stats:", e);
    }
}

function previewInvoice(id) {
    const inv = invoiceList.find(i => (i.id || i.invoiceId)?.toString() === id.toString());
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
        window.triggerBlobDownload(blob, `invoice-${invNum}.pdf`);
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
        console.log("[Merchant] [API] RAW TX RESPONSE:", data);

        // 🛡️ ROBUST RESPONSE PARSING
        let txArray = [];
        let totalPages = 1;
        let totalElements = 0;

        if (data && Array.isArray(data.content)) {
            // Spring Page format
            txArray = data.content;
            totalPages = data.totalPages || 1;
            totalElements = data.totalElements || 0;
        } else if (data && Array.isArray(data.transactions)) {
            // Alternative wrapper format
            txArray = data.transactions;
            totalPages = data.totalPages || 1;
            totalElements = data.totalElements || txArray.length;
        } else if (Array.isArray(data)) {
            // Naked array format
            txArray = data;
            totalPages = 1; // Cannot determine pagination from raw array unless API changed
            totalElements = txArray.length;
        } else {
            console.error("[Merchant] [API] UNKNOWN TX FORMAT:", data);
            txArray = [];
        }

        txTotalPages = Math.max(1, totalPages);
        document.getElementById('txPageInfo').textContent =
            `Page ${txPage + 1} of ${txTotalPages} (${totalElements} total)`;

        if (btnPrev) btnPrev.disabled = txPage === 0;
        if (btnNext) btnNext.disabled = txPage >= txTotalPages - 1;

        if (txArray.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted" style="padding:32px;">No transactions found.</td></tr>';
            return;
        }

        tbody.innerHTML = txArray.map(tx => {
            const isCredit = tx.direction === 'CREDIT';
            const displayStatus = (tx.invoiceStatus && tx.invoiceStatus !== 'PAID') ? tx.invoiceStatus : tx.status;
            
            let statusClass = 'badge-success';
            if (displayStatus === 'PENDING') statusClass = 'badge-warning';
            else if (displayStatus === 'FAILED') statusClass = 'badge-danger';
            else if (displayStatus === 'REFUND_REQUESTED') statusClass = 'badge-info';
            else if (displayStatus === 'REFUND_REJECTED') statusClass = 'badge-danger';
            else if (displayStatus === 'REFUNDED') statusClass = 'badge-secondary';

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
                    ${isCredit ? '+' : '-'}₹${(Number(tx.amount) || 0).toFixed(2)}
                </strong></td>
                <td>${tx.type || '—'}</td>
                <td><span class="badge ${statusClass}">${displayStatus || '—'}</span></td>
                <td>${esc(tx.counterparty || '—')}</td>
                <td>${tx.timestamp ? new Date(tx.timestamp).toLocaleString('en-IN') : '—'}</td>
            </tr>`;
        }).join('');
    } catch (e) {
        console.error("[Merchant] [API] TX LOAD ERR:", e);
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

            // 📊 LAZY-LOAD REPORTS ANALYTICS (only when Reports tab opened)
            if (section === 'reports') {
                if (window.ReportsAnalytics && typeof window.ReportsAnalytics.initOnce === 'function') {
                    window.ReportsAnalytics.initOnce();
                }
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
        // 🛡️ V5 SAFETY: Manual refresh acts as an escape hatch to clear stale local state.
        document.querySelectorAll('#sec-profile .form-input').forEach(el => el.dataset.dirty = "");
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
    const inv = invoiceList.find(i => (i.id || i.invoiceId)?.toString() === id.toString());
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

// ── Phase 2: Bank Accounts Logic ──
let bankAccountsList = [];

async function loadBankAccounts() {
    try {
        const banks = await API.merchant.getBankAccounts();
        bankAccountsList = banks || [];
        renderBankAccounts();
    } catch (e) {
        console.error("Failed to load bank accounts:", e);
    }
}

function renderBankAccounts() {
    const container = document.getElementById('banksListContainer');
    if (!container) return;
    
    // Build the final HTML string in memory to avoid multiple DOM updates/re-parsing
    let finalHtml = '';
    
    // 1. User Bank Accounts Section (TOP)
    finalHtml += generateUserBanksHtml(bankAccountsList);
    
    // 2. Divider & "Add Another Bank" Header
    // (Correct Order: Existing banks TOP, Divider, Add Another BOTTOM)
    finalHtml += `
        <div style="margin-top:24px; padding-top:20px; border-top:1px solid var(--border);">
            <div style="font-size:15px; font-weight:700; margin-bottom:12px; color:var(--text-primary);">
                <i class="fas fa-plus-circle" style="color:var(--primary); margin-right:6px;"></i> Add Another Bank
            </div>
            <div style="border:1px dashed var(--border); border-radius:12px; padding:16px; background:#fafafa;">
                <div style="font-size:13px; color:var(--text-secondary); margin-bottom:10px;">Select a preset or add a custom bank:</div>
                <div style="display:flex; gap:10px; flex-wrap:wrap;">
                    <button class="btn btn-outline btn-sm bank-preset-btn" data-bank-name="HDFC">HDFC</button>
                    <button class="btn btn-outline btn-sm bank-preset-btn" data-bank-name="SBI">SBI</button>
                    <button class="btn btn-outline btn-sm bank-preset-btn" data-bank-name="ICICI">ICICI</button>
                    <button class="btn btn-outline btn-sm bank-preset-btn" data-bank-name="Axis">Axis</button>
                    <button class="btn btn-secondary btn-sm bank-preset-btn" data-bank-name="">Add Custom Bank</button>
                </div>
            </div>
        </div>
    `;

    // ── SINGLE RENDER POINT (CRITICAL) ──
    // This preserves the container reference and prevents nested innerHTML += wipeouts.
    container.innerHTML = finalHtml;
}

function generateUserBanksHtml(banks) {
    let html = `
        <div style="font-size:15px; font-weight:700; margin-bottom:16px; color:var(--text-primary);">
            <i class="fas fa-university" style="color:var(--secondary); margin-right:6px;"></i> Your Bank Accounts
        </div>
    `;

    if (!banks || banks.length === 0) {
        return html + '<div class="text-center text-muted" style="padding: 20px 0; font-size: 13px; border:1px solid var(--border); border-radius:12px; border-style:dashed;">No bank accounts added yet.</div>';
    }

    return html + banks.map(bank => `
        <div style="border: 1px solid var(--border); border-radius: 8px; padding: 16px; margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center; background: ${bank.isDefault ? 'var(--bg)' : '#fff'};">
            <div>
                <div style="font-weight: 600; font-size: 15px; display: flex; align-items: center; gap: 8px;">
                    <i class="fas fa-building-columns" style="color:var(--text-secondary)"></i> 
                    ${esc(bank.bankName)}
                    ${bank.isDefault ? '<span class="badge badge-success" style="font-size: 10px;">DEFAULT</span>' : ''}
                </div>
                <div style="font-size: 13px; color: var(--text-secondary); margin-top: 4px;">
                    Account: ${esc(bank.accountNumber)} | IFSC: ${esc(bank.ifsc || 'N/A')}
                </div>
                <div style="font-size: 13px; color: var(--text-secondary); margin-top: 2px;">
                    Holder: ${esc(bank.accountHolderName)}
                </div>
            </div>
            <div>
                ${!bank.isDefault ? `<button class="btn btn-secondary btn-sm" onclick="setDefaultBank(${bank.id})">Set Default</button>` : ''}
                <button class="btn btn-outline-danger btn-sm" onclick="deleteBank(${bank.id})"><i class="fas fa-trash"></i></button>
            </div>
        </div>
    `).join('');
}

window.setDefaultBank = async function(id) {
    try {
        await API.merchant.setDefaultBankAccount(id);
        showToast('Default bank updated', 'success');
        loadBankAccounts();
    } catch (e) {
        showToast(e.message || 'Failed to set default bank', 'error');
    }
};

window.deleteBank = async function(id) {
    if(!confirm("Are you sure you want to remove this bank account?")) return;
    try {
        await API.merchant.deleteBankAccount(id);
        showToast('Bank account removed', 'success');
        loadBankAccounts();
    } catch (e) {
        showToast(e.message || 'Failed to remove bank account', 'error');
    }
};

// ── EVENT DELEGATION (CRITICAL) ──
// This listener remains active even when the internal bank items are re-rendered.
document.addEventListener('click', (e) => {
    const btn = e.target.closest('.bank-preset-btn');
    if (!btn) return;
    
    const bankName = btn.dataset.bankName || '';
    console.log("🚀 Bank preset clicked:", bankName);
    
    if (typeof window.openAddBankModalWithPrefill === 'function') {
        window.openAddBankModalWithPrefill(bankName);
    } else {
        console.error("❌ openAddBankModalWithPrefill is not globally available");
    }
});

window.openAddBankModalWithPrefill = function(bankName) {
    console.log("📂 Opening Add Bank Modal with prefill:", bankName);
    const modal = document.getElementById('addBankModal');
    if (!modal) {
        console.error("❌ addBankModal NOT FOUND in DOM");
        return;
    }

    // Prefill fields
    const nameEl = document.getElementById('newBankName');
    if (nameEl) nameEl.value = bankName || '';
    
    // Clear other fields
    ['newBankHolder', 'newBankAcc', 'newBankIfsc'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });

    // Open using existing openModal helper
    if (typeof window.openModal === 'function') {
        window.openModal('addBankModal');
    } else {
        modal.style.display = 'flex';
        setTimeout(() => modal.classList.add('active'), 10);
    }
};

document.getElementById('submitNewBankBtn')?.addEventListener('click', async () => {
    const btn = document.getElementById('submitNewBankBtn');
    const payload = {
        bankName: document.getElementById('newBankName').value.trim(),
        accountHolderName: document.getElementById('newBankHolder').value.trim(),
        accountNumber: document.getElementById('newBankAcc').value.trim(),
        ifsc: document.getElementById('newBankIfsc').value.trim(),
        isDefault: bankAccountsList.length === 0 // default to true if it's the first bank
    };
    
    if(!payload.bankName || !payload.accountHolderName || !payload.accountNumber || !payload.ifsc) {
        showToast('Please fill all bank details', 'warning');
        return;
    }
    
    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
    try {
        await API.merchant.addBankAccount(payload);
        showToast('Bank account added!', 'success');
        document.getElementById('addBankModal').style.display = 'none';
        loadBankAccounts();
    } catch (e) {
        showToast(e.message || 'Failed to add bank', 'error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-save"></i> Save Bank Account';
    }
});

// ── SAFE FIX: '+ Add Bank' Button Handler ──
document.addEventListener("DOMContentLoaded", () => {
    const btn = document.getElementById('addBankBtn');

    if (btn) {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            console.log("🚀 + Add Bank button clicked");
            if (typeof window.openAddBankModalWithPrefill === 'function') {
                window.openAddBankModalWithPrefill("");
            } else {
                console.error("❌ openAddBankModalWithPrefill not found");
            }
        });
    } else {
        console.warn("⚠️ #addBankBtn not found for direct binding");
    }
});

// Hook into loadDashboard or call directly
setTimeout(() => {
    loadBankAccounts();
}, 1000);

// ═══════════════════════════════════════════════════════════════════════
// ── WITHDRAWAL UI MODULE (Phase 3) ─────────────────────────────────────
// Zero-regression: no existing function modified.
// Reuses: bankAccountsList, openModal, closeModal, refreshWalletStats,
//         loadTransactions, showToast, safeErrorMessage, esc.
// ═══════════════════════════════════════════════════════════════════════

/**
 * Opens the Withdraw modal, pre-filling balance and bank dropdown.
 * Reads wallet balance directly from the DOM — no extra API call.
 * Reads bankAccountsList (already in memory from loadBankAccounts).
 */
function openWithdrawModal() {
    // 1. Parse current balance from stat card (always up-to-date after renderStatCards)
    const balEl = document.getElementById('stat-balance');
    const balText = balEl ? balEl.textContent.replace(/[₹,\s]/g, '') : '0';
    const currentBalance = parseFloat(balText) || 0;

    // 2. Show available balance in modal header
    const availEl = document.getElementById('withdrawAvailableBalance');
    if (availEl) {
        availEl.textContent = `₹${currentBalance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    }

    // 3. Populate bank dropdown from in-memory bankAccountsList (no re-fetch)
    const sel = document.getElementById('withdrawBankSelect');
    if (sel) {
        if (!bankAccountsList || bankAccountsList.length === 0) {
            sel.innerHTML = '<option value="">— No bank accounts linked. Please add one first. —</option>';
        } else {
            sel.innerHTML = bankAccountsList.map(b =>
                `<option value="${b.id}" ${b.isDefault ? 'selected' : ''}>
                    ${esc(b.bankName)} — ****${String(b.accountNumber || '').slice(-4)}
                    ${b.isDefault ? ' (Default)' : ''}
                </option>`
            ).join('');
        }
    }

    // 4. Reset amount field and hint
    const amtEl = document.getElementById('withdrawAmount');
    const hint = document.getElementById('withdrawAmountHint');
    if (amtEl) amtEl.value = '';
    if (hint) hint.textContent = '';

    openModal('withdrawModal');
}

/**
 * Submits the withdrawal request.
 * Validates: amount > 0, amount >= 100, amount <= balance, bank selected.
 * After success: runs lightweight refresh (refreshWalletStats + loadTransactions).
 */
async function submitWithdrawal() {
    const btn      = document.getElementById('submitWithdrawBtn');
    const amtInput = document.getElementById('withdrawAmount');
    const bankSel  = document.getElementById('withdrawBankSelect');
    const hint     = document.getElementById('withdrawAmountHint');

    const amount        = parseFloat(amtInput?.value);
    const bankAccountId = bankSel?.value ? parseInt(bankSel.value, 10) : null;

    // Current balance from DOM
    const balEl        = document.getElementById('stat-balance');
    const balText      = balEl ? balEl.textContent.replace(/[₹,\s]/g, '') : '0';
    const currentBalance = parseFloat(balText) || 0;

    // ── Client-side validation (server also validates — this is UX only) ──
    if (!amtInput?.value || isNaN(amount) || amount <= 0) {
        if (hint) { hint.textContent = 'Enter a valid withdrawal amount greater than ₹0.'; }
        amtInput?.focus();
        return;
    }
    if (amount < 100) {
        if (hint) { hint.textContent = 'Minimum withdrawal amount is ₹100.'; }
        amtInput?.focus();
        return;
    }
    if (amount > currentBalance) {
        if (hint) {
            hint.textContent = `Amount exceeds available balance of ₹${currentBalance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}.`;
        }
        amtInput?.focus();
        return;
    }
    if (!bankSel?.value) {
        showToast('Please select a bank account to receive the payout.', 'warning');
        return;
    }

    // Clear hint if all validations pass
    if (hint) hint.textContent = '';

    // ── Disable button during API call ─────────────────────────────────
    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Processing...';

    try {
        const payload = { amount };
        if (bankAccountId) payload.bankAccountId = bankAccountId;

        await API.wallet.withdraw(payload);

        showToast('Withdrawal initiated successfully! Your wallet will update shortly.', 'success');
        closeModal('withdrawModal');

        // ── Lightweight state sync — no full loadDashboard() ───────────
        await refreshWalletStats();
        if (typeof loadTransactions === 'function') {
            await loadTransactions();
        }

    } catch (e) {
        console.error('[Merchant] [Withdraw] Failed:', e);
        showToast(safeErrorMessage(e), 'error');
    } finally {
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-paper-plane"></i> Withdraw';
    }
}

// ── Bind Withdrawal Modal Triggers ─────────────────────────────────────
document.getElementById('openWithdrawModalBtn')?.addEventListener('click', openWithdrawModal);
document.getElementById('submitWithdrawBtn')?.addEventListener('click', submitWithdrawal);

// Close on backdrop click (consistent with existing modal pattern)
document.getElementById('withdrawModal')?.addEventListener('click', function (e) {
    if (e.target === this) closeModal('withdrawModal');
});

// Expose for potential inline access
window.openWithdrawModal  = openWithdrawModal;
window.submitWithdrawal   = submitWithdrawal;

// ═══════════════════════════════════════════════════════════════════════
// ── REPORTS ANALYTICS MODULE (Phase 2) ─────────────────────────────────
// Scope: STRICTLY isolated to #sec-reports & rpt-* DOM elements.
// Zero interference with: loadDashboard, bindNav, charts (chartStatus,
// chartPayment, chartActivity), or any existing variable/function.
// All chart instances stored in local closure — NO global pollution.
// Lazy-load: initOnce() is a singleton — API called only on first open.
// Fallback: All API errors show "No data available" — never throws or
// breaks the page.
// ═══════════════════════════════════════════════════════════════════════
;(function () {
    'use strict';

    // ── Module-private state ──────────────────────────────────────────
    let _initialized  = false;       // singleton guard
    let _currentRange = 'monthly';   // active range
    let _rptChartRevenue = null;     // Chart.js instance for revenue trend
    let _rptChartStatus  = null;     // Chart.js instance for invoice status
    let _isLoading    = false;       // re-entrancy guard

    // ── Helpers ──────────────────────────────────────────────────────
    const fmt = (n) => '₹' + (Number(n) || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 });
    const pct = (n) => (Number(n) || 0).toFixed(1) + '%';
    const rptEl = (id) => document.getElementById(id); // shorthand

    function _setLoading(on) {
        const lo = rptEl('rpt-loading');
        const ch = rptEl('rpt-charts-area');
        const er = rptEl('rpt-error');
        if (lo) lo.style.display = on ? 'block' : 'none';
        if (ch) ch.style.display = on ? 'none' : (ch.dataset.rptVisible === '1' ? 'block' : 'none');
        if (er && on) er.style.display = 'none';
    }

    function _showError(msg) {
        const lo = rptEl('rpt-loading');
        const ch = rptEl('rpt-charts-area');
        const er = rptEl('rpt-error');
        const em = rptEl('rpt-error-msg');
        if (lo) lo.style.display = 'none';
        if (ch) ch.style.display = 'none';
        if (er) er.style.display = 'block';
        if (em) em.textContent = msg || 'No data available for this period.';
    }

    function _showCharts() {
        const lo = rptEl('rpt-loading');
        const ch = rptEl('rpt-charts-area');
        const er = rptEl('rpt-error');
        if (lo) lo.style.display = 'none';
        if (er) er.style.display = 'none';
        if (ch) { ch.style.display = 'block'; ch.dataset.rptVisible = '1'; }
    }

    // ── Active range button styling ───────────────────────────────────
    function _updateRangeButtons(activeRange) {
        document.querySelectorAll('.rpt-range-btn').forEach(btn => {
            const r = btn.getAttribute('data-rpt-range');
            btn.className = 'btn btn-sm rpt-range-btn ' +
                (r === activeRange ? 'btn-primary' : 'btn-secondary');
        });
    }

    // ── KPI Cards ─────────────────────────────────────────────────────
    function _renderKpis(pnl) {
        if (!pnl) { return; }
        const rv = rptEl('rpt-val-revenue');  if (rv) rv.textContent = fmt(pnl.totalRevenue);
        const cg = rptEl('rpt-val-cogs');     if (cg) cg.textContent = fmt(pnl.cogs || pnl.totalCogs || 0);
        const gp = rptEl('rpt-val-gross');    if (gp) gp.textContent = fmt(pnl.grossProfit);
        const gm = rptEl('rpt-val-margin');   if (gm) gm.textContent = pct(pnl.grossMarginPercent || pnl.grossMargin || 0);
        const iv = rptEl('rpt-val-invoices'); if (iv) iv.textContent = (pnl.totalInvoices || pnl.invoiceCount || '—');
    }

    // ── P&L Breakdown Table ───────────────────────────────────────────
    function _renderPnlTable(pnl) {
        const tbody = rptEl('rpt-pnl-tbody');
        if (!tbody) return;
        if (!pnl) {
            tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted" style="padding:16px;">No data available.</td></tr>';
            return;
        }

        const totalRevenue = Number(pnl.totalRevenue || 0);
        const totalCogs = Number(pnl.totalCogs || 0);
        const processingFees = Number(pnl.totalProcessingFees || 0);
        const grossProfit = Number(pnl.grossProfit || 0);
        const unknownImpact = Number(pnl.unknownCogsRevenue || 0);
        const unknownCount = Number(pnl.unknownCogsCount || 0);
        const rows = [
            { label: 'Gross Revenue',               val: totalRevenue,                  note: 'Sum of all PAID invoices' },
            { label: 'Cost of Goods (COGS)',      val: -totalCogs,                    note: 'COGS from known cost profiles' },
            { label: 'Processing Fees',           val: -processingFees,               note: 'Platform/processing charges' },
            { label: 'Gross Profit',              val: grossProfit,                  note: 'Revenue – Fees – COGS', bold: true },
            { label: 'Unknown COGS Impact (Excluded Revenue)', val: -unknownImpact, note: unknownCount > 0 ? `${unknownCount} invoices excluded due to missing cost data.` : 'No exclusions' },
            { label: 'Net Profit',                val: Number(pnl.netProfit || 0), note: 'Gross Profit – Processing Fees', bold: true },
        ];
        tbody.innerHTML = rows.map(r => {
            const amt = Number(r.val) || 0;
            const color = amt >= 0 ? 'var(--secondary)' : 'var(--danger)';
            return `<tr style="${r.bold ? 'background:var(--primary-light);font-weight:700;' : ''}">
                <td>${r.label}</td>
                <td class="text-right" style="color:${color};font-weight:600;">${fmt(Math.abs(amt))} ${amt < 0 ? '(deduction)' : ''}</td>
                <td class="text-right" style="color:var(--text-secondary);font-size:12px;">${r.note}</td>
            </tr>`;
        }).join('');
    }

    // ── Charts ────────────────────────────────────────────────────────
    function _renderRevenueChart(summary) {
        const canvas = rptEl('rpt-chart-revenue');
        if (!canvas) return;

        // Destroy previous instance safely
        if (_rptChartRevenue) { try { _rptChartRevenue.destroy(); } catch(e) {} _rptChartRevenue = null; }

        const revenueTrend = summary?.revenueTrend || [];
        const withdrawalTrend = summary?.withdrawalTrend || [];

        const revMap = new Map(revenueTrend.map(d => [d.label, Number(d.value || 0)]));
        const wdMap = new Map(withdrawalTrend.map(d => [d.label, Number(d.value || 0)]));

        const labels = [];
        revenueTrend.forEach(d => { if (!labels.includes(d.label)) labels.push(d.label); });
        withdrawalTrend.forEach(d => { if (!labels.includes(d.label)) labels.push(d.label); });

        const revenueVals = labels.map(l => revMap.get(l) || 0);
        const withdrawalVals = labels.map(l => wdMap.get(l) || 0);

        const hasData = revenueVals.some(v => v > 0) || withdrawalVals.some(v => v > 0);

        _rptChartRevenue = new Chart(canvas, {
            type: 'line',
            data: {
                labels: hasData ? labels : ['No data'],
                datasets: [
                    {
                        label: 'Revenue',
                        data: hasData ? revenueVals : [0],
                        borderColor: '#1a73e8',
                        backgroundColor: 'rgba(26,115,232,0.1)',
                        fill: true,
                        pointRadius: 3,
                        tension: 0.3
                    },
                    {
                        label: 'Withdrawals',
                        data: hasData ? withdrawalVals : [0],
                        borderColor: '#34a853',
                        backgroundColor: 'rgba(52,168,83,0.1)',
                        fill: true,
                        pointRadius: 3,
                        tension: 0.3
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: true, position: 'top' },
                    tooltip: {
                        callbacks: {
                            label: (ctx) => `${ctx.dataset.label}: ${fmt(ctx.raw)}`
                        }
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            callback: (val) => '₹' + Number(val).toLocaleString('en-IN')
                        }
                    }
                }
            }
        });
    }

    function _renderStatusChart() {
        const canvas = rptEl('rpt-chart-status');
        if (!canvas) return;

        if (_rptChartStatus) { try { _rptChartStatus.destroy(); } catch(e) {} _rptChartStatus = null; }

        const statusMap = { PAID: 0, PENDING: 0, UNPAID: 0, CANCELLED: 0, REFUND_REQUESTED: 0, REFUNDED: 0 };
        const invs = Array.isArray(invoiceList) ? invoiceList : [];
        invs.forEach(inv => {
            const s = (inv?.status || 'UNPAID').toUpperCase();
            if (s in statusMap) statusMap[s] += 1;
        });

        const hasData = Object.values(statusMap).some(v => v > 0);

        _rptChartStatus = new Chart(canvas, {
            type: 'doughnut',
            data: {
                labels: hasData ? Object.keys(statusMap) : ['No data'],
                datasets: [{
                    data: hasData ? Object.values(statusMap) : [1],
                    backgroundColor: hasData
                        ? ['#34a853', '#fbbc04', '#ea4335', '#9aa0a6', '#4285f4', '#8e24aa']
                        : ['#e0e0e0'],
                    borderWidth: 2,
                    borderColor: '#fff'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { position: 'bottom' } }
            }
        });
    }

    // ── Shared Date Logic ────────────────────────────────────────────
    function _calculateDates(range) {
        const end = new Date();
        const start = new Date();
        
        switch (range.toLowerCase()) {
            case 'weekly':  start.setDate(end.getDate() - 84);  break; // 12 weeks
            case 'monthly': start.setMonth(end.getMonth() - 12); break; // 12 months
            case 'yearly':  start.setMonth(0, 1); start.setHours(0, 0, 0, 0); break; // current calendar year
            case 'quarterly': start.setMonth(end.getMonth() - 12); break; // last 4 quarters
            default:        start.setDate(end.getDate() - 30); break; // 30 days
        }
        
        return {
            endDate: end.toISOString(),
            startDate: start.toISOString()
        };
    }

    // ── Secure Authenticated Download ─────────────────────────────────
    async function _triggerAuthDownload(apiFn, filename, params) {
        try {
            showToast('Preparing your report...', 'info');
            const blob = await apiFn(params);
            window.triggerBlobDownload(blob, filename);
            showToast('Download complete', 'success');
        } catch (err) {
            console.error('[ReportsAnalytics] Download failed:', err);
            showToast('Failed to download report. Check permissions.', 'error');
        }
    }

    // ── Main data fetch & render ──────────────────────────────────────
    async function _loadAnalytics(range) {
        if (_isLoading) return;
        _isLoading = true;
        _setLoading(true);

        try {
            const [pnl, summary] = await Promise.all([
                API.wallet.getPnl({ range }).catch(e => {
                    console.warn('[ReportsAnalytics] PnL fetch failed:', e.message);
                    return null;
                }),
                API.wallet.getSummary({ range }).catch(e => {
                    console.warn('[ReportsAnalytics] Summary fetch failed:', e.message);
                    return null;
                })
            ]);

            // If BOTH failed — show error state, do not crash
            if (!pnl && !summary) {
                _showError('No analytics data available. Try again later.');
                return;
            }

            // 🛡️ KPI CARD MAPPING (V8 Precision)
            const setMoney = (id, n) => {
                const el = document.getElementById(id);
                if (!el) return;
                el.innerText = '₹' + (Number(n) || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
            };
            setMoney('rpt-val-revenue', summary?.totalRevenue || 0);
            setMoney('rpt-val-withdrawals', summary?.totalWithdrawals || 0);
            setMoney('rpt-val-cogs', pnl?.totalCogs || 0);
            setMoney('rpt-val-gross', pnl?.grossProfit || 0);

            // Margin calculation
            const totalRevenue = Number(pnl?.totalRevenue || 0);
            const grossProfit = Number(pnl?.grossProfit || 0);
            const margin = totalRevenue > 0 ? (grossProfit / totalRevenue * 100) : 0;
            const marginEl = document.getElementById('rpt-val-margin');
            if (marginEl) marginEl.innerText = margin.toFixed(1) + '%';

            const invoicesEl = document.getElementById('rpt-val-invoices');
            if (invoicesEl) invoicesEl.innerText = String((Array.isArray(invoiceList) ? invoiceList.length : 0) || 0);

            _renderPnlTable(pnl);
            _renderRevenueChart(summary);
            _renderStatusChart();
            _showCharts();

        } catch (err) {
            console.error('[ReportsAnalytics] Unexpected error:', err);
            _showError('Failed to load analytics. No data available.');
        } finally {
            _isLoading = false;
        }
    }

    // ── Bind range buttons ────────────────────────────────────────────
    function _bindRangeButtons() {
        document.querySelectorAll('.rpt-range-btn').forEach(btn => {
            btn.addEventListener('click', function () {
                const range = this.getAttribute('data-rpt-range');
                if (!range || range === _currentRange) return;
                _currentRange = range;
                _updateRangeButtons(range);
                // Reset and reload
                _isLoading = false;
                _loadAnalytics(range);
            });
        });

        // 🛡️ BIND AUTHENTICATED EXPORTS
        const exportReport = async ({ type, format, range }) => {
            const exporters = {
                pnl: {
                    api: API.wallet.exportPnl,
                    fileBase: 'ProfitLoss'
                },
                statement: {
                    api: API.wallet.exportStatement,
                    fileBase: 'Statement'
                },
                summary: {
                    api: API.wallet.exportSummary,
                    fileBase: 'AnalyticsSummary'
                }
            };
            const entry = exporters[type];
            if (!entry) {
                throw new Error(`Unsupported export type: ${type}`);
            }
            const extension = format === 'excel' ? 'xlsx' : 'pdf';
            await _triggerAuthDownload(
                entry.api,
                `${entry.fileBase}_${range}_${format}.${extension}`,
                { format, range }
            );
        };

        document.querySelectorAll('.rpt-export-btn').forEach(btn => {
            btn.addEventListener('click', async function(e) {
                e.preventDefault();
                const type = this.getAttribute('data-rpt-type'); // 'pnl' | 'statement' | 'summary'
                const format = this.getAttribute('data-rpt-format'); // 'pdf' or 'excel'
                await exportReport({ type, format, range: _currentRange });
            });
        });
    }

    // ── Public API ───────────────────────────────────────────────────
    /**
     * initOnce() — singleton init. Safe to call multiple times.
     * First call: binds buttons + fires API. Subsequent calls: no-op.
     */
    function initOnce() {
        if (_initialized) {
            console.log('[ReportsAnalytics] Already initialized — skipping.');
            return;
        }
        _initialized = true;
        console.log('[ReportsAnalytics] First open — initializing analytics.');
        _bindRangeButtons();
        _updateRangeButtons(_currentRange);
        _loadAnalytics(_currentRange);
    }

    // ── Expose on window ─────────────────────────────────────────────
    window.ReportsAnalytics = { initOnce };

})();
// ── END REPORTS ANALYTICS MODULE ──────────────────────────────────────────