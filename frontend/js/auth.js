(function () {
    "use strict";

    const page = document.body.dataset.page;
    const form = document.querySelector("[data-auth-form]");
    const message = document.querySelector("[data-message]");

    if (page === "home" && Api.getToken()) {
        window.location.replace("dashboard.html");
        return;
    }

    if (page !== "login" && page !== "register") {
        return;
    }

    if (Api.getToken()) {
        window.location.replace("dashboard.html");
        return;
    }

    if (page === "login") {
        const query = new URLSearchParams(window.location.search);
        if (query.get("registered") === "1") {
            showMessage("Account created. Sign in with your new credentials.", "success");
        } else if (query.get("expired") === "1") {
            showMessage("Your session has expired. Please sign in again.", "error");
        }
    }

    form.addEventListener("submit", async function (event) {
        event.preventDefault();
        clearMessage();
        clearFieldErrors();

        const formData = new FormData(form);
        const payload = {};
        for (const [key, value] of formData.entries()) {
            payload[key] = typeof value === "string" && key !== "password"
                ? value.trim()
                : value;
        }

        const validationMessage = validateForm(payload);
        if (validationMessage) {
            showMessage(validationMessage, "error");
            return;
        }

        const submitButton = form.querySelector("[data-submit]");
        const submitLabel = form.querySelector("[data-submit-label]");
        submitButton.disabled = true;
        submitLabel.textContent = page === "register" ? "Creating account…" : "Signing in…";

        try {
            if (page === "register") {
                await Api.register(payload);
                form.reset();
                window.location.assign("login.html?registered=1");
                return;
            }

            const response = await Api.login(payload);
            if (!response || typeof response.accessToken !== "string"
                    || response.tokenType !== "Bearer") {
                throw new Api.ApiError("The API returned an invalid login response.", 502);
            }

            Api.setToken(response.accessToken);
            window.location.assign("dashboard.html");
        } catch (error) {
            showMessage(formatError(error), "error");
            if (error instanceof Api.ApiError && error.fieldErrors) {
                markFieldErrors(error.fieldErrors);
            }
        } finally {
            submitButton.disabled = false;
            submitLabel.textContent = page === "register" ? "Create account" : "Sign in";
            form.elements.password.value = "";
        }
    });

    function validateForm(payload) {
        if (page === "register" && (!payload.name || payload.name.length > 100)) {
            return "Enter a name of 1 to 100 characters.";
        }
        if (!payload.email || payload.email.length > 254
                || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(payload.email)) {
            return "Enter a valid email address.";
        }
        if (typeof payload.password !== "string" || payload.password.length === 0) {
            return "Enter your password.";
        }
        if (page === "register") {
            const passwordBytes = new TextEncoder().encode(payload.password).length;
            if (payload.password.length < 12
                    || passwordBytes > 72
                    || !/[a-z]/.test(payload.password)
                    || !/[A-Z]/.test(payload.password)
                    || !/[0-9]/.test(payload.password)
                    || !/[^A-Za-z0-9\s]/.test(payload.password)) {
                return "Use at least 12 characters with uppercase, lowercase, a number, and a symbol. The UTF-8 length must be no more than 72 bytes.";
            }
        } else if (payload.password.length > 1024) {
            return "Password must be at most 1024 characters.";
        }
        return "";
    }

    function formatError(error) {
        if (error instanceof Api.ApiError && error.fieldErrors) {
            const details = Object.values(error.fieldErrors).filter(Boolean);
            if (details.length) {
                return details.join(" ");
            }
        }
        return error && error.message ? error.message : "Something went wrong. Please try again.";
    }

    function markFieldErrors(fieldErrors) {
        for (const fieldName of Object.keys(fieldErrors)) {
            const input = form.elements.namedItem(fieldName);
            if (input) {
                input.setAttribute("aria-invalid", "true");
            }
        }
    }

    function clearFieldErrors() {
        for (const input of form.querySelectorAll("[aria-invalid='true']")) {
            input.removeAttribute("aria-invalid");
        }
    }

    function showMessage(text, kind) {
        message.textContent = text;
        message.dataset.kind = kind === "error" ? "error" : "success";
        message.hidden = false;
    }

    function clearMessage() {
        message.textContent = "";
        message.hidden = true;
        delete message.dataset.kind;
    }
}());
