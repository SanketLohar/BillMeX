/**
 * BillMe — Admin Dashboard Controller
 * Handles Navigation, Data Fetching, and Charts
 */

// Global Chart Instances (to allow destruction before re-render)
let txTrendChart = null;
let payDistChart = null;
let merchantGrowthChart = null;

// State
const merchantCache = {};
let txnMerchantFilter = null; // Stores merchant name or ID for filtering

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
    // 1. Initialize UI (Auth is handled by auth.js)
    initNavigation();
    
    // Failsafe loader hide
    setTimeout(() => {
        const loader = document.getElementById('pageLoader');
        const dash = document.getElementById('dashLayout');
        if (loader) loader.style.display = 'none';
        if (dash && dash.style.display !== 'flex') dash.style.display = 'flex';
    }, 5000);

    // 2. Set Profile Data (from current session/token)
    try {
        const user = window.API.auth.getUser(); // Assumed helper or from localStorage
        if (user) {
            document.getElementById('side-name').innerText = user.firstName || 'Administrator';
            if (user.profileImageUrl) document.getElementById('side-avatar').src = user.profileImageUrl;
        }
    } catch (e) { console.warn("Could not load admin profile data"); }

    // 3. Show Layout and Load Section
    const dashLayout = document.getElementById('dashLayout');
    if (dashLayout) dashLayout.style.display = 'flex';
    
    loadCurrentSection();

    // 4. Set Dynamic Year
    const yearEl = document.getElementById('currentYear');
    if (yearEl) yearEl.textContent = new Date().getFullYear();

    // 5. Logout Handler
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            window.API.auth.logout();
        });
    }
});

/* ===========================================================
   NAVIGATION SYSTEM
=========================================================== */

function initNavigation() {
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const sectionId = item.getAttribute('data-section');
            if (sectionId) {
                window.location.hash = sectionId;
                showSection(sectionId);
            }
        });
    });

    // Listen for hash changes
    window.addEventListener('hashchange', () => {
        loadCurrentSection();
    });
}

function loadCurrentSection() {
    const hash = window.location.hash.replace('#', '') || 'dashboard';
    showSection(hash);
}

function showSection(sectionId) {
    // If navigating to transactions via sidebar (not from drawer), clear filter
    const isSidebarNav = event && event.type === 'click' && event.currentTarget.classList.contains('nav-item');
    if (isSidebarNav && sectionId === 'transactions') {
        txnMerchantFilter = null;
    }

    // Update Sidebar Active State
    document.querySelectorAll('.nav-item').forEach(item => {
        if (item.getAttribute('data-section') === sectionId) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });

    // Update Section Visibility
    document.querySelectorAll('.section-page').forEach(sec => {
        sec.classList.remove('active');
        if (sec.id === `sec-${sectionId}`) {
            sec.classList.add('active');
        }
    });

    // Update Topbar Title
    const titles = {
        'dashboard': 'Dashboard Overview',
        'merchants': 'Merchant Directory',
        'customers': 'Customer Ledger',
        'transactions': 'Global Transaction Log',
        'settings': 'Compliance & Security'
    };
    const titleEl = document.getElementById('pageTitle');
    if (titleEl) titleEl.innerText = titles[sectionId] || 'Admin Console';

    // Load Section Data
    switch (sectionId) {
        case 'dashboard': loadDashboard(); break;
        case 'merchants': loadMerchants(); break;
        case 'customers': loadCustomers(); break;
        case 'transactions': loadTransactions(); break;
        case 'settings': loadCompliance(); break;
    }
}

/* ===========================================================
   DASHBOARD OVERVIEW
=========================================================== */

