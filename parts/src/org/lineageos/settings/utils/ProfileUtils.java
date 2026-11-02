/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.settings.utils;

public final class ProfileUtils {
    private ProfileUtils() {}

    public static String normalize(String value, String... prefixes) {
        String[] profiles = value == null ? new String[0] : value.split(":", -1);
        String[] normalized = new String[prefixes.length];
        for (int i = 0; i < prefixes.length; i++) {
            normalized[i] = i < profiles.length && profiles[i].startsWith(prefixes[i])
                    ? profiles[i] : prefixes[i];
        }
        return String.join(":", normalized);
    }

    public static boolean contains(String profile, String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        for (String entry : profile.substring(profile.indexOf('=') + 1).split(",")) {
            if (packageName.equals(entry)) return true;
        }
        return false;
    }

    public static String removePackage(String value, String packageName) {
        String[] profiles = value.split(":", -1);
        for (int i = 0; i < profiles.length; i++) {
            int separator = profiles[i].indexOf('=') + 1;
            StringBuilder profile = new StringBuilder(profiles[i].substring(0, separator));
            for (String entry : profiles[i].substring(separator).split(",")) {
                if (!entry.isEmpty() && !entry.equals(packageName)) {
                    profile.append(entry).append(',');
                }
            }
            profiles[i] = profile.toString();
        }
        return String.join(":", profiles);
    }
    public static int[] parseTouchProfile(String value) {
        String[] fields = value == null ? new String[0] : value.split(",", -1);
        int[] result = new int[4];
        for (int i = 0; i < result.length && i < fields.length; i++) {
            try {
                result[i] = Math.max(0, Math.min(i == 0 ? 1 : 3,
                        Integer.parseInt(fields[i])));
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }
    public static int boundedInt(String value, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
