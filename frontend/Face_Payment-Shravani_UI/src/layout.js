/**
 * BillMe — Shared Layout Logic
 * Handles sidebar toggling, responsive overlays, and state-aware resize events.
 */

const Layout = {
    sidebar: null,
    overlay: null,
    toggleBtn: null,
    isMobile: false,

    init() {
        this.sidebar = document.querySelector('.sidebar');
        // Fallback for customer dashboard which might not have ID on sidebar
        if (!this.sidebar) this.sidebar = document.getElementById('sidebar');
        
        this.toggleBtn = document.getElementById('menuToggle');
        
        // Use existing overlay or create one
        this.ensureOverlay();
        
        if (this.toggleBtn) {
            // Remove any existing listeners to prevent duplicates if init is called twice
            const newBtn = this.toggleBtn.cloneNode(true);
            this.toggleBtn.parentNode.replaceChild(newBtn, this.toggleBtn);
            this.toggleBtn = newBtn;
            
            this.toggleBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.toggleSidebar();
            });
        }

        // Close sidebar when clicking overlay
        if (this.overlay) {
            this.overlay.addEventListener('click', () => this.closeSidebar());
        }

        // Close sidebar on navigation (data-section clicks)
        document.querySelectorAll('.nav-item').forEach(item => {
            item.addEventListener('click', () => {
                if (window.innerWidth <= 1024) {
                    this.closeSidebar();
                }
            });
        });

        // Initialize state
        this.handleResize();
        window.addEventListener('resize', () => this.handleResize());
        
        console.log("BillMe Layout System Initialized");
    },

    ensureOverlay() {
        this.overlay = document.querySelector('.sidebar-overlay') || document.getElementById('sidebarOverlay');
        if (!this.overlay) {
            this.overlay = document.createElement('div');
            this.overlay.className = 'sidebar-overlay';
            this.overlay.id = 'sidebarOverlay';
            document.body.appendChild(this.overlay);
        }
    },

    toggleSidebar() {
        const dashLayout = document.getElementById('dashLayout') || document.querySelector('.dash-layout');
        if (!dashLayout) return;

        if (this.isMobile) {
            // Mobile: Toggle Overlay logic
            if (this.sidebar.classList.contains('active')) {
                this.closeSidebar();
            } else {
                this.openSidebar();
            }
        } else {
            // Desktop: Toggle Grid/Collapse logic
            dashLayout.classList.toggle('sidebar-hidden');
            
            // Trigger chart resize after transition
            setTimeout(() => {
                window.dispatchEvent(new Event('resize'));
            }, 350);
        }
    },

    openSidebar() {
        if (this.sidebar) this.sidebar.classList.add('active');
        if (this.overlay) this.overlay.classList.add('active');
        document.body.style.overflow = 'hidden'; 
    },

    closeSidebar() {
        if (this.sidebar) this.sidebar.classList.remove('active');
        if (this.overlay) this.overlay.classList.remove('active');
        document.body.style.overflow = '';
        
        setTimeout(() => {
            window.dispatchEvent(new Event('resize'));
        }, 350);
    },

    handleResize() {
        this.isMobile = window.innerWidth <= 1024;
        const dashLayout = document.getElementById('dashLayout') || document.querySelector('.dash-layout');

        if (!this.isMobile) {
            // Reset mobile states when entering desktop view
            if (this.sidebar) this.sidebar.classList.remove('active');
            if (this.overlay) this.overlay.classList.remove('active');
            document.body.style.overflow = '';
        } else {
            // Ensure desktop-hidden state doesn't interfere with mobile view
            if (dashLayout) dashLayout.classList.remove('sidebar-hidden');
        }
    }
};

window.Layout = Layout;

document.addEventListener('DOMContentLoaded', () => {
    if (document.querySelector('.dash-layout')) {
        Layout.init();
    }
});
