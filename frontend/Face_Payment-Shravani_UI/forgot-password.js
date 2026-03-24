document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('forgotForm');
    const emailInput = document.getElementById('email');
    const emailError = document.getElementById('emailError');
    const submitBtn = document.getElementById('submitBtn');
    const btnText = document.getElementById('btnText');
    const btnLoader = document.getElementById('btnLoader');
    const alertBox = document.getElementById('alertBox');
    const formSubtitle = document.getElementById('formSubtitle');

    function showAlert(message, typeClass) {
        alertBox.innerHTML = message;
        alertBox.className = `alert ${typeClass}`;
        alertBox.style.display = 'block';
    }

    function hideErrors() {
        emailError.style.display = 'none';
        emailInput.style.borderColor = 'var(--medium-gray)';
    }

    function validateEmail(email) {
        const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return re.test(String(email).toLowerCase());
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideErrors();
        alertBox.style.display = 'none';

        const email = emailInput.value.trim();

        // Validation
        if (!email || !validateEmail(email)) {
            emailError.style.display = 'block';
            emailInput.style.borderColor = 'var(--error)';
            return;
        }

        // Button Loading State
        submitBtn.disabled = true;
        btnText.style.display = 'none';
        btnLoader.style.display = 'block';

        // API Call
        try {
            const response = await fetch('http://localhost:8080/auth/forgot-password', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    email: email
                })
            });

            if (response.ok) {
                // Success
                form.style.display = 'none';
                formSubtitle.style.display = 'none';
                showAlert('<strong>If an account exists, a password reset email has been sent.</strong><br><br>Check your email for the reset link.', 'alert-success');
            } else {
                // Error 
                showAlert('Failed to process request. Please try again later.', 'alert-error');
            }
        } catch (error) {
            showAlert('A network error occurred. Please make sure the server is running.', 'alert-error');
        } finally {
            submitBtn.disabled = false;
            btnText.style.display = 'inline';
            btnLoader.style.display = 'none';
        }
    });

});
