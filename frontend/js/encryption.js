(function () {
    "use strict";

    const ALGORITHM = "AES-256-GCM";
    const KDF = "PBKDF2-SHA-256";
    const ITERATIONS = 600000;
    const SALT_LENGTH_BYTES = 16;
    const IV_LENGTH_BYTES = 12;
    const AUTHENTICATION_TAG_LENGTH_BITS = 128;
    const MIN_SECRET_LENGTH = 12;

    function requireWebCrypto() {
        if (!window.crypto || !window.crypto.subtle
                || typeof window.crypto.getRandomValues !== "function") {
            throw new Error("Web Crypto is unavailable. Open this page in a secure browser context.");
        }
    }

    function validateEncryptionSecret(encryptionSecret) {
        if (typeof encryptionSecret !== "string"
                || Array.from(encryptionSecret).length < MIN_SECRET_LENGTH) {
            throw new TypeError("Use a separate file-encryption secret of at least 12 characters.");
        }
    }

    function toBytes(value) {
        if (value instanceof Uint8Array) {
            return value;
        }
        if (value instanceof ArrayBuffer) {
            return new Uint8Array(value);
        }
        if (ArrayBuffer.isView(value)) {
            return new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
        }
        throw new TypeError("Data must be a Uint8Array, ArrayBuffer, or typed-array view.");
    }

    async function readData(data) {
        if (typeof Blob !== "undefined" && data instanceof Blob) {
            return new Uint8Array(await data.arrayBuffer());
        }
        return toBytes(data);
    }

    function encodeBytes(value) {
        const bytes = toBytes(value);
        let binary = "";
        const chunkSize = 0x8000;
        for (let offset = 0; offset < bytes.length; offset += chunkSize) {
            binary += String.fromCharCode.apply(
                null,
                bytes.subarray(offset, Math.min(offset + chunkSize, bytes.length)));
        }
        return window.btoa(binary)
            .replace(/\+/g, "-")
            .replace(/\//g, "_")
            .replace(/=+$/g, "");
    }

    function decodeBytes(encoded) {
        if (typeof encoded !== "string"
                || !/^[A-Za-z0-9_-]*$/.test(encoded)
                || encoded.length % 4 === 1) {
            throw new TypeError("Encoded binary data must be valid unpadded base64url.");
        }
        const base64 = encoded.replace(/-/g, "+").replace(/_/g, "/")
            + "=".repeat((4 - encoded.length % 4) % 4);
        let binary;
        try {
            binary = window.atob(base64);
        } catch (error) {
            throw new TypeError("Encoded binary data is invalid.");
        }
        const bytes = new Uint8Array(binary.length);
        for (let index = 0; index < binary.length; index += 1) {
            bytes[index] = binary.charCodeAt(index);
        }
        return bytes;
    }

    async function deriveKey(encryptionSecret, salt) {
        requireWebCrypto();
        validateEncryptionSecret(encryptionSecret);
        const saltBytes = toBytes(salt);
        if (saltBytes.length !== SALT_LENGTH_BYTES) {
            throw new TypeError("PBKDF2 salt must be 16 bytes.");
        }

        const passwordMaterial = await window.crypto.subtle.importKey(
            "raw",
            new TextEncoder().encode(encryptionSecret),
            "PBKDF2",
            false,
            ["deriveKey"]);
        return window.crypto.subtle.deriveKey(
            {
                name: "PBKDF2",
                salt: saltBytes,
                iterations: ITERATIONS,
                hash: "SHA-256"
            },
            passwordMaterial,
            { name: "AES-GCM", length: 256 },
            false,
            ["encrypt", "decrypt"]);
    }

    async function encrypt(data, encryptionSecret) {
        requireWebCrypto();
        validateEncryptionSecret(encryptionSecret);
        const plaintext = await readData(data);
        const salt = window.crypto.getRandomValues(new Uint8Array(SALT_LENGTH_BYTES));
        const iv = window.crypto.getRandomValues(new Uint8Array(IV_LENGTH_BYTES));
        const key = await deriveKey(encryptionSecret, salt);
        const ciphertext = await window.crypto.subtle.encrypt(
            {
                name: "AES-GCM",
                iv: iv,
                tagLength: AUTHENTICATION_TAG_LENGTH_BITS
            },
            key,
            plaintext);

        return Object.freeze({
            algorithm: ALGORITHM,
            kdf: KDF,
            iterations: ITERATIONS,
            salt: encodeBytes(salt),
            iv: encodeBytes(iv),
            ciphertext: new Uint8Array(ciphertext)
        });
    }

    async function decrypt(ciphertext, encryptionSecret, metadata) {
        requireWebCrypto();
        validateEncryptionSecret(encryptionSecret);
        if (!metadata || metadata.algorithm !== ALGORITHM
                || metadata.kdf !== KDF || metadata.iterations !== ITERATIONS) {
            throw new TypeError("Unsupported encryption metadata.");
        }

        const salt = typeof metadata.salt === "string"
            ? decodeBytes(metadata.salt)
            : toBytes(metadata.salt);
        const iv = typeof metadata.iv === "string"
            ? decodeBytes(metadata.iv)
            : toBytes(metadata.iv);
        if (salt.length !== SALT_LENGTH_BYTES || iv.length !== IV_LENGTH_BYTES) {
            throw new TypeError("Encryption metadata contains an invalid salt or IV.");
        }

        const encryptedBytes = typeof ciphertext === "string"
            ? decodeBytes(ciphertext)
            : toBytes(ciphertext);
        const key = await deriveKey(encryptionSecret, salt);
        const plaintext = await window.crypto.subtle.decrypt(
            {
                name: "AES-GCM",
                iv: iv,
                tagLength: AUTHENTICATION_TAG_LENGTH_BITS
            },
            key,
            encryptedBytes);
        return new Uint8Array(plaintext);
    }

    window.SecureFileEncryption = Object.freeze({
        algorithm: ALGORITHM,
        kdf: KDF,
        iterations: ITERATIONS,
        deriveKey: deriveKey,
        encrypt: encrypt,
        decrypt: decrypt,
        encodeBytes: encodeBytes,
        decodeBytes: decodeBytes
    });
}());
