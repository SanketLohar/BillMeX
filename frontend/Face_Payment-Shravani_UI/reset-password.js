document.addEventListener('DOMContentLoaded', () => {
    // 1. Extract Token
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');

    const form = document.getElementById('resetForm');
    const newPasswordInput = document.getElementById('newPassword');
    const confirmPasswordInput = document.getElementById('confirmPassword');
    const newPasswordError = document.getElementById('newPasswordError');
    const confirmPasswordError = document.getElementById('confirmPasswordError');
    const submitBtn = document.getElementById('submitBtn');
    const btnText = document.getElementById('btnText');
    const btnLoader = document.getElementById('btnLoader');
    const alertBox = document.getElementById('alertBox');
    const togglePassword1 = document.getElementById('togglePassword1');
    const togglePassword2 = document.getElementById('togglePassword2');

    // Check if token exists
    if (!token) {
        form.style.display = 'none';
        showAlert('Invalid reset link. Token missing.', 'alert-error');
        document.getElementById('formSubtitle').style.display = 'none';
        return;
    }

    // Toggle Password Visibility
    function setupToggle(icon, input) {
        icon.addEventListener('click', () => {
            const type = input.getAttribute('type') === 'password' ? 'text' : 'password';
            input.setAttribute('type', type);
            // Toggle icon
            icon.classList.toggle('fa-eye');
            icon.classList.toggle('fa-eye-slash');
        });
    }

    setupToggle(togglePassword1, newPasswordInput);
    setupToggle(togglePassword2, confirmPasswordInput);

    // Show Alert Box
    function showAlert(message, typeClass) {
        alertBox.textContent = message;
        alertBox.className = `alert ${typeClass}`;
        alertBox.style.display = 'block';
    }

    // Hide Errors
    function hideErrors() {
        newPasswordError.style.display = 'none';
        confirmPasswordError.style.display = 'none';
        newPasswordInput.style.borderColor = 'var(--medium-gray)';
        confirmPasswordInput.style.borderColor = 'var(--medium-gray)';
    }

    // Handle Form Submit
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideErrors();
        alertBox.style.display = 'none';

        const newPassword = newPasswordInput.value.trim();
        const confirmPassword = confirmPasswordInput.value.trim();

        // Validation
        let isValid = true;
        if (!newPassword) {
            newPasswordError.style.display = 'block';
            newPasswordInput.style.borderColor = 'var(--error)';
            isValid = false;
        }

        if (newPassword !== confirmPassword) {
            confirmPasswordError.style.display = 'block';
            confirmPasswordInput.style.borderColor = 'var(--error)';
            isValid = false;
        }

        if (!isValid) return;

        // Button Loading State
        submitBtn.disabled = true;
        btnText.style.display = 'none';
        btnLoader.style.display = 'block';

        // API Call
        try {
            const response = await fetch('http://localhost:8080/auth/reset-password', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    token: token,
                    newPassword: newPassword
                })
            });

            if (response.ok) {
                // Success
                form.style.display = 'none';
                document.getElementById('formSubtitle').style.display = 'none';
                showAlert('Password reset successful. Redirecting to login...', 'alert-success');

                setTimeout(() => {
                    window.location.href = 'index.html';
                }, 2000);
            } else {
                // Error (Token invalid, expired, or already used)
                let errorMsg = 'Failed to reset password.';
                try {
                    const errorResponse = await response.json();
                    errorMsg = errorResponse.message || errorMsg;
                } catch (e) {
                    // Try to read text
                    const errorText = await response.text();
                    if (errorText) {
                        try {
                            const parsed = JSON.parse(errorText);
                            errorMsg = parsed.message || errorText;
                        } catch (err) {
                            errorMsg = errorText;
                        }
                    }
                }
                showAlert(errorMsg, 'alert-error');
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
