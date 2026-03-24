/**
 * BillMe Global I18N Engine
 * Handles dynamic JSON loading, language persistence, and UI translation.
 */

window.translations = null;

/**
 * Determines the base path for i18n files.
 * Since pages are in / (root), /src/, and /dashboard/, we need to handle paths carefully.
 */
function getI18nPath() {
    const path = window.location.pathname;
    if (path.includes('/src/') || path.includes('/dashboard/')) {
        return '../src/i18n/';
    }
    return 'src/i18n/';
}

/**
 * Fetches the translation JSON for the given language.
 */
async function loadTranslations(lang) {
    const basePath = getI18nPath();
    try {
        const response = await fetch(`${basePath}${lang}.json`);
        if (!response.ok) throw new Error(`Failed to load ${lang}.json`);
        window.translations = await response.json();
        console.log(`Translations loaded: ${lang}`);
        return true;
    } catch (error) {
        console.error("I18N Error:", error);
        return false;
    }
}

/**
 * Updates the UI with translated strings.
 */
function applyLanguage(lang) {
    if (!window.translations) {
        console.warn("Translations not loaded yet.");
        return;
    }

    // Update HTML lang attribute
    document.documentElement.setAttribute('lang', lang);

    // Update all elements with data-i18n
    const elements = document.querySelectorAll('[data-i18n]');
    elements.forEach(el => {
        const key = el.getAttribute('data-i18n');
        let translation = window.translations[key];

        if (translation) {
            // Handle placeholders if the element is an input or textarea
            if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
                el.placeholder = translation;
            } else {
                // Preserve icons (fa-* classes) if they exist
                const icon = el.querySelector('i.fas, i.fab, i.far');
                if (icon) {
                    el.innerHTML = '';
                    el.appendChild(icon);
                    el.appendChild(document.createTextNode(' ' + translation));
                } else {
                    el.innerText = translation;
                }
            }
        }
    });

    // Update Language Switcher Display (e.g., "EN", "HI", "MR")
    const langLabel = document.getElementById('current-lang');
    if (langLabel) {
        langLabel.innerText = lang.toUpperCase();
    }
}

/**
 * Sets the language, persists it, and updates the UI.
 */
window.setLanguage = async function (lang) {
    localStorage.setItem('billme_lang', lang);
    const success = await loadTranslations(lang);
    if (success) {
        applyLanguage(lang);
    }

    // Auto-close dropdown if it exists
    const menu = document.getElementById('lang-menu');
    if (menu) {
        menu.classList.remove('active');
    }
};

/**
 * Toggles the language dropdown menu.
 */
window.toggleLangDropdown = function () {
    const menu = document.getElementById("lang-menu");
    if (!menu) return;

    menu.classList.toggle("active");
};

// Global click-out to close dropdown
document.addEventListener("click", (e) => {
    const switcher = document.querySelector(".language-switcher");
    const container = document.querySelector(".language-dropdown-container");
    const menu = document.getElementById("lang-menu");

    if (!menu || !container) return;

    if (!container.contains(e.target)) {
        menu.classList.remove("active");
    }
});

/**
 * Initializes translations on page load.
 */
document.addEventListener('DOMContentLoaded', async () => {
    const savedLang = localStorage.getItem('billme_lang') || 'en';
    const success = await loadTranslations(savedLang);
    if (success) {
        applyLanguage(savedLang);
    }
});