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
    
    // Period Toggle listeners (Legacy fallback)
    document.querySelectorAll('input[name="period"]').forEach(radio => {
        radio.addEventListener('change', () => {
            console.log(`Period changed to (legacy): ${radio.value}`);
            loadBalanceSheet();
        });
    });

    // New Production Range Selector
    document.getElementById('reportRange')?.addEventListener('change', loadBalanceSheet);


    // Fallback for the hidden select dropdown (if it's still used by some legacy logic)
    document.getElementById('periodFilter')?.addEventListener('change', loadBalanceSheet);

    // Export Listeners
    document.getElementById('exportPnlExcel')?.addEventListener('click', (e) => { e.preventDefault(); downloadReport('PNL', 'EXCEL'); });
    document.getElementById('exportPnlPdf')?.addEventListener('click', (e) => { e.preventDefault(); downloadReport('PNL', 'PDF'); });
    document.getElementById('exportSummaryExcel')?.addEventListener('click', (e) => { e.preventDefault(); downloadReport('SUMMARY', 'EXCEL'); });
    document.getElementById('exportSummaryPdf')?.addEventListener('click', (e) => { e.preventDefault(); downloadReport('SUMMARY', 'PDF'); });
});

async function downloadReport(type, format) {
    const rangeSelect = document.getElementById('reportRange');
    const period = rangeSelect ? rangeSelect.value : (document.querySelector('input[name="period"]:checked')?.value || 'monthly');

    try {
        window.showToast?.(`Generating ${type} ${format}...`, 'info');
        let blob;
        if (type === 'PNL') {
            blob = await API.wallet.exportPnl({ format, range: period });
        } else {
            blob = await API.wallet.exportSummary({ format, range: period });
        }

        window.triggerBlobDownload(
            blob,
            `BillMeX_${type}_${period}_${new Date().toISOString().split('T')[0]}.${format === 'EXCEL' ? 'xlsx' : 'pdf'}`
        );
        window.showToast?.('Report downloaded successfully', 'success');
    } catch (err) {
        console.error('Export failed:', err);
        window.showToast?.('Failed to export report', 'error');
    }
}

