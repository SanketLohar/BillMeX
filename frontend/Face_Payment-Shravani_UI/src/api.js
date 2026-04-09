// ============================================================
// BillMe — Centralized API Client
// ============================================================

const API_BASE_URL = (() => {
  const host = window.location.hostname;
  if (host === "localhost" || host === "127.0.0.1") {
    return "http://localhost:8080";
  }
  return "https://billmex-production.up.railway.app";
})();
window.API_BASE_URL = API_BASE_URL;
console.log("🚀 [BillMeX] [API] Using base URL: " + API_BASE_URL);





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
  const isPublicEndpoint = endpoint.startsWith("/auth/login") ||
    endpoint.startsWith("/auth/register") ||
    endpoint.startsWith("/public/") ||
    endpoint.startsWith("/api/chatbot");

  if (!token && !isPublicEndpoint) {
    console.warn("[API] CALL BLOCKED (NO TOKEN):", endpoint);
    throw new Error("UNAUTHORIZED");
  }

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
    console.log("[API] AUTH ATTACHED:", endpoint);
  } else {
    console.warn("[API] NO TOKEN FOUND:", endpoint);
  }

  let response;

  try {

    response = await fetch(url, {
      ...options,
      headers,
    });

  } catch (err) {
    console.error("[API] FETCH FAILED:", endpoint, err);
    throw new Error("Backend not reachable. Please check your connection.");

  }

  /* ======================
     TOKEN EXPIRED
  ====================== */

  if (response.status === 401) {
    console.warn("[API] Session expired or unauthorized (401)");
    if (window.Auth && window.Auth.logout) {
      window.Auth.logout();
    } else {
      clearAuth();
      let rootPath = window.location.pathname;
      if (rootPath.includes('/dashboard/')) rootPath = rootPath.substring(0, rootPath.indexOf('/dashboard/') + 1);
      else if (rootPath.includes('/src/')) rootPath = rootPath.substring(0, rootPath.indexOf('/src/') + 1);
      else if (rootPath.includes('/payment/')) rootPath = rootPath.substring(0, rootPath.indexOf('/payment/') + 1);
      else rootPath = rootPath.substring(0, rootPath.lastIndexOf('/') + 1);
      window.location.href = window.location.origin + rootPath + 'index.html';
    }
    throw new Error("SESSION_EXPIRED");
  }

  /* ======================
     RESPONSE PARSING
  ====================== */

  let data;
  const rawText = await response.text(); 

  try {
    data = rawText ? JSON.parse(rawText) : null; 
  } catch (e) {
    data = rawText; 
  }

  if (!response.ok) {
    console.error("[API] ERR:", {
      url,
      status: response.status,
      response: data
    });

    // Extract clean error message from various Spring error shapes:
    let errorMessage;
    if (typeof data === "string") {
      errorMessage = data;
    } else if (data?.message) {
      errorMessage = data.message;
    } else if (data?.error) {
      // Translate backend-speak to user-friendly language
      const raw = data.error;
      if (raw === "Bad credentials" || raw === "Unauthorized" || raw === "Forbidden") {
        errorMessage = "Invalid email or password. Please try again.";
      } else {
        errorMessage = raw;
      }
    } else {
      errorMessage = `Request failed (${response.status})`;
    }

    throw new Error(errorMessage);
  }

  return data;
}

/* ===========================================================
   FILE DOWNLOAD
=========================================================== */

async function apiDownload(endpoint) {

  const token = getToken();

  const headers = {};

  if (!token) {
    console.warn("API CALL BLOCKED (NO TOKEN):", endpoint);
    throw new Error("UNAUTHORIZED");
  }

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    headers,
  });

  if (response.status === 401) {
    console.warn("Session expired or unauthorized (401)");
    if (window.Auth && window.Auth.logout) {
      window.Auth.logout();
    } else {
      clearAuth();
      let rootPath = window.location.pathname;
      if (rootPath.includes('/dashboard/')) rootPath = rootPath.substring(0, rootPath.indexOf('/dashboard/') + 1);
      else if (rootPath.includes('/src/')) rootPath = rootPath.substring(0, rootPath.indexOf('/src/') + 1);
      else if (rootPath.includes('/payment/')) rootPath = rootPath.substring(0, rootPath.indexOf('/payment/') + 1);
      else rootPath = rootPath.substring(0, rootPath.lastIndexOf('/') + 1);
      window.location.href = window.location.origin + rootPath + 'index.html';
    }
    throw new Error("UNAUTHORIZED");
  }

  if (!response.ok) {
    throw new Error(`Download failed (${response.status})`);
  }

  const blob = await response.blob();
  const contentDisposition = response.headers.get("Content-Disposition") || response.headers.get("content-disposition");
  const filename = getFilenameFromContentDisposition(contentDisposition);
  blob.downloadFilename = filename || null;
  blob.contentDisposition = contentDisposition || null;
  return blob;
}

