/**
 * BillMe — Customer Dashboard Logic
 * Hardened for Production Stabilization
 */

// ── Error Handling Utilities ──────────────────────────────────
function safeErrorMessage(e) {
    if (!e) return "Unknown error";
    if (typeof e === "string") return e;
    if (e.message) return e.message;
    if (e.error) return e.error;
    if (typeof e === 'object') {
        try { return JSON.stringify(e); } catch(s) { return "Something went wrong"; }
    }
    return "Something went wrong";
}

window.addEventListener("error", function (e) {
    console.error("GLOBAL ERROR:", e.error || e.message);
});

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

    try {
        await loadCustomerData();

        const loader = document.getElementById('pageLoader');
        const layout = document.getElementById('dashLayout');

        if (loader) loader.style.display = 'none';
        if (layout) layout.style.display = 'flex';

        // Initialize Navigation
        setupNavigation();

        // Initialize Logout
        setupLogout();

    } catch (err) {
        console.error('Failed to load dashboard:', err);
        if (window.API && window.API.showToast) {
            window.API.showToast(`Failed to load dashboard: ${safeErrorMessage(err)}`, 'error');
        }
    }
});

function setupNavigation() {
    console.log('[Customer] setupNavigation() called');

    // ✅ Bind on <a> tags directly to avoid li propagation issues
    document.querySelectorAll('.nav-item a').forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();

            const navItem = this.closest('.nav-item');
            const section = navItem ? navItem.getAttribute('data-section') : null;
            console.log('[Customer] Nav link clicked → section:', section);

            if (section) {
                showSection(section);
                if (window.innerWidth <= 768) toggleSidebar(false);
            }
        });
    });

    // Overlay: already handled by inline onclick on the element,
    // but also bind here as a JS fallback
    document.getElementById('sidebarOverlay')?.addEventListener('click', () => {
        console.log('[Customer] Overlay clicked → closing sidebar');
        toggleSidebar(false);
    });
}

/**
 * Unified Sidebar Toggle
 */
window.toggleSidebar = function(forcedState) {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('sidebarOverlay');
    console.log('[Customer] toggleSidebar() forcedState:', forcedState, 'sidebar:', !!sidebar);

    if (!sidebar) {
        console.error('[Customer] SIDEBAR NOT FOUND! Check id="sidebar" in HTML.');
        return;
    }

    if (typeof forcedState === 'boolean') {
        if (forcedState) {
            sidebar.classList.add('open');
            if (overlay) overlay.classList.add('active');
        } else {
            sidebar.classList.remove('open');
            if (overlay) overlay.classList.remove('active');
        }
    } else {
        sidebar.classList.toggle('open');
        if (overlay) overlay.classList.toggle('active');
    }
    console.log('[Customer] Sidebar classes now:', sidebar.className);
};

function setupLogout() {
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            localStorage.removeItem("billme_token");
            localStorage.removeItem("billme_role");
            localStorage.removeItem("billme_user_id");
            window.location.href = "../index.html";
        });
    }
}

function showSection(sectionId) {
    console.log('[Customer] showSection() →', sectionId);

    document.querySelectorAll('.section-page').forEach(sec => sec.classList.remove('active'));
    document.querySelectorAll('.nav-item').forEach(nav => nav.classList.remove('active'));

    const section = document.getElementById('sec-' + sectionId);
    if (section) {
        section.classList.add('active');
    } else {
        console.warn('[Customer] No section found: sec-' + sectionId);
    }

    const nav = document.querySelector(`.nav-item[data-section="${sectionId}"]`);
    if (nav) nav.classList.add('active');

    const titles = { overview: 'Overview', invoices: 'My Invoices', profile: 'Profile Settings' };
    const titleEl = document.getElementById('pageTitle');
    if (titleEl) titleEl.innerText = titles[sectionId] || 'Dashboard';
}

