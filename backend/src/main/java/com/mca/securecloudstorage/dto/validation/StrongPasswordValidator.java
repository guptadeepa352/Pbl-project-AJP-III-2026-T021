package com.mca.securecloudstorage.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true;
        }

        if (password.codePointCount(0, password.length()) < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }

        boolean hasLowercase = false;
        boolean hasUppercase = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;

        for (int offset = 0; offset < password.length();) {
            int codePoint = password.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isISOControl(codePoint)) {
                return false;
            }
            hasLowercase |= Character.isLowerCase(codePoint);
            hasUppercase |= Character.isUpperCase(codePoint);
            hasDigit |= Character.isDigit(codePoint);
            hasSymbol |= !Character.isLetterOrDigit(codePoint)
                    && !Character.isWhitespace(codePoint);
        }

        return hasLowercase && hasUppercase && hasDigit && hasSymbol;
    }
}
