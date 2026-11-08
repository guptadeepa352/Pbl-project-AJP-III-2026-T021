(function () {
    "use strict";

    const message = document.querySelector("[data-message]");
    const profileEmail = document.querySelector("[data-profile-email]");
    const headerEmail = document.querySelector("[data-account-email]");
    const userName = document.querySelector("[data-user-name]");
    const logoutButton = document.querySelector("[data-logout]");

    logoutButton.addEventListener("click", function () {
        const encryptionSecret = document.querySelector("[data-encryption-secret]");
        const fileInput = document.querySelector("[data-file-input]");
        if (encryptionSecret) {
            encryptionSecret.value = "";
        }
        if (fileInput) {
            fileInput.value = "";
        }
        Api.clearToken();
        window.location.replace("login.html");
    });

    if (!Api.getToken()) {
        window.location.replace("login.html");
        return;
    }

    Api.getCurrentUser()
        .then(function (user) {
            if (!user || typeof user.email !== "string") {
                throw new Api.ApiError("The API returned an invalid account response.", 502);
            }
            profileEmail.textContent = user.email;
            headerEmail.textContent = user.email;
            userName.textContent = user.email.split("@")[0];
            document.dispatchEvent(new Event("securecloud:account-ready"));
        })
        .catch(function (error) {
            if (error.status !== 401) {
                showError(error.message || "Could not load your account. Please try again.");
                profileEmail.textContent = "Unavailable";
                userName.textContent = "there";
            }
        });

    function showError(text) {
        message.textContent = text;
        message.dataset.kind = "error";
        message.hidden = false;
    }
}());
