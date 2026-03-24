// ============================================================
// BillMe — Centralized API Client
// ============================================================

const API_BASE_URL = "http://localhost:8080";

/* ===========================================================
   TOKEN HELPERS
=========================================================== */

const getToken = () => localStorage.getItem("billme_token");
const setToken = (t) => localStorage.setItem("billme_token", t);

const getRole = () => localStorage.getItem("billme_role");
const setRole = (r) => localStorage.setItem("billme_role", r);

const clearAuth = () => {
  localStorage.removeItem("billme_token");
  localStorage.removeItem("billme_role");
  localStorage.removeItem("billme_user_id");
};

/* ===========================================================
   CORE API CALL
=========================================================== */

async function apiCall(endpoint, options = {}) {

  const url = `${API_BASE_URL}${endpoint}`;

  const headers = {
    ...(options.headers || {}),
  };

  // Centralized Serialization Fix:
  // Automatically stringify body if it's an object and not FormData
  if (options.body && !(options.body instanceof FormData) && typeof options.body !== 'string') {
    options.body = JSON.stringify(options.body);
    headers["Content-Type"] = "application/json";
  } else if (options.body && typeof options.body === 'string') {
    // If body is already a string, assume it's JSON and set header
    headers["Content-Type"] = "application/json";
  }

  // Attach JWT token
  const token = getToken();
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
    console.log("API CALL AUTH HEADER ATTACHED:", endpoint);
  } else {
    console.warn("API CALL NO TOKEN FOUND:", endpoint);
  }

  let response;

  try {

    response = await fetch(url, {
      ...options,
      headers,
    });

  } catch (err) {

    throw new Error("Backend not reachable (port 8080)");

  }

  /* ======================
     TOKEN EXPIRED
  ====================== */

  if (response.status === 401) {
    console.warn("Session expired or unauthorized (401)");
    throw new Error("UNAUTHORIZED");
  }

  /* ======================
     RESPONSE PARSING
  ====================== */

  const contentType = response.headers.get("content-type") || "";

  let data;

  if (contentType.includes("application/json")) {

    data = await response.json();

  } else {

    data = await response.text();

  }

  if (!response.ok) {
  console.error("❌ API ERROR:", {
    url,
    status: response.status,
    response: data
  });

  throw new Error(
    typeof data === "string"
      ? data
      : data?.message || JSON.stringify(data) || `Request failed (${response.status})`
  );
}

  return data;
}

/* ===========================================================
   FILE DOWNLOAD
=========================================================== */

async function apiDownload(endpoint) {

  const token = getToken();

  const headers = {};

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    headers,
  });

  if (response.status === 401) {
    throw new Error("UNAUTHORIZED");
  }

  if (!response.ok) {

    throw new Error(`Download failed (${response.status})`);

  }

  return response.blob();
}

/* ===========================================================
   API ENDPOINTS
=========================================================== */

