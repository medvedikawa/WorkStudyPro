package com.example.wspro;

import org.junit.Test;

import static org.junit.Assert.*;

public class ForgotPasswordFlowTest {

    @Test
    public void testPasswordEvaluationRules() {
        // Less than 8 chars
        assertNotNull(validatePasswordRule("short1!"));

        // Missing digit
        assertNotNull(validatePasswordRule("NoDigitsHere!"));

        // Missing letter
        assertNotNull(validatePasswordRule("12345678!@#"));

        // Missing special char
        assertNotNull(validatePasswordRule("ValidLength123"));

        // Valid password
        assertNull(validatePasswordRule("ValidP@ssw0rd"));
    }

    @Test
    public void testPasswordStrengthScoring() {
        // Empty or too short
        assertEquals(1, calculateScore("abc"));

        // 8 chars letters only
        assertEquals(2, calculateScore("abcdefgh"));

        // 8 chars mixed letters and digits
        assertEquals(3, calculateScore("abcdefg1"));

        // Strong password: mixed case, digits, symbols
        assertEquals(4, calculateScore("Abcdefg1!"));
    }

    @Test
    public void testOtpPasteCleaning() {
        String raw1 = " 4 5 8 - 9 1 0 ";
        String cleaned1 = cleanOtp(raw1, 6);
        assertEquals("458910", cleaned1);

        String raw2 = "123456789";
        String cleaned2 = cleanOtp(raw2, 6);
        assertEquals("123456", cleaned2);
    }

    private String validatePasswordRule(String password) {
        if (password == null || password.isEmpty()) {
            return "Please enter a new password.";
        }
        if (password.length() < 8) {
            return "Password must be at least 8 characters.";
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                hasSpecial = true;
            }
        }

        if (!hasLetter) return "Password must include at least one letter.";
        if (!hasDigit) return "Password must include at least one number.";
        if (!hasSpecial) return "Password must include at least one special character.";

        return null;
    }

    private int calculateScore(String password) {
        if (password == null || password.length() < 8) return 1;

        int score = 1;
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) hasSpecial = true;
        }

        if (hasLower || hasUpper) score++;
        if (hasDigit) score++;
        if (hasSpecial) score++;
        if (hasLower && hasUpper && score < 4) score++;

        return Math.min(4, Math.max(1, score));
    }

    private String cleanOtp(String raw, int maxLength) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() > maxLength) {
            return digits.substring(0, maxLength);
        }
        return digits;
    }
}
