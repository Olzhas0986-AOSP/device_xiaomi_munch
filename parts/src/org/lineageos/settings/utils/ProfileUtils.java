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
}
