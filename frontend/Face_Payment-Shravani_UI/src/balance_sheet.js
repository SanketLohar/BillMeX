// ============================================================
//  BillMe — Balance Sheet Logic (balance_sheet.js)
//  Financial values: always ₹0 until wallet/ledger APIs land
//  Invoice counts: real data from /merchant/invoices
// ============================================================
'use strict';

let chartMonthly = null, chartStatus = null, chartMethods = null;

/**
 * Helper to format numbers into Indian Currency format (₹ XX,XX,XXX.XX)
 */
const formatCurrency = (val) => {
    const amount = Number(val) || 0;
    return new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(amount);
};

document.addEventListener('DOMContentLoaded', async () => {
    // Auth check (requires auth.js)
    if (window.requireAuth) {
        requireAuth('../src/login.html');
    }

    await loadBalanceSheet();

    // Event Listeners
    document.getElementById('refreshBtn')?.addEventListener('click', loadBalanceSheet);
    
    // Period Toggle listeners
    document.querySelectorAll('input[name="period"]').forEach(radio => {
        radio.addEventListener('change', () => {
            console.log(`Period changed to: ${radio.value}`);
            loadBalanceSheet();
        });
    });

    // Fallback for the hidden select dropdown (if it's still used by some legacy logic)
    document.getElementById('periodFilter')?.addEventListener('change', loadBalanceSheet);
});

async function loadBalanceSheet() {
    toggleLoader(true);
    
    try {
        // ── 1. Identify Period (for future API support) ──
        const period = document.querySelector('input[name="period"]:checked')?.value || 'monthly';

        // ── 2. Attempt wallet/balance-sheet APIs (graceful stubs return null) ──
        // Note: The backend might not support ?period=... yet, but we prepare the request structure.
        const [balanceSheet, wallet, ledger] = await Promise.all([
            API.wallet.getBalanceSheet().catch(() => null),
            API.wallet.getWallet().catch(() => null),
            API.wallet.getTransactions({ limit: 1 }).catch(() => null), // stub for ledger activity
        ]);

        // ── 3. Financial Metrics Strategy (Safe Fallbacks) ──
        
        // Revenue Source: preferring balance-sheet over wallet balance
        const grossRevenue = balanceSheet?.totalRevenue || balanceSheet?.revenue || 0;
        
        // Refunds: Fallback to 0 if not supported by current API
        const refunds      = balanceSheet?.totalRefunds || 0; 

        // Fees: Processing fees are usually provided; Platform fees fallback to 0
        const processingFees = balanceSheet?.processingFees || wallet?.platformFee || 0;
        const platformFees   = balanceSheet?.platformFees || 0;
        const totalFees      = processingFees + platformFees;

        // Balance & Escrow
        const walletBalance  = wallet?.balance || wallet?.currentBalance || 0;
        const escrowBalance  = balanceSheet?.escrow || wallet?.escrowBalance || 0;
        const withdrawals    = balanceSheet?.withdrawals || wallet?.totalWithdrawn || 0;

        // Net Settlement Computation: revenue - fees - refunds
        const netSettlement  = grossRevenue - totalFees - refunds;

        // ── 4. Render UI Elements ──
        renderSummaryCards(grossRevenue, refunds, totalFees, netSettlement);
        renderLegacyCards(grossRevenue, escrowBalance, withdrawals, totalFees, netSettlement);
        
        // Update Timestamp
        const timeEl = document.getElementById('lastUpdated');
        if (timeEl) timeEl.textContent = `Last updated: ${new Date().toLocaleTimeString()}`;

        // ── 5. Invoice counts (Secondary metrics) ──
        const invoices = await API.merchant.getInvoices().catch(() => []);
        renderInvoiceCounts(invoices);
        renderCharts(invoices);

    } catch (err) {
        console.error('Balance Sheet Refresh Failed:', err);
        if (window.showToast) window.showToast('Failed to sync financial data', 'error');
    } finally {
        toggleLoader(false);
    }
}

function toggleLoader(show) {
    const loader = document.getElementById('balanceLoader');
    if (loader) loader.style.display = show ? 'flex' : 'none';
}

function renderSummaryCards(gross, refunds, fees, net) {
    const setText = (id, val) => {
        const el = document.getElementById(id);
        if (el) el.textContent = formatCurrency(val);
    };

    setText('fs-gross', gross);
    setText('fs-refunds', refunds);
    setText('fs-fees', fees);
    setText('fs-net', net);
}