async function loadCustomerData() {
    if (!window.API) {
        throw new Error("API client not loaded.");
    }

    try {
        /* ---------- PROFILE ---------- */
        const profile = await window.API.customer.getProfile();
        if (profile) {
            const name = profile.fullName || profile.username || "Customer";
            const avatarUrl = profile.profileImageUrl || `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=1a73e8&color=fff`;

            setText('side-name', name);
            setText('side-id', '#' + (profile.id || 'CUST'));
            setText('prof-name', name);
            setText('prof-email', profile.email);
            setText('prof-id', '#' + (profile.id || 'CUST'));
            setText('prof-location', profile.location || 'Not Set');
            
            setText('prof-join', profile.createdAt ? new Date(profile.createdAt).toLocaleDateString() : '-');
            setText('prof-active', profile.lastActive ? new Date(profile.lastActive).toLocaleDateString() : 'Active Now');

            setImage('side-avatar', avatarUrl);
            setImage('prof-avatar', avatarUrl);
            
            // Also update the harmonized preview container if it exists
            const previewContainer = document.getElementById('profile-preview-container');
            if (previewContainer) {
                previewContainer.innerHTML = `<img src="${avatarUrl}" style="width:100%; height:100%; border-radius:50%; object-fit:cover;">`;
                previewContainer.style.background = 'none';
            }
        }

        /* ---------- INVOICES ---------- */
        const invoices = await window.API.customer.getInvoices();
        renderInvoices(invoices || []);
        updateAnalytics(invoices || []);
    } catch (err) {
        console.error("Data load error:", err);
        throw err;
    }
}

function renderInvoices(invoices) {
    const recentBody = document.getElementById('recentInvoicesBody');
    const allBody = document.getElementById('allInvoicesBody');

    if (!recentBody || !allBody) return;

    // Cache for modal preview lookup
    window._customerInvoiceList = invoices;

    const rows = invoices.map(inv => {
        const invoiceId = inv.id || inv.invoiceId;
        if (!invoiceId) return '';

        const amount = Number(inv.totalPayable || 0);
        const status = inv.status || "UNPAID";

        let statusBadgeClass = 'warning';
        if (status === 'PAID') statusBadgeClass = 'success';
        if (status === 'REFUNDED') statusBadgeClass = 'secondary';
        if (status === 'REFUND_REQUESTED') statusBadgeClass = 'info';
        if (status === 'FAILED') statusBadgeClass = 'danger';

        let actionsHtml = `<button class="btn btn-secondary btn-sm" onclick="previewInvoice('${inv.invoiceNumber}', '${inv.paymentToken}')" title="Preview"><i class="fas fa-eye"></i></button>`;
        
        if (status === 'UNPAID') {
            actionsHtml += `<button class="btn btn-primary btn-sm" onclick="payInvoice('${inv.invoiceNumber}', '${inv.paymentToken}')">Pay Now</button>`;
        } else if (status === 'PENDING' || status === 'FAILED') {
            actionsHtml += `<button class="btn btn-warning btn-sm" onclick="retryPayment('${invoiceId}')" title="Retry Payment"><i class="fas fa-redo"></i> Retry</button>`;
        } else if (status === 'PAID') {
            const now = new Date();
            const expiry = inv.refundWindowExpiry ? new Date(inv.refundWindowExpiry) : null;
            
            if (!expiry || expiry > now) {
                actionsHtml += `<button class="btn btn-outline-danger btn-sm" onclick="requestRefund('${invoiceId}')">Request Refund</button>`;
            } else {
                actionsHtml += `<span class="badge badge-secondary" style="font-size:11px; opacity:0.6;">Refund Window Closed</span>`;
            }
        } else if (status === 'REFUND_REQUESTED') {
            actionsHtml += `<span class="badge badge-info" style="font-size:11px;">Pending Refund</span>`;
        }

        return `
        <tr>
            <td>${inv.invoiceNumber || "-"}</td>
            <td>${inv.merchantName || "Merchant"}</td>
            <td class="fw-600">₹${amount.toFixed(2)}</td>
            <td>
                <span class="badge badge-${statusBadgeClass}">
                    ${status.replace('_', ' ')}
                </span>
            </td>
            <td>${inv.dueDate ? new Date(inv.dueDate).toLocaleDateString() : "-"}</td>
            <td>${(status === 'PAID' && inv.paidAt) ? new Date(inv.paidAt).toLocaleDateString() : "-"}</td>
            <td>
                <div class="d-flex gap-1 align-items-center">
                    ${actionsHtml}
                    <button class="btn btn-secondary btn-sm" onclick="downloadInvoice('${invoiceId}')" title="Download PDF">
                        <i class="fas fa-download"></i>
                    </button>
                </div>
            </td>
        </tr>`;
    }).join('');

    allBody.innerHTML = invoices.length ? rows : '<tr><td colspan="7" class="text-center text-muted">No invoices found</td></tr>';
    recentBody.innerHTML = invoices.length ? rows.split('</tr>').slice(0, 5).join('</tr>') : '<tr><td colspan="7" class="text-center text-muted">No recent activity</td></tr>';
}

