package com.seple.ThingsBoard_Bot.util;

public class ThingsBoardRequestContext {
    private static final ThreadLocal<String> currentHost = new ThreadLocal<>();

    public static void setHost(String host) {
        currentHost.set(host);
    }

    public static String getHost() {
        return currentHost.get();
    }

    public static void clear() {
        currentHost.remove();
    }
}