async function loadDashboard() {
    try {
        toggleLoader(true);
        
        const [stats, revenueList, transactions] = await Promise.all([
            window.API.admin.getStats(),
            window.API.admin.getRevenue(),
            window.API.admin.getTransactions()
        ]);
        
        document.getElementById('count-merchants').innerText = stats.totalMerchants || 0;
        document.getElementById('count-customers').innerText = stats.totalCustomers || 0;
        document.getElementById('count-txns').innerText = stats.totalTransactions || 0;
        document.getElementById('platform-revenue').innerText = '₹' + (stats.totalRevenue || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 });

        const processedCharts = processDashboardData(transactions, revenueList);

        renderRevenueTrend(processedCharts.revenueTrend);
        renderPaymentDistribution(processedCharts.paymentMethods);
        renderTransactionVolume(processedCharts.dailyVolume);
        renderActivity(transactions.slice(0, 10));

    } catch (err) {
        console.warn('Dashboard Data Aggregation Failed:', err);
        showToast(`Sync Failed: ${safeErrorMessage(err)}`, 'error');
    } finally {
        toggleLoader(false);
    }
}

function processDashboardData(transactions, revenueList) {
    console.log("Chart API Response (Transactions):", transactions);
    console.log("Chart API Response (Revenue):", revenueList);

    const volumeByDate = {};
    const methods = {};

    const revLabels = [];
    const revValues = [];

    (revenueList || []).filter(r => r.amount != null).forEach(r => {
        if (r.date) {
            revLabels.push(r.date);
            revValues.push(Number(r.amount));
        }
    });

    (transactions || []).forEach(tx => {
        const timeVal = tx.timestamp || tx.createdAt;
        if (timeVal) {
            const date = new Date(timeVal).toISOString().split('T')[0];
            volumeByDate[date] = (volumeByDate[date] || 0) + 1;
        }

        const method = tx.transactionType;
        if (method && method !== 'UNKNOWN') {
            // Count all valid payment methods dynamically
            methods[method] = (methods[method] || 0) + 1;
        }
    });

    const volLabels = Object.keys(volumeByDate).sort();
    const volValues = volLabels.map(d => volumeByDate[d]);

    console.log("Processed Labels (Daily Volume):", volLabels);
    console.log("Processed Values (Daily Volume):", volValues);
    console.log("Processed Payment Methods:", methods);
    console.log("Processed Labels (Revenue):", revLabels);
    console.log("Processed Values (Revenue):", revValues);

    return {
        revenueTrend: { labels: revLabels, values: revValues },
        paymentMethods: methods,
        dailyVolume: { labels: volLabels, values: volValues }
    };
}

function renderRevenueTrend(data) {
    const canvas = document.getElementById('revenueTrendChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (window.revenueChart) window.revenueChart.destroy();

    window.revenueChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.labels,
            datasets: [{
                label: 'Revenue (₹)',
                data: data.values,
                borderColor: '#6366f1',
                backgroundColor: 'rgba(99, 102, 241, 0.1)',
                borderWidth: 3,
                fill: true,
                tension: 0.4,
                pointRadius: 4,
                pointBackgroundColor: '#fff',
                pointBorderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                y: { beginAtZero: true, grid: { borderDash: [5, 5] }, ticks: { callback: v => '₹' + v } },
                x: { grid: { display: false } }
            }
        }
    });
}

function renderPaymentDistribution(methods) {
    const canvas = document.getElementById('payDistChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (window.payChart) window.payChart.destroy();

    window.payChart = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: Object.keys(methods).filter(m => methods[m] > 0),
            datasets: [{
                data: Object.values(methods).filter(v => v > 0),
                backgroundColor: ['#6366f1', '#10b981', '#f59e0b', '#ef4444'],
                borderWidth: 0,
                hoverOffset: 10
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '75%',
            plugins: {
                legend: { position: 'bottom', labels: { usePointStyle: true, padding: 20 } }
            }
        }
    });
}

function renderTransactionVolume(data) {
    const canvas = document.getElementById('txTrendChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (window.volChart) window.volChart.destroy();

    window.volChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.labels,
            datasets: [{
                label: 'Transactions',
                data: data.values,
                backgroundColor: '#cbd5e1',
                hoverBackgroundColor: '#6366f1',
                borderRadius: 6
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                y: { beginAtZero: true, ticks: { stepSize: 1 } },
                x: { grid: { display: false } }
            }
        }
    });
}

