// ============================================================
// BillMe — Global Chatbot (Single Source)
// ============================================================

function toggleChat() {
    const chat = document.getElementById("chatWindow");
    if (chat) chat.classList.toggle("active");
}

async function sendMessage() {
    const input = document.getElementById("chatInput");
    const content = document.getElementById("chatContent");

    if (!input || !content) return;

    const text = input.value.trim();
    if (!text) return;

    // USER MESSAGE
    const userMsg = document.createElement("div");
    userMsg.style.cssText = `
        background:#fff;
        padding:12px 16px;
        border-radius:15px;
        border-bottom-right-radius:2px;
        margin-bottom:15px;
        font-size:14px;
        color:#333;
        max-width:85%;
        align-self:flex-end;
        box-shadow:0 2px 5px rgba(0,0,0,0.05);
        margin-left:auto;
    `;
    userMsg.innerText = text;
    content.appendChild(userMsg);

    input.value = "";
    content.scrollTop = content.scrollHeight;

    // BOT LOADING
    const botMsg = document.createElement("div");
    botMsg.style.cssText = `
        background:#e3f2fd;
        padding:12px 16px;
        border-radius:15px;
        border-bottom-left-radius:2px;
        margin-bottom:15px;
        font-size:14px;
        color:#1565c0;
        max-width:85%;
    `;
    botMsg.innerHTML = '<i class="fas fa-spinner fa-spin"></i> thinking...';
    content.appendChild(botMsg);

    content.scrollTop = content.scrollHeight;

    try {
        if (!window.API || !window.API.chatbot) {
            throw new Error("API not initialized");
        }

        const response = await window.API.chatbot.ask({
            question: text
        });

        botMsg.innerText =
            response?.answer ||
            "I couldn't understand. Please try again.";

    } catch (err) {
        console.error("Chatbot Error:", err);

        if (err.message.includes("Backend not reachable")) {
            botMsg.innerText = "Server unreachable. Try again.";
        } else {
            botMsg.innerText = "Something went wrong.";
        }
    }

    content.scrollTop = content.scrollHeight;
}

// Expose functions globally for HTML onclick handlers
window.toggleChat = toggleChat;
window.sendMessage = sendMessage;