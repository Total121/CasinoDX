package com.dravorax.casinodx.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates that CasinoDX head textures resolve to real Mojang CDN URLs.
 * Broken/truncated base64 (the old bug that rendered Steve heads) must be rejected.
 */
class HeadTexturesTest {

    @Test
    void defaultHashesResolveToCdnUrls() {
        for (var entry : HeadTextureCatalog.DEFAULT_HASHES.entrySet()) {
            String url = HeadTextureCatalog.resolveToUrl(entry.getValue());
            assertNotNull(url, "hash for " + entry.getKey() + " must resolve");
            assertTrue(url.startsWith("https://textures.minecraft.net/texture/"),
                    entry.getKey() + " → " + url);
            String hash = url.substring(url.lastIndexOf('/') + 1);
            assertTrue(hash.length() >= 60, entry.getKey() + " hash too short: " + hash.length());
            assertFalse(hash.contains("?"), entry.getKey() + " corrupted path");
        }
    }

    @Test
    void fullHttpsUrlAccepted() {
        String url = HeadTextureCatalog.resolveToUrl(
                "https://textures.minecraft.net/texture/3438e0c8dc54f4f42a5ab71240cad8a79f15db754373d7175882efd33fc2164d");
        assertNotNull(url);
        assertTrue(url.startsWith("https://"));
    }

    @Test
    void httpUpgradedToHttps() {
        String url = HeadTextureCatalog.resolveToUrl(
                "http://textures.minecraft.net/texture/3438e0c8dc54f4f42a5ab71240cad8a79f15db754373d7175882efd33fc2164d");
        assertNotNull(url);
        assertTrue(url.startsWith("https://"));
    }

    @Test
    void validBase64Accepted() {
        String b64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzQzOGUwYzhkYzU0ZjRmNDJhNWFiNzEyNDBjYWQ4YTc5ZjE1ZGI3NTQzNzNkNzE3NTg4MmVmZDMzZmMyMTY0ZCJ9fX0=";
        String url = HeadTextureCatalog.resolveToUrl(b64);
        assertNotNull(url);
        assertTrue(url.contains("/texture/3438e0c8"));
    }

    @Test
    void corruptBase64WithQxtureRejected() {
        String corrupt = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3GleHR1cmUvYjNjZWUyZjJiMTczZTAxM2Q4Y2UyNThkN2RmMjMzNmY4YWExZDdkN2U4OTc5MzI1Y2JmIn19fQ==";
        assertNull(HeadTextureCatalog.resolveToUrl(corrupt));
    }

    @Test
    void shortHashRejected() {
        assertNull(HeadTextureCatalog.resolveToUrl("abc123"));
    }

    @Test
    void blankRejected() {
        assertNull(HeadTextureCatalog.resolveToUrl(""));
        assertNull(HeadTextureCatalog.resolveToUrl(null));
    }

    @Test
    void requiredGuiKeysPresent() {
        String[] required = {
                "coin", "close", "arrow-left", "arrow-right", "info", "help",
                "plus", "minus", "confirm", "stats", "history", "replay", "custom-bet",
                "mines", "roulette", "blackjack", "coinflip", "crash", "wheel", "slots"
        };
        for (String key : required) {
            assertTrue(HeadTextureCatalog.DEFAULT_HASHES.containsKey(key), "missing default head: " + key);
        }
    }
}