const API = {

  /* ======================
     AUTH
  ====================== */

  auth: {

    login: (data) =>
      apiCall("/auth/login", {
        method: "POST",
        body: data,
      }),

    registerMerchant: (data) =>
      apiCall("/auth/register/merchant", {
        method: "POST",
        body: data,
      }),

    registerCustomer: (data) =>
      apiCall("/auth/register/customer", {
        method: "POST",
        body: data,
      }),

    logout: () => {
      if (window.Auth && window.Auth.logout) {
          window.Auth.logout();
      } else {
          clearAuth();
          window.location.href = "../index.html";
      }
    },

    getMe: () => apiCall("/auth/me")

  },

  /* ======================
     MERCHANT
  ====================== */
  merchant: {
    getProfile: () => apiCall("/api/merchant/profile"),
    updateProfile: (data) =>
      apiCall("/api/merchant/profile", {
        method: "PUT",
        body: data,
      }),
    getProducts: () => apiCall("/api/merchant/products"),
    createProduct: (data) =>
      apiCall("/api/merchant/products", {
        method: "POST",
        body: data,
      }),
    deleteProduct: (id) =>
      apiCall(`/api/merchant/products/${id}`, {
        method: "DELETE",
      }),
    getInvoices: () => apiCall("/merchant/invoices"),
    createInvoice: (data) =>
      apiCall("/merchant/invoices", {
        method: "POST",
        body: data,
      }),
    downloadInvoicePdf: (id) => apiDownload(`/invoice/${id}/pdf`),
    getPaymentMethods: () => apiCall("/api/merchant/reports/payment-methods"),
  },

  /* ======================
     CUSTOMER
  ====================== */

  customer: {
    getProfile: () => apiCall("/api/customer/profile"),
    getInvoices: () => apiCall("/api/customer/invoices"),
    getPendingInvoices: () => apiCall("/api/customer/invoices/pending"),
  },

  /* ======================
     INVOICE
  ====================== */

  invoice: {
    getPreview: (id) =>
      apiCall(`/api/invoices/${id}/preview`),
    getPublic: (invoiceNumber, token) =>
      apiCall(`/public/invoices/${invoiceNumber}?token=${token}`),
  },

  /* ======================
     PAYMENT
  ====================== */

  payment: {
    createOrder: (invoiceId) =>
      apiCall(`/api/payments/create-order/${invoiceId}`, {
        method: "POST",
      }),
    payWithFace: () => {
      throw new Error("❌ payWithFace SHOULD NOT BE CALLED. Use direct fetch in pay-invoice.js instead.");
    },
    verifyRazorpay: (data) =>
      apiCall("/api/payments/verify", {
        method: "POST",
        body: data,
      }),
    refund: (invoiceId) =>
      apiCall(`/api/refund/${invoiceId}`, {
        method: "POST",
      }),
    requestRefund: (invoiceId) =>
      apiCall(`/api/refund/request/${invoiceId}`, {
        method: "POST"
      }),
    approveRefund: (invoiceId) =>
      apiCall(`/api/payments/refund/${invoiceId}`, {
        method: "POST"
      }),
    rejectRefund: (invoiceId) =>
      apiCall(`/api/payments/refund/reject/${invoiceId}`, {
        method: "POST"
      }),
    retryPayment: (invoiceId) =>
      apiCall(`/api/payments/retry/${invoiceId}`, {
        method: "POST"
      }),
  },

  /* ======================
     WALLET / FINANCIALS
  ====================== */

  wallet: {
    getWallet: () => apiCall("/api/merchant/wallet"),
    getBalanceSheet: () => apiCall("/api/merchant/reports/balance-sheet"),
    getTransactions: (params) => {
      let query = "";
      if (params) {
        const cleanParams = Object.fromEntries(
          Object.entries(params).filter(([_, v]) => v !== undefined && v !== "")
        );
        if (Object.keys(cleanParams).length > 0) {
          query = "?" + new URLSearchParams(cleanParams).toString();
        }
      }
      return apiCall(`/transactions${query}`);
    },
    exportTransactions: () => apiDownload("/api/merchant/reports/export"),
    exportBalanceSheet: () => apiDownload("/api/merchant/reports/balance-sheet/export"),
    sendReportEmail: (data) =>
      apiCall("/api/merchant/reports/email", {
        method: "POST",
        body: data,
      }),
  },

  /* ======================
     CHATBOT
  ====================== */

  chatbot: {

    ask: (data) => 
      apiCall("/api/chatbot/ask", {
        method: "POST",
        body: data
      })

  },

  /* ======================
     ADMIN
  ====================== */

  admin: {

    getStats: () => apiCall("/api/admin/stats"),
    
    getRevenue: () => apiCall("/api/admin/revenue"),

    getDashboard: () => API.admin.getStats(),

    getMerchants: () => apiCall("/api/admin/merchants"),

    getCustomers: () => apiCall("/api/admin/customers"),

    getTransactions: () => apiCall("/api/admin/transactions"),

    approveMerchant: (id) => apiCall(`/api/admin/merchant/${id}/approve`, { method: "PUT" }),

    suspendMerchant: (id) => apiCall(`/api/admin/merchant/${id}/suspend`, { method: "PUT" }),

    getMerchantDetails: (id) => apiCall(`/api/admin/merchants/${id}`)

  },

  /* ======================
     NOTIFICATIONS
  ====================== */

  notifications: {
    get: () => apiCall("/api/notifications"),
    markRead: (id) => apiCall(`/api/notifications/${id}/read`, { method: "POST" })
  },

    /* ======================
       USER
    ====================== */
    user: {
      uploadProfilePhoto: (formData) =>
        apiCall("/api/user/profile-photo", {
          method: "POST",
          body: formData,
          // Note: Browser sets Content-Type to multipart/form-data with boundary when body is FormData
          headers: {} 
        })
    }
  };

/* ===========================================================
   AUTH HELPERS
=========================================================== */

function saveAuthResponse(response) {

  if (!response) return;

  const token = response.accessToken || response.token;

  if (token) setToken(token);

  if (response.role) {
    setRole(response.role.toLowerCase());
  }

}

/* ===========================================================
   TOAST
=========================================================== */

function showToast(message, type = "info") {

  console.log(`[${type}]`, message);

  // In a real production app, use a nice UI toast.
  // For now, keep it simple but functional.
  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.innerText = message;
  toast.style.cssText = "position:fixed; bottom:20px; right:20px; padding:12px 24px; border-radius:8px; color:#fff; z-index:9999; font-weight:600; box-shadow:0 4px 12px rgba(0,0,0,0.15);";
  
  if (type === "success") toast.style.backgroundColor = "#34a853";
  else if (type === "error") toast.style.backgroundColor = "#ea4335";
  else toast.style.backgroundColor = "#4285f4";

  document.body.appendChild(toast);
  
  setTimeout(() => {
    toast.style.opacity = "0";
    toast.style.transition = "opacity 0.5s";
    setTimeout(() => toast.remove(), 500);
  }, 3000);

}

/* ===========================================================
   GLOBAL EXPORT
=========================================================== */

window.API = API;

window.saveAuthResponse = saveAuthResponse;

window.getToken = getToken;
window.getRole = getRole;

window.clearAuth = clearAuth;

window.showToast = showToast;