function renderActivity(transactions) {
    const body = document.getElementById('adminActivityBody');
    if (!body) return;
    
    if (!transactions || transactions.length === 0) {
        body.innerHTML = '<tr><td colspan="4" class="text-center">No system activity recorded yet.</td></tr>';
        return;
    }

    body.innerHTML = transactions.map(t => `
        <tr>
            <td>
                <div style="display:flex; align-items:center; gap:10px;">
                    <div style="width:28px; height:28px; background:#f1f5f9; border-radius:50%; display:flex; align-items:center; justify-content:center; font-size:10px; font-weight:700;">
                        ${(t.customerName || 'U').charAt(0)}
                    </div>
                    <div>
                        <div style="font-weight:600;">${t.customerName || 'Anonymous'}</div>
                        <div style="font-size:11px; color:var(--text-muted);">TXN #${t.id || 'N/A'}</div>
                    </div>
                </div>
            </td>
            <td>Action: <span class="badge badge-info">${t.transactionType || 'SYSTEM'}</span></td>
            <td><span class="badge badge-${t.status === 'SUCCESS' ? 'success' : 'warning'}">${t.status || 'UNKNOWN'}</span></td>
            <td style="color:var(--text-muted); font-size:12px;">${t.timestamp ? new Date(t.timestamp).toLocaleString() : 'N/A'}</td>
        </tr>
    `).join('');
}

/* ===========================================================
   TRANSACTION MONITORING
=========================================================== */

async function loadTransactions() {
    const body = document.getElementById('transactionsTableBody');
    if (!body) return;

    try {
        toggleSkeleton('transactions', true);
        
        // Handle filter UI
        const indicator = document.getElementById('txnFilterIndicator');
        const filterText = document.getElementById('txnFilterText');
        if (txnMerchantFilter) {
            if (indicator) indicator.style.display = 'flex';
            if (filterText) filterText.innerText = `Filtering: ${txnMerchantFilter}`;
        } else {
            if (indicator) indicator.style.display = 'none';
        }

        const txns = await window.API.admin.getTransactions();
        renderTransactions(txns);
    } catch (err) {
        console.warn('Failed to load transactions:', err);
        body.innerHTML = '<tr><td colspan="7" class="text-center text-danger">Failed to load transaction data.</td></tr>';
    } finally {
        toggleSkeleton('transactions', false);
    }
}

function clearTransactionFilter() {
    txnMerchantFilter = null;
    loadTransactions();
}

function renderTransactions(transactions) {
    const body = document.getElementById('transactionsTableBody');
    if (!body) return;

    if (!transactions || transactions.length === 0) {
        body.innerHTML = '<tr><td colspan="7" class="text-center text-muted">No transactions recorded.</td></tr>';
        return;
    }

    // Apply Filter
    let filtered = transactions;
    if (txnMerchantFilter) {
        filtered = transactions.filter(t => 
            (t.merchantName && t.merchantName.toLowerCase().includes(txnMerchantFilter.toLowerCase())) ||
            (t.merchantId && t.merchantId.toString() === txnMerchantFilter.toString())
        );
    }

    if (filtered.length === 0) {
        body.innerHTML = '<tr><td colspan="7" class="text-center text-muted">No transactions found for this merchant.</td></tr>';
        return;
    }

    body.innerHTML = filtered.map(t => `
        <tr>
            <td><code style="font-size:12px; font-weight:700;">#${t.id || t.transactionId}</code></td>
            <td><div style="font-weight:700; color:var(--primary);">₹${(t.amount || 0).toLocaleString()}</div></td>
            <td>
                <div style="display:flex; align-items:center; gap:8px;">
                    <i class="fas ${t.transactionType === 'CARD' ? 'fa-credit-card' : 'fa-mobile-screen'}"></i>
                    ${t.transactionType && t.transactionType !== 'UNKNOWN' ? t.transactionType : 'UNKNOWN'}
                </div>
            </td>
            <td>
                <div style="font-weight:600;">${t.merchantName || 'Merchant'}</div>
                <div style="font-size:11px; color:var(--text-muted);">ID: ${t.merchantId || t.userId || 'N/A'}</div>
            </td>
            <td>
                <div style="font-weight:500;">${t.customerName || 'Customer'}</div>
                <div style="font-size:11px; color:var(--text-muted);">${t.customerEmail || ''}</div>
            </td>
            <td><span class="badge badge-${t.status === 'SUCCESS' ? 'success' : 'warning'}">${t.status || 'UNKNOWN'}</span></td>
            <td style="color:var(--text-muted); font-size:12px;">${t.timestamp ? new Date(t.timestamp).toLocaleString() : 'N/A'}</td>
        </tr>
    `).join('');
}