function getFilenameFromContentDisposition(headerValue) {
  if (!headerValue) return null;

  // RFC 5987: filename*=UTF-8''encoded-name.ext
  const utf8Match = headerValue.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match && utf8Match[1]) {
    try {
      return decodeURIComponent(utf8Match[1].trim().replace(/["']/g, ""));
    } catch (_) {
      return utf8Match[1].trim().replace(/["']/g, "");
    }
  }

  // Basic: filename="name.ext" or filename=name.ext
  const basicMatch = headerValue.match(/filename=([^;]+)/i);
  if (!basicMatch || !basicMatch[1]) return null;
  return basicMatch[1].trim().replace(/["']/g, "");
}

function triggerBlobDownload(fileBlob, fallbackFilename) {
  if (!(fileBlob instanceof Blob)) {
    throw new Error("Invalid download payload");
  }
  const filename = fileBlob.downloadFilename || fallbackFilename || `download-${Date.now()}`;
  const url = window.URL.createObjectURL(fileBlob);
  const a = document.createElement("a");
  a.style.display = "none";
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  // Delayed revoke avoids browser races where download doesn't start.
  setTimeout(() => {
    window.URL.revokeObjectURL(url);
    a.remove();
  }, 1500);
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
        let rootPath = window.location.pathname;
        if (rootPath.includes('/dashboard/')) rootPath = rootPath.substring(0, rootPath.indexOf('/dashboard/') + 1);
        else if (rootPath.includes('/src/')) rootPath = rootPath.substring(0, rootPath.indexOf('/src/') + 1);
        else if (rootPath.includes('/payment/')) rootPath = rootPath.substring(0, rootPath.indexOf('/payment/') + 1);
        else rootPath = rootPath.substring(0, rootPath.lastIndexOf('/') + 1);
        window.location.href = window.location.origin + rootPath + 'index.html';
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
    getPaymentMethods: () => apiCall("/api/v1/merchant/reports/payment-methods"),
    
    // Bank Accounts API
    getBankAccounts: () => apiCall("/api/v1/merchant/bank-accounts"),
    addBankAccount: (data) => apiCall("/api/v1/merchant/bank-accounts", { method: "POST", body: data }),
    setDefaultBankAccount: (id) => apiCall(`/api/v1/merchant/bank-accounts/${id}/default`, { method: "PUT" }),
    deleteBankAccount: (id) => apiCall(`/api/v1/merchant/bank-accounts/${id}`, { method: "DELETE" }),
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
    requestRefund: (invoiceId, data) =>
      apiCall(`/api/refund/request/${invoiceId}`, {
        method: "POST",
        body: data
      }),
    approveRefund: (invoiceId) =>
      apiCall(`/api/refund/${invoiceId}`, {
        method: "POST"
      }),
    rejectRefund: (invoiceId) =>
      apiCall(`/api/refund/reject/${invoiceId}`, {
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
    getBalanceSheet: () => apiCall("/api/v1/merchant/reports/balance-sheet"),
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
    /**
     * Initiate a withdrawal.
     * @param {{ amount: number, bankAccountId?: number }} data
     * bankAccountId is optional — omit to use the merchant's default bank.
     */
    withdraw: (data) =>
      apiCall("/api/merchant/withdraw", { method: "POST", body: data }),
    exportTransactions: () => apiDownload("/api/merchant/reports/export"),
    exportBalanceSheet: () => apiDownload("/api/merchant/reports/balance-sheet/export"),
    getPnl: (params = {}) => {
      const cleanParams = {
        range: params.range || "monthly"
      };
      const query = "?" + new URLSearchParams(cleanParams).toString();
      console.log("[API FIX] getPnl:", query);
      return apiCall(`/api/v1/merchant/profit-loss${query}`);
    },

    exportPnl: (params = {}) => {
      const cleanParams = {
        format: params.format || "pdf",
        range: params.range || "monthly"
      };
      const query = new URLSearchParams(cleanParams).toString();
      console.log("[API FIX] exportPnl:", query);
      return apiDownload(`/api/v1/merchant/reports/pnl/export?${query}`);
    },

    getSummary: (params = {}) => {
      const cleanParams = {
        range: params.range || "monthly"
      };
      const query = "?" + new URLSearchParams(cleanParams).toString();
      console.log("[API FIX] getSummary:", query);
      return apiCall(`/api/v1/merchant/reports/summary${query}`);
    },

    exportSummary: (params = {}) => {
      const cleanParams = {
        format: params.format || "pdf",
        range: params.range || "monthly"
      };
      const query = new URLSearchParams(cleanParams).toString();
      console.log("[API FIX] exportSummary:", query);
      return apiDownload(`/api/v1/merchant/reports/summary/export?${query}`);
    },

    exportStatement: (params = {}) => {
      const cleanParams = {
        format: params.format || "pdf",
        range: params.range || "monthly"
      };
      const query = new URLSearchParams(cleanParams).toString();
      console.log("[API FIX] exportStatement:", query);
      return apiDownload(`/api/v1/merchant/reports/statement/export?${query}`);
    },

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
window.triggerBlobDownload = triggerBlobDownload;