// ============================================================
//  BillMe — Balance Sheet Logic (balance_sheet.js)
//  Financial values: always ₹0 until wallet/ledger APIs land
//  Invoice counts: real data from /merchant/invoices
// ============================================================
'use strict';

let chartMonthly = null, chartStatus = null, chartMethods = null;

document.addEventListener('DOMContentLoaded', async () => {
    requireAuth('../src/register.html');
    await loadBalanceSheet();
    document.getElementById('refreshBtn')?.addEventListener('click', loadBalanceSheet);
    document.getElementById('periodFilter')?.addEventListener('change', loadBalanceSheet);
});

async function loadBalanceSheet() {
    // ── 1. Attempt wallet/balance-sheet APIs (graceful stubs return null) ──
    const [balanceSheet, wallet, ledger] = await Promise.all([
        API.merchant.getBalanceSheet(),
        API.merchant.getWallet(),
        API.merchant.getLedger(),
    ]);

    // ── 2. Financial values: only from real APIs, else ₹0 ──
    const revenue     = balanceSheet?.revenue     ?? wallet?.balance   ?? 0;
    const escrow      = balanceSheet?.escrow      ?? wallet?.escrowBalance ?? 0;
    const withdrawals = balanceSheet?.withdrawals ?? ledger?.withdrawals ?? 0;
    const fees        = balanceSheet?.processingFees ?? 0;
    const net         = revenue - withdrawals - fees;

    renderCards(revenue, escrow, withdrawals, fees, net);

    // ── 3. Invoice counts (NOT used for financial reporting) ──
    const invoices = await API.merchant.getInvoices().catch(() => []);
    renderInvoiceCounts(invoices);
    renderCharts(invoices);
}

function renderCards(revenue, escrow, withdrawals, fees, net) {
    const fmt = v => '₹' + (v||0).toFixed(2);
    document.getElementById('bs-revenue').textContent     = fmt(revenue);
    document.getElementById('bs-escrow').textContent      = fmt(escrow);
    document.getElementById('bs-withdrawals').textContent = fmt(withdrawals);
    document.getElementById('bs-fees').textContent        = fmt(fees);

    document.getElementById('st-rev').textContent    = (revenue||0).toFixed(2);
    document.getElementById('st-escrow').textContent = (escrow||0).toFixed(2);
    document.getElementById('st-wd').textContent     = (withdrawals||0).toFixed(2);
    document.getElementById('st-fees').textContent   = (fees||0).toFixed(2);
    document.getElementById('st-net').textContent    = (net||0).toFixed(2);
}

function renderInvoiceCounts(invoices) {
    let paid = 0, pending = 0, unpaid = 0;
    invoices.forEach(inv => {
        const s = (inv.status||'').toUpperCase();
        if (s === 'PAID') paid++;
        else if (s === 'PENDING') pending++;
        else unpaid++;
    });
    document.getElementById('ic-total').textContent   = invoices.length;
    document.getElementById('ic-paid').textContent    = paid;
    document.getElementById('ic-pending').textContent = pending;
    document.getElementById('ic-unpaid').textContent  = unpaid;
}

function renderCharts(invoices) {
    // Monthly activity (last 12 months — invoice counts)
    const monthLabels = [], monthData = {};
    const now = new Date();
    for (let i = 11; i >= 0; i--) {
        const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
        const key = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`;
        monthLabels.push(d.toLocaleString('default',{ month:'short', year:'2-digit' }));
        monthData[key] = 0;
    }
    invoices.forEach(inv => {
        if (!inv.issuedAt) return;
        const d = new Date(inv.issuedAt);
        const key = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`;
        if (key in monthData) monthData[key]++;
    });

    // Status counts
    const sc = { PAID:0, PENDING:0, UNPAID:0, CANCELLED:0 };
    invoices.forEach(i => { const s=(i.status||'UNPAID').toUpperCase(); if(s in sc) sc[s]++; else sc.UNPAID++; });

    // Method counts
    const mc = { UPI:0, FACEPAY:0, CARD:0, OTHER:0 };
    invoices.forEach(i => { const m=(i.paymentMethod||'OTHER').toUpperCase(); if(m in mc) mc[m]++; else mc.OTHER++; });

    // Destroy old charts
    if (chartMonthly) chartMonthly.destroy();
    if (chartStatus)  chartStatus.destroy();
    if (chartMethods) chartMethods.destroy();

    chartMonthly = new Chart(document.getElementById('chartMonthly'), {
        type: 'bar',
        data: {
            labels: monthLabels,
            datasets: [{ label: 'Invoices', data: Object.values(monthData),
                backgroundColor: 'rgba(26,115,232,0.75)', borderRadius: 5 }]
        },
        options: { responsive:true, maintainAspectRatio:false,
            plugins:{ legend:{display:false} },
            scales:{ y:{ beginAtZero:true, ticks:{ stepSize:1 } } } }
    });

    chartStatus = new Chart(document.getElementById('chartStatus'), {
        type: 'doughnut',
        data: {
            labels: Object.keys(sc),
            datasets: [{ data: Object.values(sc),
                backgroundColor: ['#34a853','#fbbc04','#ea4335','#9aa0a6'],
                borderWidth: 2, borderColor: '#fff' }]
        },
        options: { responsive:true, maintainAspectRatio:false, plugins:{ legend:{ position:'bottom' } } }
    });

    chartMethods = new Chart(document.getElementById('chartMethods'), {
        type: 'pie',
        data: {
            labels: ['UPI','FacePay','Card','Other'],
            datasets: [{ data: Object.values(mc),
                backgroundColor: ['#1a73e8','#34a853','#fbbc04','#9aa0a6'],
                borderWidth: 2, borderColor: '#fff' }]
        },
        options: { responsive:true, maintainAspectRatio:false, plugins:{ legend:{ position:'bottom' } } }
    });
}