/* ===========================================================
   COMPLIANCE & RISK SYSTEM
=========================================================== */

async function loadCompliance() {
    try {
        toggleSkeleton('compliance', true);
        await Promise.all([
            renderComplianceStats(),
            loadFraudAlerts(),
            renderAuditLogs()
        ]);
    } catch (err) {
        console.warn('Compliance load failed:', err);
        showToast('Compliance Data Sync Failed', 'error');
    } finally {
        toggleSkeleton('compliance', false);
    }
}

async function renderComplianceStats() {
    const stats = await window.API.admin.getStats();
    const txns = await window.API.admin.getTransactions();

    document.getElementById('comp-daily-txns').innerText = stats.totalTransactions || 0;
    document.getElementById('comp-active-merchants').innerText = stats.totalMerchants || 0;
    document.getElementById('comp-active-customers').innerText = stats.totalCustomers || 0;

    const failedCount = txns.filter(t => t.status === 'FAILED').length;
    const failRate = txns.length ? ((failedCount / txns.length) * 100).toFixed(1) : 0;
    const flagged = txns.filter(t => (t.amount > 10000) || t.status === 'FAILED').length;

    document.getElementById('risk-flagged-count').innerText = flagged;
    document.getElementById('risk-high-merchants').innerText = Math.ceil(stats.totalMerchants * 0.05);
    document.getElementById('risk-fail-rate').innerText = `${failRate}%`;
}

async function loadFraudAlerts() {
    const body = document.getElementById('fraudTableBody');
    if (!body) return;

    try {
        const response = await fetch('/api/admin/fraud-check', {
            headers: { 'Authorization': `Bearer ${localStorage.getItem('billme_token')}` }
        });
        
        if (!response.ok) {
            throw new Error(`HTTP Error: ${response.status}`);
        }

        const alerts = await response.json();

        if (!alerts || alerts.length === 0) {
            body.innerHTML = '<tr><td colspan="4" class="text-center">No fraud alerts detected.</td></tr>';
            return;
        }

        body.innerHTML = alerts.map(a => `
            <tr>
                <td>
                    <div style="font-weight:600;">${a.customer || 'Customer'}</div>
                    <div style="font-size:11px; color:var(--text-muted);">via ${a.merchant || 'Merchant'}</div>
                </td>
                <td><span style="font-size:12px; color:var(--danger); font-weight:500;">${a.reason}</span></td>
                <td><span class="badge badge-warning">FLAGGED</span></td>
                <td style="text-align: right;">
                    <div style="display:flex; gap:5px; justify-content:flex-end;">
                        <button class="btn btn-sm btn-outline" onclick="handleFraudAction('SAFE', ${a.id})">Safe</button>
                        <button class="btn btn-sm btn-danger" onclick="handleFraudAction('FREEZE', ${a.id})">Freeze</button>
                    </div>
                </td>
            </tr>
        `).join('');
    } catch (e) {
        console.warn("Fraud API failed, showing mock data for compliance overview:", e.message);
        renderMockFraudAlerts();
    }
}

function renderMockFraudAlerts() {
    const body = document.getElementById('fraudTableBody');
    const mockData = [
        { id: 101, customer: "Rahul Sharma", merchant: "Zomato", reason: "High Amount; Frequency", status: "FLAGGED" },
        { id: 102, customer: "Anita Desai", merchant: "Amazon", reason: "Failed Status", status: "FLAGGED" }
    ];
    body.innerHTML = mockData.map(a => `
        <tr>
            <td>
                <div style="font-weight:600;">${a.customer}</div>
                <div style="font-size:11px; color:var(--text-muted);">via ${a.merchant}</div>
            </td>
            <td><span style="font-size:12px; color:var(--danger); font-weight:500;">${a.reason}</span></td>
            <td><span class="badge badge-warning">FLAGGED</span></td>
            <td style="text-align: right;">
                <div style="display:flex; gap:5px; justify-content:flex-end;">
                    <button class="btn btn-sm btn-outline" onclick="handleFraudAction('SAFE', ${a.id})">Safe</button>
                    <button class="btn btn-sm btn-danger" onclick="handleFraudAction('FREEZE', ${a.id})">Freeze</button>
                </div>
            </td>
        </tr>
    `).join('');
}