function updateAnalytics(invoices) {
    const paidInvoices = invoices.filter(i => i.status === 'PAID');
    const totalSpent = paidInvoices.reduce((sum, i) => sum + Number(i.totalPayable || 0), 0);
    const distinctMerchants = new Set(invoices.map(i => i.merchantName)).size;

    setText('stat-total-spent', `₹${totalSpent.toLocaleString()}`);
    setText('stat-total-purchases', invoices.length);
    setText('stat-avg-order', invoices.length ? `₹${(totalSpent / invoices.length).toFixed(2)}` : '₹0.00');
    setText('stat-merchants', distinctMerchants);
}

// Global actions exposed to window
window.previewInvoice = function(num, token) {
    console.log('Modal opened', 'invoiceNum:', num, 'token:', token);

    if (!num || !token) {
        console.warn('[Customer] previewInvoice: missing num or token');
        return;
    }

    // Build or reuse the modal
    let modal = document.getElementById('invoicePreviewModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'invoicePreviewModal';
        modal.style.cssText = 'display:none; position:fixed; inset:0; background:rgba(0,0,0,0.55); z-index:3000; align-items:center; justify-content:center; padding:16px;';
        modal.innerHTML = `
            <div style="background:#fff; border-radius:16px; width:100%; max-width:520px; max-height:90vh; overflow-y:auto; box-shadow:0 20px 40px rgba(0,0,0,0.2);">
                <div style="padding:20px 24px; border-bottom:1px solid var(--border,#e2e8f0); display:flex; justify-content:space-between; align-items:center;">
                    <h3 style="margin:0; font-size:17px; font-weight:700;">Invoice Details</h3>
                    <button onclick="document.getElementById('invoicePreviewModal').style.display='none'" style="background:none;border:none;cursor:pointer;font-size:20px;color:#64748b;">✕</button>
                </div>
                <div id="invoicePreviewBody" style="padding:24px;">
                    <div style="text-align:center;padding:20px;color:#94a3b8;">Loading invoice details…</div>
                </div>
            </div>`;
        document.body.appendChild(modal);
        // Close on backdrop click
        modal.addEventListener('click', function(e) {
            if (e.target === modal) modal.style.display = 'none';
        });
    }

    modal.style.display = 'flex';
    const previewBody = document.getElementById('invoicePreviewBody');

    // Try to find invoice data from the already-loaded list first (no extra API call)
    const cachedInvoice = (window._customerInvoiceList || []).find(
        inv => inv.invoiceNumber === num || String(inv.invoiceNumber) === String(num)
    );

    if (cachedInvoice) {
        renderInvoicePreview(previewBody, cachedInvoice);
    } else {
        // Fallback: show what we know from the token/num
        previewBody.innerHTML = `
            <div style="display:flex;flex-direction:column;gap:14px;">
                <div><span style="font-size:12px;font-weight:600;color:#64748b;text-transform:uppercase;">Invoice #</span><div style="font-size:16px;font-weight:700;margin-top:3px;">${num}</div></div>
                <div style="padding:14px;background:#f8fafc;border-radius:10px;text-align:center;color:#64748b;font-size:13px;">Full details unavailable offline. Use Pay Now to proceed.</div>
                <button onclick="window.location.href='../pay-invoice.html?num=${encodeURIComponent(num)}&token=${encodeURIComponent(token)}'"
                    style="background:var(--primary,#1a73e8);color:#fff;border:none;padding:10px 20px;border-radius:8px;font-weight:600;cursor:pointer;width:100%;">
                    <i class="fas fa-external-link-alt"></i> Open Invoice Page
                </button>
            </div>`;
    }
};