async function loadBalanceSheet() {
    toggleLoader(true);
    
    try {
        console.log("[Analytics] Initializing analytics engine...");
        
        // Correctly initialize period from UI selection (New Select > Legacy Radio)
        const rangeSelect = document.getElementById('reportRange');
        const period = rangeSelect ? rangeSelect.value : (document.querySelector('input[name="period"]:checked')?.value || 'monthly');
        console.log(`[Analytics] Current range: ${period}`);


        // ── 2. Fetch Data (Legacy API + New Reporting APIs) ──
        const [wallet, pnl, summary] = await Promise.all([
            API.wallet.getWallet().catch(e => { console.error("Wallet error:", e); return null; }),
            API.wallet.getPnl({ range: period }).catch(e => { console.error("PNL error:", e); return null; }),
            API.wallet.getSummary({ range: period }).catch(e => { console.error("Summary error:", e); return null; })
        ]);

        if (!pnl && !summary) {
            window.showToast?.('Failed to load analytics', 'error');
            console.error("Critical: Both PNL and Summary failed to load.");
        }


        // ── 3. Financial Metrics Strategy (Deterministic Sources) ──
        
        // As requested:
        // summary.totalRevenue -> Revenue card
        // summary.totalWithdrawals -> Withdrawals
        // pnl.grossProfit -> Profit 
        const revenue = summary?.totalRevenue || 0;
        const withdrawals = summary?.totalWithdrawals || 0;
        const profit = pnl?.grossProfit || 0;
        
        // Other fallback mappings
        const escrowBalance  = wallet?.escrowBalance || 0;
        const totalFees      = pnl?.totalProcessingFees || 0;

        // ── 4. Render UI Elements ──
        renderSummaryCards(revenue, pnl?.totalRefunds || 0, totalFees, profit);
        renderLegacyCards(revenue, escrowBalance, withdrawals, totalFees, profit);
        setUnknownCogsAlert(pnl);
        
        // Update Timestamp
        const timeEl = document.getElementById('lastUpdated');
        if (timeEl) timeEl.textContent = `Last updated: ${new Date().toLocaleTimeString()}`;

        // ── 5. Invoice counts & Payment Methods ──
        const [invoices, paymentMethods] = await Promise.all([
            API.merchant.getInvoices().catch(() => []),
            API.merchant.getPaymentMethods().catch(() => ({}))
        ]);
        
        renderInvoiceCounts(invoices);
        renderCharts(invoices, summary, paymentMethods);


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

function setUnknownCogsAlert(pnl) {
    const alert = document.getElementById('unknownCogsAlert');
    const text  = document.getElementById('unknownCogsText');
    if (!alert) return;

    if (pnl && pnl.unknownCogsCount > 0) {
        alert.style.display = 'block';
        if (text) text.textContent = `Report contains ${pnl.unknownCogsCount} invoices with missing cost data (Impact: ${formatCurrency(pnl.unknownCogsRevenue)} excluded).`;
    } else {
        alert.style.display = 'none';
    }
}

function renderCharts(invoices, summary, paymentMethods) {
    // ── 1. Revenue & Withdrawal Trend (REAL DATA) ──
    const ctxTrend = document.getElementById('chartMonthly');
    if (ctxTrend && summary) {
        if (chartMonthly) chartMonthly.destroy();
        
        const revTrend = summary.revenueTrend || [];
        const wdTrend  = summary.withdrawalTrend || [];

        chartMonthly = new Chart(ctxTrend, {
            type: 'line',
            data: {
                labels: revTrend.map(d => d.label),
                datasets: [
                    {
                        label: 'Gross Revenue',
                        data: revTrend.map(d => d.value),
                        borderColor: '#1a73e8',
                        backgroundColor: 'rgba(26,115,232,0.1)',
                        fill: true,
                        pointRadius: 4,
                        tension: 0.3
                    },
                    {
                        label: 'Withdrawals',
                        data: wdTrend.map(d => d.value),
                        borderColor: '#34a853',
                        backgroundColor: 'rgba(52,168,83,0.1)',
                        fill: true,
                        pointRadius: 4,
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
                            label: (ctx) => `${ctx.dataset.label}: ${formatCurrency(ctx.raw)}`
                        }
                    }
                },
                scales: { 
                    y: { 
                        beginAtZero: true,
                        ticks: { callback: (val) => '₹' + val.toLocaleString('en-IN') }
                    } 
                }
            }
        });
    }

    // ── 2. Invoice Status Distribution ──
    const ctxStatus = document.getElementById('chartStatus');
    if (ctxStatus) {
        if (chartStatus) chartStatus.destroy();
        
        const sc = { PAID: 0, PENDING: 0, UNPAID: 0, CANCELLED: 0 };
        invoices.forEach(i => {
            const s = (i.status || 'UNPAID').toUpperCase();
            if (s in sc) sc[s]++;
            else sc.UNPAID++;
        });

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

    // ── 3. Payment Methods (REAL DATA) ──
    const ctxMethods = document.getElementById('chartMethods');
    if (ctxMethods) {
        if (chartMethods) chartMethods.destroy();
        
        const labels = paymentMethods ? Object.keys(paymentMethods) : ['UPI', 'FACE_PAY', 'CARD'];
        const values = paymentMethods ? Object.values(paymentMethods) : [0, 0, 0];

        chartMethods = new Chart(ctxMethods, {
            type: 'pie',
            data: {
                labels: labels.map(l => l.replace('_', ' ')),
                datasets: [{
                    data: values,
                    backgroundColor: ['#1a73e8', '#34a853', '#fbbc04', '#ea4335', '#9aa0a6'],
                    borderWidth: 2,
                    borderColor: '#fff'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { 
                    legend: { position: 'bottom' },
                    tooltip: {
                        callbacks: {
                            label: (ctx) => `${ctx.label}: ${ctx.raw} Invoices`
                        }
                    }
                }
            }
        });
    }
}

