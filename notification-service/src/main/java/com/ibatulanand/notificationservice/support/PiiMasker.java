package com.ibatulanand.notificationservice.support;

/** Masks customer contact details (email / phone) before they reach a log line. */
public final class PiiMasker {

    private PiiMasker() {
    }

    public static String mask(String destination) {
        if (destination == null || destination.isBlank()) {
            return "<none>";
        }
        String value = destination.trim();
        int at = value.indexOf('@');
        if (at > 0) {
            return LogText.oneLine(maskEmail(value, at));
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() >= 4) {
            return "***-***-" + digits.substring(digits.length() - 4);
        }
        return "***";
    }

    private static String maskEmail(String email, int at) {
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String tld = dot >= 0 ? domain.substring(dot) : "";
        String domainHead = domain.isEmpty() ? "" : domain.substring(0, 1);
        return email.charAt(0) + "***@" + domainHead + "***" + tld;
    }
}