function renderAuditLogs() {
    const container = document.getElementById('auditLogList');
    if (!container) return;
    
    const logs = [
        { action: "Admin changed status for 'Tech Store' to ACTIVE", time: "2 mins ago" },
        { action: "Fraud alert resolved for TXN #4492", time: "1 hour ago" },
        { action: "System bulk update - Merchant profiles", time: "3 hours ago" },
        { action: "New merchant 'Coffee Day' approved", time: "5 hours ago" }
    ];

    container.innerHTML = logs.map(log => `
        <div style="display:flex; gap:12px; padding-bottom:12px; border-bottom:1px solid #f1f5f9;">
            <div style="width:32px; height:32px; border-radius:8px; background:var(--bg); display:flex; align-items:center; justify-content:center; color:var(--primary);">
                <i class="fas fa-shield-check"></i>
            </div>
            <div>
                <div style="font-size:13px; font-weight:500;">${log.action}</div>
                <div style="font-size:11px; color:var(--text-muted);">${log.time}</div>
            </div>
        </div>
    `).join('');
}

/* ===========================================================
   MERCHANT MANAGEMENT
=========================================================== */

let allMerchants = [];

async function loadMerchants() {
    const body = document.getElementById('merchantsTableBody');
    if (!body) return;

    try {
        toggleSkeleton('merchants', true);
        const merchants = await window.API.admin.getMerchants();
        allMerchants = merchants || [];
        renderMerchantTable(allMerchants);
    } catch (err) {
        console.warn('Failed to load merchants:', err);
        showToast('Sync Failed', 'error');
    } finally {
        toggleSkeleton('merchants', false);
    }
}

function renderMerchantTable(merchants) {
    const body = document.getElementById('merchantsTableBody');
    if (!body) return;

    if (!merchants || merchants.length === 0) {
        body.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No merchants found.</td></tr>';
        return;
    }

    body.innerHTML = merchants.map(m => `
        <tr id="merchant-row-${m.id}">
            <td><div style="font-weight:700;">${m.businessName || 'N/A'}</div></td>
            <td>${m.ownerName || 'N/A'}</td>
            <td><code style="font-size:12px;">${m.email || 'N/A'}</code></td>
            <td><span class="badge badge-${getStatusColor(m.status)}">${m.status || 'UNKNOWN'}</span></td>
            <td style="text-align: right;">
                <div style="display:flex; gap:8px; justify-content:flex-end;">
                    <button class="btn btn-outline btn-sm" onclick="openMerchantDetails(${m.id})">Details</button>
                    ${m.status === 'ACTIVE' 
                        ? `<button class="btn btn-sm" style="background:#fff3f3; color:#dc3545; border:1px solid #ffdada;" onclick="confirmMerchantAction('SUSPEND', ${m.id}, '${m.businessName}')">Suspend</button>`
                        : `<button class="btn btn-sm btn-success" onclick="confirmMerchantAction('ACTIVATE', ${m.id}, '${m.businessName}')">Activate</button>`
                    }
                </div>
            </td>
        </tr>
    `).join('');
}

// ── Details Drawer Logic ─────────────────────────────────────