function renderLegacyCards(revenue, escrow, withdrawals, fees, net) {
    const fmt = val => formatCurrency(val);
    const set = (id, v) => {
        const el = document.getElementById(id);
        if (el) el.textContent = fmt(v);
    };

    // Old Stat Cards
    set('bs-revenue', revenue);
    set('bs-escrow', escrow);
    set('bs-withdrawals', withdrawals);
    set('bs-fees', fees);

    // Summary Table (Numeric formatting without currency symbol for consistency in table)
    const num = (id, v) => {
        const el = document.getElementById(id);
        if (el) el.textContent = (v || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 });
    };

    num('st-rev', revenue);
    num('st-escrow', escrow);
    num('st-wd', withdrawals);
    num('st-fees', fees);
    num('st-net', net);
}

function renderInvoiceCounts(invoices) {
    let paid = 0, pending = 0, unpaid = 0;
    invoices.forEach(inv => {
        const s = (inv.status || '').toUpperCase();
        if (s === 'PAID') paid++;
        else if (s === 'PENDING') pending++;
        else unpaid++;
    });
    
    const set = (id, v) => {
        const el = document.getElementById(id);
        if (el) el.textContent = v;
    };

    set('ic-total', invoices.length);
    set('ic-paid', paid);
    set('ic-pending', pending);
    set('ic-unpaid', unpaid);
}

function renderCharts(invoices) {
    // ── Monthly activity (last 12 months) ──
    const monthLabels = [], monthData = {};
    const now = new Date();
    for (let i = 11; i >= 0; i--) {
        const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        monthLabels.push(d.toLocaleString('default', { month: 'short', year: '2-digit' }));
        monthData[key] = 0;
    }
    invoices.forEach(inv => {
        const dateStr = inv.issuedAt || inv.createdAt;
        if (!dateStr) return;
        const d = new Date(dateStr);
        const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        if (key in monthData) monthData[key]++;
    });

    // ── Status counts ──
    const sc = { PAID: 0, PENDING: 0, UNPAID: 0, CANCELLED: 0 };
    invoices.forEach(i => {
        const s = (i.status || 'UNPAID').toUpperCase();
        if (s in sc) sc[s]++;
        else sc.UNPAID++;
    });

    // ── Method counts ──
    const mc = { UPI: 0, FACEPAY: 0, CARD: 0, OTHER: 0 };
    invoices.forEach(i => {
        const m = (i.paymentMethod || 'OTHER').toUpperCase();
        if (m in mc) mc[m]++;
        else if (m === 'FACE_PAY') mc.FACEPAY++;
        else mc.OTHER++;
    });

    // Destroy old charts to prevent ghosting
    if (chartMonthly) chartMonthly.destroy();
    if (chartStatus) chartStatus.destroy();
    if (chartMethods) chartMethods.destroy();

    const ctxMonthly = document.getElementById('chartMonthly');
    if (ctxMonthly) {
        chartMonthly = new Chart(ctxMonthly, {
            type: 'bar',
            data: {
                labels: monthLabels,
                datasets: [{
                    label: 'Invoices',
                    data: Object.values(monthData),
                    backgroundColor: 'rgba(26,115,232,0.75)',
                    borderRadius: 5
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
            }
        });
    }

    const ctxStatus = document.getElementById('chartStatus');
    if (ctxStatus) {
        chartStatus = new Chart(ctxStatus, {
            type: 'doughnut',
            data: {
                labels: Object.keys(sc),
                datasets: [{
                    data: Object.values(sc),
                    backgroundColor: ['#34a853', '#fbbc04', '#ea4335', '#9aa0a6'],
                    borderWidth: 2,
                    borderColor: '#fff'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { position: 'bottom' } },
                cutout: '70%'
            }
        });
    }

    const ctxMethods = document.getElementById('chartMethods');
    if (ctxMethods) {
        chartMethods = new Chart(ctxMethods, {
            type: 'pie',
            data: {
                labels: ['UPI', 'FacePay', 'Card', 'Other'],
                datasets: [{
                    data: [mc.UPI, mc.FACEPAY, mc.CARD, mc.OTHER],
                    backgroundColor: ['#1a73e8', '#34a853', '#fbbc04', '#9aa0a6'],
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
}