function renderInvoicePreview(container, inv) {
    const status = inv.status || 'UNKNOWN';
    const statusColors = { PAID: '#16a34a', UNPAID: '#d97706', FAILED: '#dc2626', REFUNDED: '#6366f1', PENDING: '#d97706' };
    const color = statusColors[status] || '#64748b';
    container.innerHTML = `
        <div style="display:flex;flex-direction:column;gap:16px;">
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">
                <div><div style="font-size:11px;font-weight:600;color:#94a3b8;text-transform:uppercase;">Invoice #</div><div style="font-weight:700;margin-top:3px;">${inv.invoiceNumber || '—'}</div></div>
                <div><div style="font-size:11px;font-weight:600;color:#94a3b8;text-transform:uppercase;">Status</div><div style="font-weight:700;color:${color};margin-top:3px;">${status}</div></div>
                <div><div style="font-size:11px;font-weight:600;color:#94a3b8;text-transform:uppercase;">Merchant</div><div style="font-weight:600;margin-top:3px;">${inv.merchantName || '—'}</div></div>
                <div><div style="font-size:11px;font-weight:600;color:#94a3b8;text-transform:uppercase;">Amount</div><div style="font-weight:700;font-size:18px;margin-top:3px;">₹${Number(inv.totalPayable||0).toFixed(2)}</div></div>
                <div><div style="font-size:11px;font-weight:600;color:#94a3b8;text-transform:uppercase;">Due Date</div><div style="margin-top:3px;">${inv.dueDate ? new Date(inv.dueDate).toLocaleDateString() : '—'}</div></div>
                <div><div style="font-size:11px;font-weight:600;color:#94a3b8;text-transform:uppercase;">Paid Date</div><div style="margin-top:3px;">${(status==='PAID' && inv.paidAt) ? new Date(inv.paidAt).toLocaleDateString() : '—'}</div></div>
            </div>
            ${inv.description ? `<div style="padding:12px;background:#f8fafc;border-radius:8px;font-size:13px;color:#475569;"><strong>Description:</strong> ${inv.description}</div>` : ''}
            ${status === 'UNPAID' ? `<button onclick="window.payInvoice && payInvoice('${inv.invoiceNumber}','${inv.paymentToken}'); document.getElementById('invoicePreviewModal').style.display='none';" style="background:var(--primary,#1a73e8);color:#fff;border:none;padding:12px;border-radius:8px;font-weight:600;cursor:pointer;width:100%;font-size:15px;">Pay Now ₹${Number(inv.totalPayable||0).toFixed(2)}</button>` : ''}
        </div>`;
}

window.downloadInvoice = async function(invoiceId) {
    if (!invoiceId || invoiceId === 'undefined') {
        if (window.API && window.API.showToast) window.API.showToast('Invalid Invoice ID', 'error');
        return;
    }

    try {
        if (window.API && window.API.showToast) window.API.showToast('Preparing download...', 'info');
        const blob = await window.API.merchant.downloadInvoicePdf(invoiceId);
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `invoice_${invoiceId}.pdf`;
        document.body.appendChild(a);
        a.click();
        setTimeout(() => {
            window.URL.revokeObjectURL(url);
            a.remove();
        }, 100);
        if (window.API && window.API.showToast) window.API.showToast('Download started', 'success');
    } catch (err) {
        console.error('Download error:', err);
        if (window.API && window.API.showToast) window.API.showToast(err.message || 'Failed to download invoice', 'error');
    }
};

window.payInvoice = function(invoiceNumber, token) {
    if (!invoiceNumber || invoiceNumber === 'undefined') {
        if (window.API && window.API.showToast) window.API.showToast('Invalid Invoice', 'error');
        return;
    }
    window.location.href = `../pay-invoice.html?num=${invoiceNumber}&token=${token}`;
};

window.retryPayment = async function(invoiceId) {
    if (!invoiceId || invoiceId === 'undefined') {
        if (window.API && window.API.showToast) window.API.showToast('Invalid Invoice ID', 'error');
        return;
    }

    try {
        if (window.API && window.API.showToast) window.API.showToast('Refreshing payment session...', 'info');
        const orderId = await window.API.payment.retryPayment(invoiceId);
        if (window.API && window.API.showToast) window.API.showToast('Payment session refreshed. Redirecting...', 'success');
        
        // Find the invoice details to get the number and token
        const invoices = await window.API.customer.getInvoices();
        const inv = invoices.find(i => (i.id || i.invoiceId).toString() === invoiceId.toString());
        
        if (inv) {
            window.location.href = `../pay-invoice.html?num=${inv.invoiceNumber}&token=${inv.paymentToken}`;
        } else {
            window.location.reload();
        }
    } catch (err) {
        console.error('Retry failed:', err);
        if (window.API && window.API.showToast) window.API.showToast(err.message || 'Failed to retry payment', 'error');
    }
};

window.requestRefund = async function(invoiceId) {
    if (!invoiceId || invoiceId === 'undefined') {
        if (window.API && window.API.showToast) window.API.showToast('Invalid Invoice ID', 'error');
        return;
    }
    if (!confirm('Are you sure you want to request a refund for this invoice?')) return;

    try {
        if (window.API && window.API.showToast) window.API.showToast('Initiating refund request...', 'info');
        await window.API.payment.requestRefund(invoiceId);
        if (window.API && window.API.showToast) window.API.showToast('Refund request submitted successfully', 'success');
        await loadCustomerData();
    } catch (err) {
        console.error('Refund request failed:', err);
        if (window.API && window.API.showToast) window.API.showToast(err.message || 'Failed to request refund', 'error');
    }
};


// Internal helpers
function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.innerText = value;
}

function setImage(id, src) {
    const el = document.getElementById(id);
    if (el) el.src = src;
}


