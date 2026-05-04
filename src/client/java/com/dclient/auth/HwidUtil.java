package com.dclient.auth;

import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HwidUtil {

    private static String cached = null;

    /**
     * Generates a stable hardware ID from MAC addresses + OS info.
     * Uses sorted MACs so network interface ordering doesn't matter.
     * Hashed with SHA-256 so the raw MAC is never sent.
     */
    public static String get() {
        if (cached != null) return cached;
        try {
            // Collect all non-loopback, non-virtual MAC addresses and sort them
            // so the order doesn't change between sessions
            List<String> macs = new ArrayList<>();
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || ni.isVirtual()) continue;
                byte[] mac = ni.getHardwareAddress();
                if (mac == null || mac.length == 0) continue;
                // Skip all-zero MACs (some virtual adapters)
                boolean allZero = true;
                for (byte b : mac) if (b != 0) { allZero = false; break; }
                if (allZero) continue;
                StringBuilder sb = new StringBuilder();
                for (byte b : mac) sb.append(String.format("%02X", b));
                macs.add(sb.toString());
            }
            Collections.sort(macs); // sort so order is deterministic

            StringBuilder raw = new StringBuilder();
            for (String mac : macs) raw.append(mac);

            // Only use stable OS info — NOT user.name (can change) or hostname
            raw.append(System.getProperty("os.name", ""));
            raw.append(System.getProperty("os.arch", ""));

            if (raw.length() == 0) {
                // No MACs found — use a stable fallback based on OS only
                raw.append(System.getProperty("os.name", "unknown"));
                raw.append(System.getProperty("os.arch", "unknown"));
                raw.append(System.getProperty("os.version", "unknown"));
            }

            // SHA-256 hash
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.toString().getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            cached = hex.toString();
        } catch (Exception e) {
            // Stable fallback using OS properties only
            try {
                String fallbackRaw = System.getProperty("os.name", "") +
                    System.getProperty("os.arch", "") +
                    System.getProperty("os.version", "");
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(fallbackRaw.getBytes());
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) hex.append(String.format("%02x", b));
                cached = hex.toString();
            } catch (Exception e2) {
                cached = "fallback-static";
            }
        }
        return cached;
    }
}
