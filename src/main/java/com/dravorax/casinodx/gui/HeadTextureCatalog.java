package com.dravorax.casinodx.gui;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure head-texture catalog and URL resolver with no Bukkit dependency (unit-testable).
 * Texture hashes are sourced from <a href="https://minecraft-heads.com/">minecraft-heads.com</a>
 * catalogs; only Mojang CDN URLs are used at runtime.
 */
public final class HeadTextureCatalog {

    public static final String TEX_BASE = "https://textures.minecraft.net/texture/";

    public static final Map<String, String> DEFAULT_HASHES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("coin", "3438e0c8dc54f4f42a5ab71240cad8a79f15db754373d7175882efd33fc2164d");
        m.put("money", "209299a117bee88d3262f6ab98211fba344ecae39b47ec848129706dedc81e4f");
        m.put("arrow-left", "5ae78451bf26cf49fd5f54cd8f2b37cd25c92e5ca76298b3634cb541e9ad89");
        m.put("arrow-right", "117f3666d3cedfae57778c78230d480c719fd5f65ffa2ad3255385e433b86e");
        m.put("close", "27548362a24c0fa8453e4d93e68c5969ddbde57bf6666c0319c1ed1e84d89065");
        m.put("info", "16439d2e306b225516aa9a6d007a7e75edd2d5015d113b42f44be62a517e574f");
        m.put("help", "fc02695cb3fc5e3b81138a0f06a8881ac32c6fc623c78d855ff7855fc69614ee");
        m.put("question", "16439d2e306b225516aa9a6d007a7e75edd2d5015d113b42f44be62a517e574f");
        m.put("stats", "29f50ba66cdba4c9a244b6e1dd7bba95301c2d6d81f924bbbb7f49175282e802");
        m.put("history", "1f575bb54a2e9133aaa1310a14642f78a014fcc9360774171663d34db236ccc4");
        m.put("confirm", "bc0e6d9e242735481918c5fd14498bd760bb9f4ff6430ad4696b38e8a883da97");
        // Distinct hashes (bet +/- now use gold materials in GUI; heads kept unique for catalog)
        m.put("plus", "44c7b74a61b2174c79749b9d896981cf53be329b4859a24a558aefa27f850842");
        m.put("minus", "61f2267a299f4e854673ad3486a75ee197e41fd79af31f95c6c95e49037019f4");
        m.put("win", "bc0e6d9e242735481918c5fd14498bd760bb9f4ff6430ad4696b38e8a883da97");
        m.put("loss", "27548362a24c0fa8453e4d93e68c5969ddbde57bf6666c0319c1ed1e84d89065");
        m.put("mines", "18046d38a97c91f59949ea742efd74827ccedefe98528552cd327b4b61c39b5f");
        m.put("roulette", "ece98abf014afc83a2153e32ea6719b34512b7e95107aeb7b2354d365e7c86e9");
        m.put("blackjack", "68de99b41880f6025bdfb7de57768c12dc1aa89090a59e9dea60ac5d2b809ff2");
        m.put("coinflip", "3438e0c8dc54f4f42a5ab71240cad8a79f15db754373d7175882efd33fc2164d");
        m.put("crash", "de8a38e9afbd3da10d19b577c55c7bfd6b4f2e407e44d4017b23be9167abff02");
        m.put("wheel", "fc1ce577bde281a3d235acd5a48d46994c695c9f535c28f1277cda1fd2af617d");
        m.put("slots", "6061f98aaff1e406e06f6f13ffe06005873f3d1eaddb1b59e19dda0ed9ffb270");
        m.put("heads", "3438e0c8dc54f4f42a5ab71240cad8a79f15db754373d7175882efd33fc2164d");
        m.put("tails", "ff33754d818897b4b9fba406f7fddae52b277f38f81c9b0ebb1140ed6553735c");
        // Distinct from arrow-right (chest texture)
        m.put("replay", "1f575bb54a2e9133aaa1310a14642f78a014fcc9360774171663d34db236ccc4");
        m.put("custom-bet", "209299a117bee88d3262f6ab98211fba344ecae39b47ec848129706dedc81e4f");
        DEFAULT_HASHES = Collections.unmodifiableMap(m);
    }

    private HeadTextureCatalog() {
    }

    /**
     * Accepts a raw hash, full CDN URL, or base64 textures property.
     * Returns a https Mojang CDN URL, or null if the value is invalid/corrupt.
     */
    public static String resolveToUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if (v.startsWith("http://") || v.startsWith("https://")) {
            if (!v.contains("textures.minecraft.net/texture/")) {
                return null;
            }
            if (v.startsWith("http://")) {
                v = "https://" + v.substring("http://".length());
            }
            return validateTextureUrl(v);
        }
        if (v.startsWith("eyJ") || v.length() > 100) {
            try {
                String json = new String(Base64.getDecoder().decode(v), StandardCharsets.UTF_8);
                int urlKey = json.indexOf("\"url\"");
                if (urlKey < 0) {
                    return null;
                }
                int colon = json.indexOf(':', urlKey);
                int q1 = json.indexOf('"', colon + 1);
                int q2 = json.indexOf('"', q1 + 1);
                if (q1 < 0 || q2 < 0) {
                    return null;
                }
                String url = json.substring(q1 + 1, q2);
                if (url.startsWith("http://")) {
                    url = "https://" + url.substring("http://".length());
                }
                return validateTextureUrl(url);
            } catch (Throwable t) {
                return null;
            }
        }
        if (v.length() < 60 || v.length() > 64) {
            return null;
        }
        if (!v.matches("[0-9a-fA-F]+")) {
            return null;
        }
        return TEX_BASE + v.toLowerCase();
    }

    private static String validateTextureUrl(String url) {
        if (url == null || !url.contains("textures.minecraft.net/texture/")) {
            return null;
        }
        int idx = url.lastIndexOf("/texture/");
        String hash = idx >= 0 ? url.substring(idx + "/texture/".length()) : "";
        if (hash.length() < 60 || hash.contains("?")) {
            return null;
        }
        return url;
    }
}
