(function () {
    "use strict";

    const API_BASE_URL = (window.SECURE_CLOUD_API_URL || "http://localhost:8081")
        .replace(/\/+$/, "");
    const TOKEN_KEY = "secureCloudAccessToken";

    class ApiError extends Error {
        constructor(message, status, fieldErrors) {
            super(message);
            this.name = "ApiError";
            this.status = status;
            this.fieldErrors = fieldErrors || {};
        }
    }

    function getToken() {
        return sessionStorage.getItem(TOKEN_KEY);
    }

    function setToken(token) {
        sessionStorage.setItem(TOKEN_KEY, token);
    }

    function clearToken() {
        sessionStorage.removeItem(TOKEN_KEY);
    }

    async function request(path, options) {
        const requestOptions = options || {};
        const headers = new Headers(requestOptions.headers || {});
        const token = getToken();

        if (requestOptions.body !== undefined && !headers.has("Content-Type")) {
            headers.set("Content-Type", "application/json");
        }
        if (token) {
            headers.set("Authorization", "Bearer " + token);
        }

        let response;
        try {
            response = await fetch(API_BASE_URL + path, {
                ...requestOptions,
                headers: headers,
                credentials: "omit"
            });
        } catch (error) {
            throw new ApiError(
                "Could not reach the API. Check that Spring Boot is running and the frontend origin is allowed by CORS.",
                0
            );
        }

        const responseText = await response.text();
        let body = null;
        if (responseText) {
            try {
                body = JSON.parse(responseText);
            } catch (error) {
                body = null;
            }
        }

        if (response.status === 401 && path !== "/api/auth/login") {
            console.warn("API request was unauthorized; invalidating the session.", {
                method: requestOptions.method || "GET",
                path: path,
                status: response.status
            });
            clearToken();
            if (document.body.dataset.page === "dashboard") {
                window.location.replace("login.html?expired=1");
            }
        }

        if (!response.ok) {
            const message = body && typeof body.message === "string"
                ? body.message
                : "The request could not be completed.";
            throw new ApiError(message, response.status, body && body.fieldErrors);
        }

        return body;
    }

    window.Api = Object.freeze({
        ApiError: ApiError,
        getToken: getToken,
        setToken: setToken,
        clearToken: clearToken,
        request: request,
        register: function (payload) {
            return request("/api/auth/register", {
                method: "POST",
                body: JSON.stringify(payload)
            });
        },
        login: function (payload) {
            return request("/api/auth/login", {
                method: "POST",
                body: JSON.stringify(payload)
            });
        },
        getCurrentUser: function () {
            return request("/api/auth/me", { method: "GET" });
        },
        listFiles: function () {
            return request("/api/files", { method: "GET" });
        },
        uploadEncryptedFile: function (payload) {
            return request("/api/files/upload", {
                method: "POST",
                body: JSON.stringify(payload)
            });
        },
        downloadEncryptedFile: function (id) {
            return request("/api/files/" + encodeURIComponent(id) + "/download", {
                method: "GET"
            });
        },
        deleteFile: function (id) {
            return request("/api/files/" + encodeURIComponent(id), {
                method: "DELETE"
            });
        }
    });
}());
