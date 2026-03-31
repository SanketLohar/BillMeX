document.addEventListener('DOMContentLoaded', async () => {
    const params = new URLSearchParams(window.location.search);
    const invoiceNumber =
        params.get('invoiceId') ||
        params.get('invoiceNumber') ||
        params.get('num');
    const tokenParam = params.get('token');

    console.log("URL:", window.location.href);
    console.log("invoiceNumber:", invoiceNumber);
    console.log("token:", tokenParam);

    const loader = document.getElementById('loader');
    const content = document.getElementById('payment-content');
    const successArea = document.getElementById('success-area');
    const actionArea = document.getElementById('action-area');
    const cameraSection = document.getElementById('cameraSection');
    const video = document.getElementById('video');

    const merchantName = document.getElementById('merchant-name');
    const merchantGst = document.getElementById('merchant-gst');
    const itemsList = document.getElementById('items-list');
    const subTotalElem = document.getElementById('sub-total');
    const platformFeeElem = document.getElementById('platform-fee');
    const cgstElem = document.getElementById('cgst-total');
    const sgstElem = document.getElementById('sgst-total');
    const taxTotalElem = document.getElementById('tax-total');
    const grandTotalElem = document.getElementById('grand-total');
    const invoiceLabel = document.getElementById('invoice-label');

    let currentInvoice = null;
    let stream = null;

    if (!invoiceNumber || !tokenParam) {
        showToast('Invalid payment link.', 'error');
        return;
    }

    try {
        const invoice = await window.API.invoice.getPublic(invoiceNumber, tokenParam);
        currentInvoice = invoice;

        merchantName.innerText = invoice.merchantName || 'BillMe Merchant';
        merchantGst.innerText = invoice.merchantGSTIN ? `GSTIN: ${invoice.merchantGSTIN}` : 'GST Not Applicable';
        invoiceLabel.innerText = `Invoice #${invoice.invoiceNumber}`;

        itemsList.innerHTML = (invoice.items || []).map(item => `
            <div class="item-row">
                <span>${esc(item.productName)} (x${item.quantity})</span>
                <span>₹${(item.totalPrice || 0).toFixed(2)}</span>
            </div>
        `).join('');

        subTotalElem.innerText = `₹${(invoice.subtotal || 0).toFixed(2)}`;
        platformFeeElem.innerText = `₹${(invoice.processingFee || 0).toFixed(2)}`;
        cgstElem.innerText = `₹${(invoice.cgstAmount || 0).toFixed(2)}`;
        sgstElem.innerText = `₹${(invoice.sgstAmount || 0).toFixed(2)}`;
        if (taxTotalElem) taxTotalElem.innerText = `₹${(invoice.gstTotal || 0).toFixed(2)}`;
        grandTotalElem.innerText = `₹${(invoice.totalPayable || 0).toFixed(2)}`;

        loader.style.display = 'none';

        if (invoice.status === 'PAID') {
            showSuccess();
            return;
        }

        content.style.display = 'block';

    } catch (err) {
        showToast('Failed to load invoice', 'error');
        console.error(err);
    }

    // =========================
    // FACEPAY START
    // =========================

    document.getElementById('btn-facepay')?.addEventListener('click', async () => {
        try {
            const MODEL_URL = 'https://justadudewhohacks.github.io/face-api.js/models';

            await faceapi.nets.ssdMobilenetv1.loadFromUri(MODEL_URL);
            await faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_URL);
            await faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL);

            actionArea.style.display = 'none';
            cameraSection.style.display = 'block';

            stream = await navigator.mediaDevices.getUserMedia({ video: {} });
            video.srcObject = stream;

        } catch (err) {
            showToast('Camera error', 'error');
        }
    });

    document.getElementById('btn-verify')?.addEventListener('click', async (e) => {
        const btn = e.currentTarget;
        if (btn.disabled) return; // Prevent duplicate clicks

        try {
            // 1. Initial Processing State
            btn.disabled = true;
            btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Processing...';
            showToast('Processing payment...', 'info');

            const detection = await faceapi
                .detectSingleFace(video)
                .withFaceLandmarks()
                .withFaceDescriptor();

            if (!detection) throw new Error("No face detected. Please look into the camera.");

            const BASE_URL = window.API_BASE_URL;
            const embedding = Array.from(detection.descriptor).map(Number);

            // Fetch call with timeout/error safety
            const res = await fetch(`${BASE_URL}/public/pay/face`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    invoiceNumber: invoiceNumber,
                    token: tokenParam,
                    embedding: embedding
                })
            });

            if (!res.ok) {
                const errText = await res.text();
                throw new Error(errText || "Payment verification failed");
            }

            // 2. Success Scenario
            showToast('Payment successful', 'success');
            
            // Short delay to let user see success toast before redirecting
            setTimeout(() => {
                showSuccess();
            }, 1000);

        } catch (err) {
            console.error(err);
            
            // 3. Error/Exception Scenarios
            const userMsg = err.message || "Something went wrong. Please try again.";
            showToast(userMsg, 'error');

            // Re-enable button on failure
            btn.disabled = false;
            btn.innerHTML = '<i class="fas fa-fingerprint"></i> Verify & Pay';
        }
    });

    // =========================
    // RAZORPAY UPI / CARD
    // =========================
    document.getElementById('btn-upi')?.addEventListener('click', async () => {
        try {
            const BASE_URL = window.API_BASE_URL;

            // 1. Create Razorpay Order
            const token = localStorage.getItem("billme_token");
            const orderRes = await fetch(`${BASE_URL}/api/payments/create-order/${currentInvoice.id}?token=${tokenParam || ''}`, {
                method: "POST",
                headers: {
                    "Authorization": token ? `Bearer ${token}` : ""
                }
            });

            if (!orderRes.ok) {
                const errText = await orderRes.text();
                const errJson = errText.startsWith('{') ? JSON.parse(errText) : null;
                throw new Error(errJson ? (errJson.error || errJson.message) : errText || "Failed to create payment order");
            }
            const orderId = await orderRes.text();

            // 2. Configure Razorpay Options
            const options = {
                "key": "rzp_test_SIgyziHVJLgRT2", // Test Key from application.properties
                "amount": (currentInvoice.totalPayable * 100).toString(),
                "currency": "INR",
                "name": "BillMe",
                "description": `Invoice #${currentInvoice.invoiceNumber}`,
                "order_id": orderId,
                "handler": async function (response) {
                    // 3. Verify Payment
                    try {
                        const verifyRes = await fetch(`${BASE_URL}/api/payments/verify`, {
                            method: "POST",
                            headers: { "Content-Type": "application/json" },
                            body: JSON.stringify({
                                razorpay_order_id: response.razorpay_order_id,
                                razorpay_payment_id: response.razorpay_payment_id,
                                razorpay_signature: response.razorpay_signature
                            })
                        });

                        if (verifyRes.ok) {
                            showSuccess();
                        } else {
                            const vErrText = await verifyRes.text();
                            const vErrJson = vErrText.startsWith('{') ? JSON.parse(vErrText) : null;
                            showToast(vErrJson ? (vErrJson.error || vErrJson.message) : vErrText || "Payment verification failed", "error");
                        }
                    } catch (err) {
                        showToast("Verification error", "error");
                    }
                },
                "prefill": {
                    "name": currentInvoice.customerName,
                    "email": currentInvoice.customerEmail
                },
                "theme": {
                    "color": "#2563eb"
                }
            };

            const rzp = new Razorpay(options);
            rzp.open();

        } catch (err) {
            console.error(err);
            showToast(err.message, 'error');
        }
    });

    // =========================
    // SUCCESS
    // =========================

    function showSuccess() {
        if (stream) {
            stream.getTracks().forEach(t => t.stop());
        }

        // Navigate to dedicated success page for validation, idempotency, and smart redirect
        window.location.href = `payment/success.html?invoiceId=${invoiceNumber}&token=${tokenParam || ''}`;
    }

    function esc(str) {
        return String(str || '').replace(/</g, "&lt;").replace(/>/g, "&gt;");
    }
});