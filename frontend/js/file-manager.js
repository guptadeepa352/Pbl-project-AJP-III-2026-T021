(function () {
    "use strict";

    const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    const FILE_NAME_MAX_LENGTH = 255;
    const MIME_TYPE_MAX_LENGTH = 127;
    const fileInput = document.querySelector("[data-file-input]");
    const secretInput = document.querySelector("[data-encryption-secret]");
    const uploadForm = document.querySelector("[data-upload-form]");
    const uploadMessage = document.querySelector("[data-upload-message]");
    const uploadButton = document.querySelector("[data-upload-submit]");
    const uploadLabel = document.querySelector("[data-upload-label]");
    const fileList = document.querySelector("[data-file-list]");
    const fileListMessage = document.querySelector("[data-file-list-message]");
    const refreshButton = document.querySelector("[data-refresh-files]");
    const refreshLabel = document.querySelector("[data-refresh-label]");
    const refreshSpinner = document.querySelector("[data-refresh-spinner]");
    const uploadSpinner = document.querySelector("[data-upload-spinner]");

    if (!uploadForm || !Api.getToken()) {
        return;
    }

    uploadForm.addEventListener("submit", uploadFile);
    refreshButton.addEventListener("click", loadFiles);
    fileList.addEventListener("click", handleFileAction);
    uploadButton.disabled = true;
    fileInput.disabled = true;
    secretInput.disabled = true;
    fileListMessage.textContent = "Verifying your account…";
    document.addEventListener("securecloud:account-ready", function () {
        uploadButton.disabled = false;
        fileInput.disabled = false;
        secretInput.disabled = false;
        loadFiles();
    }, { once: true });

    async function uploadFile(event) {
        event.preventDefault();
        clearUploadMessage();

        const file = fileInput.files && fileInput.files[0];
        let encryptionSecret = secretInput.value;
        const validationError = validateFile(file, encryptionSecret);
        if (validationError) {
            showUploadMessage(validationError, "error");
            return;
        }

        setUploading(true);
        try {
            const encrypted = await SecureFileEncryption.encrypt(file, encryptionSecret);
            encryptionSecret = "";
            secretInput.value = "";

            const response = await Api.uploadEncryptedFile({
                originalFilename: file.name,
                mimeType: file.type || "application/octet-stream",
                fileSize: file.size,
                encryptionAlgorithm: encrypted.algorithm,
                keyDerivationFunction: encrypted.kdf,
                keyDerivationIterations: encrypted.iterations,
                iv: encrypted.iv,
                salt: encrypted.salt,
                ciphertext: SecureFileEncryption.encodeBytes(encrypted.ciphertext)
            });
            uploadForm.reset();
            showUploadMessage(
                "File encrypted in your browser and uploaded successfully.",
                "success");
            await loadFiles();
            return response;
        } catch (error) {
            showUploadMessage(
                error && error.message ? error.message : "Encrypted upload failed.",
                "error");
        } finally {
            encryptionSecret = "";
            secretInput.value = "";
            setUploading(false);
        }
    }

    function validateFile(file, encryptionSecret) {
        if (!file) {
            return "Choose a file to upload.";
        }
        if (file.size > MAX_FILE_SIZE_BYTES) {
            return "Files must be 10 MiB or smaller.";
        }
        if (file.name.length === 0 || file.name.length > FILE_NAME_MAX_LENGTH
                || file.name !== file.name.trim()
                || file.name === "." || file.name === ".."
                || /[/\\\p{Cc}]/u.test(file.name)) {
            return "Filename must be a plain filename of at most 255 characters.";
        }
        const mimeType = file.type || "application/octet-stream";
        if (mimeType.length > MIME_TYPE_MAX_LENGTH
                || !/^[A-Za-z0-9!#$&^_.+-]+\/[A-Za-z0-9!#$&^_.+-]+$/.test(mimeType)) {
            return "The file has an invalid MIME type.";
        }
        if (typeof encryptionSecret !== "string"
                || Array.from(encryptionSecret).length < 12) {
            return "Enter a separate file-encryption secret of at least 12 characters.";
        }
        return "";
    }

    async function loadFiles() {
        fileList.replaceChildren();
        fileListMessage.textContent = "Loading your files…";
        fileListMessage.dataset.kind = "";
        fileListMessage.setAttribute("aria-busy", "true");
        refreshButton.disabled = true;
        refreshLabel.textContent = "Loading…";
        refreshSpinner.hidden = false;
        try {
            const files = await Api.listFiles();
            if (!Array.isArray(files)) {
                throw new Api.ApiError("The API returned an invalid file list.", 502);
            }
            if (files.length === 0) {
                fileListMessage.textContent = "No files uploaded yet.";
                return;
            }
            fileListMessage.textContent = "";
            files.forEach(addFileRow);
        } catch (error) {
            fileListMessage.textContent = error && error.message
                ? error.message
                : "Could not load your files.";
            fileListMessage.dataset.kind = "error";
        } finally {
            fileListMessage.removeAttribute("aria-busy");
            refreshButton.disabled = false;
            refreshLabel.textContent = "Refresh";
            refreshSpinner.hidden = true;
        }
    }

    function addFileRow(file) {
        const item = document.createElement("li");
        item.className = "file-list-item";

        const description = document.createElement("div");
        const filename = document.createElement("div");
        filename.className = "file-list-name";
        filename.textContent = file.originalFilename;
        const metadata = document.createElement("div");
        metadata.className = "file-list-meta";
        metadata.textContent = (file.mimeType || "Unknown type") + " · "
            + formatDate(file.createdAt);
        description.append(filename, metadata);

        const actions = document.createElement("div");
        actions.className = "file-list-actions";
        const size = document.createElement("span");
        size.className = "file-list-size";
        size.textContent = formatSize(file.fileSize);
        const download = document.createElement("button");
        download.className = "file-download-button";
        download.type = "button";
        download.dataset.downloadId = String(file.id);
        download.textContent = "Download";
        download.setAttribute("aria-label", "Download and decrypt " + file.originalFilename);
        const remove = document.createElement("button");
        remove.className = "file-delete-button";
        remove.type = "button";
        remove.dataset.deleteId = String(file.id);
        remove.textContent = "Delete";
        remove.setAttribute("aria-label", "Delete " + file.originalFilename);
        actions.append(size, download, remove);
        item.append(description, actions);
        fileList.appendChild(item);
    }

    async function handleFileAction(event) {
        const button = event.target.closest("[data-download-id], [data-delete-id]");
        if (!button || !fileList.contains(button)) {
            return;
        }
        if (button.hasAttribute("data-delete-id")) {
            const item = button.closest(".file-list-item");
            const filename = item.querySelector(".file-list-name").textContent;
            if (!window.confirm("Delete “" + filename + "” permanently? This cannot be undone.")) {
                return;
            }
            const previousLabel = button.textContent;
            button.disabled = true;
            button.textContent = "Deleting…";
            try {
                await Api.deleteFile(button.dataset.deleteId);
                showUploadMessage("File deleted successfully.", "success");
                await loadFiles();
            } catch (error) {
                showUploadMessage(
                    error && error.message ? error.message : "Could not delete this file.",
                    "error");
                button.disabled = false;
                button.textContent = previousLabel;
            }
            return;
        }
        let encryptionSecret = secretInput.value;
        if (Array.from(encryptionSecret).length < 12) {
            showUploadMessage(
                "Enter the file-encryption secret above before downloading.",
                "error");
            secretInput.focus();
            return;
        }

        button.disabled = true;
        const previousLabel = button.textContent;
        button.textContent = "Decrypting…";
        secretInput.disabled = true;
        try {
            const response = await Api.downloadEncryptedFile(button.dataset.downloadId);
            validateDownloadResponse(response);
            const ciphertext = SecureFileEncryption.decodeBytes(response.ciphertext);
            const plaintext = await SecureFileEncryption.decrypt(
                ciphertext,
                encryptionSecret,
                {
                    algorithm: response.encryptionAlgorithm,
                    kdf: response.keyDerivationFunction,
                    iterations: response.keyDerivationIterations,
                    iv: response.iv,
                    salt: response.salt
                });
            if (plaintext.length !== response.fileSize) {
                throw new Error("Decrypted file size did not match its metadata.");
            }
            downloadFile(response, plaintext);
            showUploadMessage("File decrypted and downloaded in your browser.", "success");
        } catch (error) {
            showUploadMessage(
                error && error.name === "OperationError"
                    ? "Could not decrypt this file. Check the file-encryption secret."
                    : error && error.message
                        ? error.message
                        : "File download or decryption failed.",
                "error");
        } finally {
            encryptionSecret = "";
            secretInput.value = "";
            secretInput.disabled = false;
            button.disabled = false;
            button.textContent = previousLabel;
        }
    }

    function validateDownloadResponse(response) {
        if (!response
                || typeof response.originalFilename !== "string"
                || typeof response.mimeType !== "string"
                || typeof response.ciphertext !== "string"
                || !Number.isSafeInteger(response.fileSize)
                || response.fileSize < 0
                || response.fileSize > MAX_FILE_SIZE_BYTES) {
            throw new Api.ApiError("The API returned invalid encrypted file data.", 502);
        }
    }

    function downloadFile(metadata, plaintext) {
        const blob = new Blob([plaintext], {
            type: metadata.mimeType || "application/octet-stream"
        });
        const objectUrl = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = objectUrl;
        link.download = metadata.originalFilename;
        link.hidden = true;
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.setTimeout(function () {
            URL.revokeObjectURL(objectUrl);
        }, 1000);
    }

    function formatSize(bytes) {
        if (!Number.isFinite(bytes) || bytes < 0) {
            return "Unknown size";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024).toFixed(1) + " KiB";
        }
        return (bytes / (1024 * 1024)).toFixed(1) + " MiB";
    }

    function formatDate(value) {
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? "Date unavailable" : date.toLocaleString();
    }

    function showUploadMessage(text, kind) {
        uploadMessage.textContent = text;
        uploadMessage.dataset.kind = kind;
        uploadMessage.hidden = false;
    }

    function clearUploadMessage() {
        uploadMessage.textContent = "";
        uploadMessage.hidden = true;
        delete uploadMessage.dataset.kind;
    }

    function setUploading(isUploading) {
        uploadButton.disabled = isUploading;
        fileInput.disabled = isUploading;
        secretInput.disabled = isUploading;
        uploadForm.setAttribute("aria-busy", String(isUploading));
        uploadSpinner.hidden = !isUploading;
        uploadLabel.textContent = isUploading ? "Encrypting and uploading…" : "Encrypt and upload";
    }
}());
