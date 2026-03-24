// ============================================================
// BillMe — Notifications Logic
// ============================================================

document.addEventListener('DOMContentLoaded', () => {
    // Check if user is logged in
    const token = localStorage.getItem("billme_token");
    if (!token) return;

    fetchNotifications();
    
    // Poll for notifications every 30 seconds
    setInterval(fetchNotifications, 30000);
});

async function fetchNotifications() {
    if (!window.API || !window.API.notifications) return;

    try {
        const notifs = await window.API.notifications.get();
        const unreadCount = notifs.filter(n => !n.read).length;
        
        const badge = document.getElementById('notifBadge');
        if (badge) {
            if (unreadCount > 0) {
                badge.innerText = unreadCount;
                badge.style.display = 'inline-block';
            } else {
                badge.style.display = 'none';
            }
        }

        const list = document.getElementById('notifList');
        if (list) {
            if (notifs.length === 0) {
                list.innerHTML = '<div style="padding: 10px; text-align: center;">No new notifications.</div>';
            } else {
                list.innerHTML = notifs.map(n => `
                    <div style="padding: 10px; border-bottom: 1px solid #eee; background: ${n.read ? '#fff' : '#f8f9fa'}">
                        <div style="font-weight: ${n.read ? 'normal' : 'bold'}; margin-bottom: 4px;">
                            ${n.type === 'REFUND_REQUESTED' ? '<i class="fas fa-undo"></i>' : '<i class="fas fa-info-circle"></i>'}
                            ${n.message}
                        </div>
                        <div style="font-size: 11px; color: #888; display: flex; justify-content: space-between;">
                            <span>${new Date(n.createdAt).toLocaleString()}</span>
                            ${!n.read ? `<button onclick="markNotificationRead(${n.id}, event)" style="border:none;background:none;color:var(--primary);cursor:pointer;font-size:11px;">Mark Read</button>` : ''}
                        </div>
                    </div>
                `).join('');
            }
        }
    } catch (e) {
        console.error("Failed to fetch notifications:", e);
    }
}

window.toggleNotifications = function() {
    const dropdown = document.getElementById('notifDropdown');
    if (dropdown) {
        dropdown.classList.toggle('active');
    }
};

window.markNotificationRead = async function(id, event) {
    if (event) event.stopPropagation();
    try {
        await window.API.notifications.markRead(id);
        fetchNotifications();
    } catch (e) {
        console.error("Failed to mark notification as read:", e);
    }
};