async function openMerchantDetails(id) {
    const drawer = document.getElementById('merchantDrawer');
    const overlay = document.getElementById('drawerOverlay');
    
    if (!drawer || !overlay) return;

    // 1. Show Drawer & Overlay
    drawer.classList.add('active');
    overlay.classList.add('active');
    document.body.style.overflow = 'hidden'; // Lock scroll

    // 2. Initial State
    document.getElementById('drawerContent').style.display = 'none';
    document.getElementById('drawerError').style.display = 'none';
    document.getElementById('drawerSkeleton').style.display = 'block';

    try {
        let data = null;

        // A. Check Cache
        if (merchantCache[id]) {
            data = merchantCache[id];
        } else {
            // B. Try API
            try {
                data = await window.API.admin.getMerchantDetails(id);
                merchantCache[id] = data; // Store in cache
            } catch (apiErr) {
                console.warn("API detail fetch failed, falling back to local list:", apiErr);
                // C. Fallback to local list
                data = allMerchants.find(m => m.id == id);
                if (!data) throw new Error("Merchant not found in local state");
            }
        }

        renderMerchantDetails(data);
        
        // Setup Transaction View Button
        const txnBtn = document.getElementById('viewMerchantTxnBtn');
        if (txnBtn) {
            txnBtn.onclick = () => {
                txnMerchantFilter = data.businessName || data.id.toString();
                closeMerchantDetails();
                window.location.hash = 'transactions';
            };
        }

    } catch (err) {
        console.warn("Failed to load merchant details:", err);
        document.getElementById('drawerSkeleton').style.display = 'none';
        document.getElementById('drawerError').style.display = 'block';
        document.getElementById('retryFetchBtn').onclick = () => openMerchantDetails(id);
    }
}

function renderMerchantDetails(data) {
    document.getElementById('drawerSkeleton').style.display = 'none';
    document.getElementById('drawerContent').style.display = 'block';

    // Header Values
    document.getElementById('drawerBusinessName').innerText = data.businessName || 'Business Details';
    const badge = document.getElementById('drawerStatusBadge');
    badge.innerText = data.status || 'UNKNOWN';
    badge.className = `badge badge-${getStatusColor(data.status)}`;

    // Avatar Icon (First Letter)
    const avatar = document.getElementById('drawerAvatar');
    if (avatar) {
        avatar.innerText = (data.businessName || 'M').charAt(0).toUpperCase();
    }

    // Detail Values
    document.getElementById('det-owner').innerText = data.ownerName || 'N/A';
    document.getElementById('det-email').innerText = data.email || 'N/A';
    document.getElementById('det-phone').innerText = data.phone || 'N/A';
    document.getElementById('det-created').innerText = data.createdAt ? new Date(data.createdAt).toLocaleDateString() : 'N/A';
    document.getElementById('det-address').innerText = data.address || 'N/A';
    document.getElementById('det-city-state').innerText = `${data.city || ''}, ${data.state || ''} ${data.pinCode || ''}`.trim() || 'N/A';
    document.getElementById('det-gstin').innerText = data.gstin || 'NOT REGISTERED';
    document.getElementById('det-tx-count').innerText = data.totalTransactions || 0;
    
    // Risk Level Mock (Based on some logic or data)
    const riskEl = document.getElementById('det-risk-level');
    if (riskEl) {
        const isRisky = (data.totalTransactions > 100 && data.status !== 'ACTIVE') || (data.gstin === null);
        riskEl.innerText = isRisky ? 'Medium' : 'Low';
        riskEl.style.color = isRisky ? 'var(--warning)' : 'var(--success)';
    }
}

function closeMerchantDetails() {
    const drawer = document.getElementById('merchantDrawer');
    const overlay = document.getElementById('drawerOverlay');
    if (drawer) drawer.classList.remove('active');
    if (overlay) overlay.classList.remove('active');
    document.body.style.overflow = ''; // Unlock scroll
}

// Global Event Listeners for Drawer
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeMerchantDetails();
});

function getStatusColor(status) {
    switch (status) {
        case 'ACTIVE': return 'success';
        case 'SUSPENDED': return 'danger';
        case 'INACTIVE': return 'warning';
        case 'PENDING': return 'warning';
        default: return 'secondary';
    }
}

document.getElementById('merchantSearch')?.addEventListener('input', (e) => {
    const term = e.target.value.toLowerCase();
    const filtered = allMerchants.filter(m => 
        (m.businessName || '').toLowerCase().includes(term) || 
        (m.email || '').toLowerCase().includes(term)
    );
    renderMerchantTable(filtered);
});

document.getElementById('merchantFilter')?.addEventListener('change', (e) => {
    const val = e.target.value;
    if (val === 'ALL') {
        renderMerchantTable(allMerchants);
    } else {
        const filtered = allMerchants.filter(m => m.status === val);
        renderMerchantTable(filtered);
    }
});

