(function () {
    "use strict";

    const result = document.querySelector("[data-test-result]");
    const details = document.querySelector("[data-test-details]");
    const testSecret = "Browser-only-test-secret-2026!";
    const fixture = new TextEncoder().encode(
        "ciphertext self-test fixture \u0000 \u00ff");

    runSelfTest().catch(function () {
        result.textContent = "Self-test failed. Check browser Web Crypto support.";
        result.parentElement.dataset.kind = "error";
    });

    async function runSelfTest() {
        const first = await SecureFileEncryption.encrypt(fixture, testSecret);
        const second = await SecureFileEncryption.encrypt(fixture, testSecret);
        const recovered = await SecureFileEncryption.decrypt(
            first.ciphertext,
            testSecret,
            first);
        assert(equalBytes(fixture, recovered), "Encrypt/decrypt round-trip");
        const blobEncrypted = await SecureFileEncryption.encrypt(
            new Blob([fixture], { type: "application/octet-stream" }),
            testSecret);
        const blobRecovered = await SecureFileEncryption.decrypt(
            blobEncrypted.ciphertext,
            testSecret,
            blobEncrypted);
        assert(equalBytes(fixture, blobRecovered), "Blob encrypt/decrypt round-trip");
        assert(first.salt !== second.salt, "Fresh random salt per encryption");
        assert(first.iv !== second.iv, "Fresh random IV per encryption");
        assert(decodeLength(first.salt) === 16, "Salt length is 16 bytes");
        assert(decodeLength(first.iv) === 12, "AES-GCM IV length is 12 bytes");

        const encodedCiphertext = SecureFileEncryption.encodeBytes(first.ciphertext);
        assert(
            equalBytes(
                first.ciphertext,
                SecureFileEncryption.decodeBytes(encodedCiphertext)),
            "Binary encoding round-trip");

        const tampered = first.ciphertext.slice();
        tampered[0] ^= 1;
        let tamperRejected = false;
        try {
            await SecureFileEncryption.decrypt(tampered, testSecret, first);
        } catch (error) {
            tamperRejected = true;
        }
        assert(tamperRejected, "Modified ciphertext is rejected");

        const tamperedTag = first.ciphertext.slice();
        tamperedTag[tamperedTag.length - 1] ^= 1;
        let tagRejected = false;
        try {
            await SecureFileEncryption.decrypt(tamperedTag, testSecret, first);
        } catch (error) {
            tagRejected = true;
        }
        assert(tagRejected, "Modified authentication tag is rejected");

        let wrongSecretRejected = false;
        try {
            await SecureFileEncryption.decrypt(
                first.ciphertext,
                "different-file-secret-2026!",
                first);
        } catch (error) {
            wrongSecretRejected = true;
        }
        assert(wrongSecretRejected, "Incorrect encryption secret is rejected");

        addResult("AES-256-GCM encrypt/decrypt round-trip passed");
        addResult("File/Blob encrypt/decrypt round-trip passed");
        addResult("Independent cryptographic salts and IVs passed");
        addResult("Binary encoding/decoding round-trip passed");
        addResult("AES-GCM tamper detection passed");
        addResult("AES-GCM authentication-tag tampering is rejected");
        addResult("Incorrect encryption secret is rejected");
        result.textContent = "All browser-only encryption tests passed.";
        result.parentElement.dataset.kind = "success";
    }

    function decodeLength(encoded) {
        return SecureFileEncryption.decodeBytes(encoded).length;
    }

    function equalBytes(left, right) {
        if (left.length !== right.length) {
            return false;
        }
        let difference = 0;
        for (let index = 0; index < left.length; index += 1) {
            difference |= left[index] ^ right[index];
        }
        return difference === 0;
    }

    function assert(condition, label) {
        if (!condition) {
            throw new Error(label + " failed");
        }
    }

    function addResult(text) {
        const item = document.createElement("li");
        item.textContent = text;
        details.appendChild(item);
    }
}());
