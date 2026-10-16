package com.ibatulanand.notificationservice.support;

/** Neutralises control characters (CR/LF, tabs, ...) in event-supplied text so it cannot forge extra log lines. */
public final class LogText {

    private LogText() {
    }

    public static String oneLine(String value) {
        return value == null ? null : value.replaceAll("\\p{Cntrl}", "_");
    }
}