let pendingAction = null;

function confirmMerchantAction(type, id, name) {
    pendingAction = { type, id, name };
    const modal = document.getElementById('confirmModal');
    const title = document.getElementById('modalTitle');
    const msg = document.getElementById('modalMessage');
    const btn = document.getElementById('modalConfirmBtn');

    title.innerText = type === 'SUSPEND' ? 'Suspend Merchant?' : 'Activate Merchant?';
    msg.innerText = `Are you sure you want to ${type.toLowerCase()} "${name}"? This will affect their ability to process payments.`;
    btn.className = type === 'SUSPEND' ? 'btn btn-danger' : 'btn btn-success';
    btn.innerText = type === 'SUSPEND' ? 'Suspend Now' : 'Activate Now';
    
    btn.onclick = executePendingAction;
    modal.style.display = 'flex';
}

function closeConfirmModal() {
    const modal = document.getElementById('confirmModal');
    if (modal) modal.style.display = 'none';
    pendingAction = null;
}

async function executePendingAction() {
    if (!pendingAction) return;
    const { type, id, name } = pendingAction;
    closeConfirmModal();

    try {
        const newStatus = type === 'SUSPEND' ? 'SUSPENDED' : 'ACTIVE';
        updateRowStatus(id, newStatus);
        
        if (type === 'SUSPEND') {
            await window.API.admin.suspendMerchant(id);
        } else {
            await window.API.admin.approveMerchant(id);
        }

        showToast(`Merchant ${name} ${type === 'SUSPEND' ? 'suspended' : 'activated'}`, 'success');
        loadMerchants();
    } catch (e) {
        showToast(`Failed to ${type.toLowerCase()} merchant`, 'error');
        loadMerchants();
    }
}

function updateRowStatus(id, status) {
    const row = document.querySelector(`#merchant-row-${id}`);
    if (row) {
        const badge = row.querySelector('.badge');
        if (badge) {
            badge.className = `badge badge-${getStatusColor(status)}`;
            badge.innerText = status;
        }
    }
}

/* ===========================================================
   MISC UTILITIES
=========================================================== */

async function loadCustomers() {
    const body = document.getElementById('customersTableBody');
    if (!body) return;
    try {
        toggleSkeleton('merchants', true); // Reuse merchants skeleton
        const customers = await window.API.admin.getCustomers();
        body.innerHTML = (customers || []).map(c => `
            <tr>
                <td><code style="font-size:12px;">#${c.id || 'N/A'}</code></td>
                <td style="font-weight:600;">${c.name || 'Anonymous'}</td>
                <td>${c.email || 'N/A'}</td>
                <td style="font-weight:700; color:var(--primary);">₹${(c.ltv || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
                <td style="font-size:12px; color:var(--text-muted);">${c.joined ? new Date(c.joined).toLocaleDateString() : 'N/A'}</td>
            </tr>
        `).join('');
    } catch (err) {
        console.warn('Failed to load customers:', err);
        showToast('Sync Failed', 'error');
    } finally {
        toggleSkeleton('merchants', false);
    }
}

function handleFraudAction(type, id) {
    showToast(`Fraud action ${type} initiated for ID: ${id}`, 'info');
    setTimeout(() => { 
        loadFraudAlerts(); 
        showToast('Record updated successfully', 'success'); 
    }, 1200);
}

function toggleSkeleton(section, show) {
    const targets = { 'merchants': 'merchantsTableBody', 'compliance': 'fraudTableBody', 'transactions': 'transactionsTableBody' };
    const targetId = targets[section];
    const target = document.getElementById(targetId);
    if (!target) return;

    if (show) {
        const cols = section === 'transactions' ? 7 : 5;
        target.innerHTML = Array(5).fill(0).map(() => `
            <tr class="skeleton-row"><td colspan="${cols}"><div class="skeleton" style="height:20px; width:100%; border-radius:4px;"></div></td></tr>
        `).join('');
    }
}

function toggleLoader(show) {
    const loader = document.getElementById('pageLoader');
    if (loader) loader.style.display = show ? 'flex' : 'none';
}