document.addEventListener('DOMContentLoaded', async () => {
    const params = new URLSearchParams(window.location.search);

    const invoiceNumber = params.get('invoiceId');
    const tokenParam = params.get('token'); // 🔑 KEY: detect public flow

    const loadingState = document.getElementById('loading-state');
    const successState = document.getElementById('success-state');
    const errorState = document.getElementById('error-state');

    const amountEl = document.getElementById('paid-amount');
    const returnBtn = document.getElementById('return-btn');

    const safeRoute = `${window.location.origin}/src/login.html`;

    // 🔥 NEW: Determine redirect properly
    const redirectRoute = getRedirectRoute(tokenParam);

    // Button text
    returnBtn.innerText = redirectRoute === safeRoute
        ? "Return to Login"
        : "Go to Dashboard";

    returnBtn.addEventListener('click', () => {
        window.location.href = redirectRoute;
    });

    if (!invoiceNumber) {
        showError("Invalid Link", "No invoice reference was provided.");
        return;
    }

    try {
        const invoice = await window.API.invoice.getPublic(invoiceNumber, tokenParam);

        if (invoice && invoice.status === 'PAID') {
            const amount = invoice.totalPayable || 0;
            amountEl.innerText = `₹${amount.toFixed(2)}`;

            // Show success
            loadingState.style.display = 'none';
            successState.style.display = 'block';

            // 🔥 Redirect ONLY after success verification
            setTimeout(() => {
                window.location.href = redirectRoute;
            }, 3000);

        } else {
            showError(
                "Payment Pending",
                "The invoice has not been marked as paid yet. Please try refreshing."
            );
        }

    } catch (err) {
        console.error("Success Validation Error:", err);
        showError(
            "Verification Error",
            "Could not verify payment. Please check your connection."
        );
    }

    function showError(title, desc) {
        loadingState.style.display = 'none';
        successState.style.display = 'none';
        errorState.style.display = 'block';

        if (title) document.getElementById('error-title').innerText = title;
        if (desc) document.getElementById('error-desc').innerText = desc;
    }

    // ✅ FINAL CORRECT ROUTING LOGIC
    function getRedirectRoute(tokenParam) {

        const token = localStorage.getItem('billme_token');
        const role = (localStorage.getItem('billme_role') || '').toUpperCase();

        // 🔥 CASE 1: PUBLIC FLOW (email)
        if (tokenParam) {
            return safeRoute; // NEVER go to dashboard
        }

        // 🔥 CASE 2: LOGGED-IN CUSTOMER FLOW
        if (token && role === 'CUSTOMER') {
            return `${window.location.origin}/dashboard/customer.html`;
        }

        // ⚠️ CASE 3: Wrong session (merchant/admin leftover)
        if (token && (role === 'MERCHANT' || role === 'ADMIN')) {
            console.warn("Unexpected role in payment flow:", role);
            return safeRoute;
        }

        // 🔐 DEFAULT
        return safeRoute;
    }
});