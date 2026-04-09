document.addEventListener('DOMContentLoaded', async () => {
    const params = new URLSearchParams(window.location.search);

    const invoiceNumber = params.get('invoiceId');
    const tokenParam = params.get('token'); // 🔑 KEY: detect public flow

    const loadingState = document.getElementById('loading-state');
    const successState = document.getElementById('success-state');
    const errorState = document.getElementById('error-state');

    const amountEl = document.getElementById('paid-amount');
    const returnBtn = document.getElementById('return-btn');

    // 🔥 NEW: Determine redirect properly
    const redirectRoute = getRedirectRoute(tokenParam);

    // 🛡️ ZERO-REGRESSION: Safe Route Fallback
    const fallbackRoute = `${window.location.origin}/src/login.html`;

    // Button UI & Action
    if (redirectRoute) {
        returnBtn.innerText = "Go to Dashboard";
        returnBtn.addEventListener('click', () => {
            window.location.href = redirectRoute;
        });
    } else {
        returnBtn.innerText = "Return to Login";
        returnBtn.addEventListener('click', () => {
            window.location.href = fallbackRoute;
        });
    }

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

            // 🔥 ZERO-REGRESSION: Redirect ONLY if internal dashboard flow
            if (redirectRoute) {
                setTimeout(() => {
                    window.location.href = redirectRoute;
                }, 3000);
            }

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

    // ✅ FINAL V4 CORRECT ROUTING LOGIC (Priority Based)
    function getRedirectRoute(tokenParam) {
        // 🛡️ Priority 1: Email source (Always stay on page)
        if (tokenParam) {
            console.log("[Success] Email-based payment detected. Auto-redirect disabled.");
            return null;
        }

        // 🛡️ Priority 2: Session-based dashboard flow
        const token = localStorage.getItem('billme_token');
        const role = (localStorage.getItem('billme_role') || '').toUpperCase();

        if (token && role === 'CUSTOMER') {
            return `${window.location.origin}/dashboard/customer.html`;
        }
        if (token && role === 'MERCHANT') {
            return `${window.location.origin}/dashboard/merchant.html`;
        }

        // 🔐 Default: No auto-redirect
        return null;
    }
});