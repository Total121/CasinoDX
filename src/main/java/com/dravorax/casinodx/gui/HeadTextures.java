package com.dravorax.casinodx.gui;

import com.dravorax.casinodx.config.ConfigManager;
import com.dravorax.casinodx.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Custom player-head icons for CasinoDX GUIs.
 *
 * <p>Texture values come from <a href="https://minecraft-heads.com/">minecraft-heads.com</a>
 * catalogs. At runtime nothing is fetched from that site — only immutable
 * {@code textures.minecraft.net} skin URLs are applied via {@link PlayerProfile}.
 *
 * @see HeadTextureCatalog for the hash catalog and URL resolver
 */
public final class HeadTextures {

    /** @deprecated use {@link HeadTextureCatalog#DEFAULT_HASHES} */
    @Deprecated
    public static final Map<String, String> DEFAULT_HASHES = HeadTextureCatalog.DEFAULT_HASHES;

    private final ConfigManager configManager;
    private final Logger logger;
    private final Map<String, String> resolvedUrls = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> cache = new ConcurrentHashMap<>();

    public HeadTextures(ConfigManager configManager) {
        this.configManager = configManager;
        this.logger = Bukkit.getLogger();
        reload();
    }

    public void reload() {
        cache.clear();
        resolvedUrls.clear();
        for (Map.Entry<String, String> e : HeadTextureCatalog.DEFAULT_HASHES.entrySet()) {
            String url = HeadTextureCatalog.resolveToUrl(e.getValue());
            if (url != null) {
                resolvedUrls.put(e.getKey(), url);
            }
        }
        ConfigurationSection heads = null;
        if (configManager.gui() != null) {
            heads = configManager.gui().getConfigurationSection("heads");
        }
        try {
            var file = configManager.moduleFile("gui", "heads.yml");
            if (file != null && file.contains("heads")) {
                ConfigurationSection section = file.getConfigurationSection("heads");
                if (section != null) {
                    heads = section;
                }
            }
        } catch (Throwable ignored) {
        }
        if (heads != null) {
            for (String key : heads.getKeys(false)) {
                String value = heads.getString(key, "").trim();
                if (value.isEmpty()) {
                    continue;
                }
                String url = HeadTextureCatalog.resolveToUrl(value);
                if (url != null) {
                    resolvedUrls.put(key.toLowerCase(), url);
                } else {
                    logger.log(Level.WARNING, "[CasinoDX] Invalid head texture for key '" + key + "'");
                }
            }
        }
    }

    public ItemStack createHead(String key) {
        String id = key == null ? "question" : key.toLowerCase();
        ItemStack cached = cache.get(id);
        if (cached != null) {
            return cached.clone();
        }
        String url = resolvedUrls.get(id);
        if (url == null) {
            url = resolvedUrls.get("question");
        }
        ItemStack head = buildHead(id, url);
        if (head == null) {
            return new ItemStack(Material.PLAYER_HEAD);
        }
        cache.put(id, head);
        return head.clone();
    }

    public ItemStack createHead(String key, String displayName, String... lore) {
        return ItemBuilder.of(createHead(key))
                .name(displayName)
                .lore(lore)
                .hideAllFlags()
                .build();
    }

    public String urlFor(String key) {
        return resolvedUrls.get(key == null ? "" : key.toLowerCase());
    }

    public boolean hasValidTexture(String key) {
        String url = urlFor(key);
        return url != null && url.contains("textures.minecraft.net/texture/");
    }

    /** @see HeadTextureCatalog#resolveToUrl(String) */
    public static String resolveToUrl(String value) {
        return HeadTextureCatalog.resolveToUrl(value);
    }

    private ItemStack buildHead(String id, String url) {
        if (url == null) {
            return null;
        }
        try {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta == null) {
                return null;
            }
            PlayerProfile profile = Bukkit.createPlayerProfile(stableUuid("CasinoDX-head:" + id));
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create(url).toURL());
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
            skull.setItemMeta(meta);
            return skull;
        } catch (Throwable t) {
            logger.log(Level.FINE, "Failed to build head '" + id + "': " + t.getMessage());
            return null;
        }
    }

    private static UUID stableUuid(String seed) {
        UUID base = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        long msb = (base.getMostSignificantBits() & 0xFFFFFFFFFFFF0FFFL) | 0x0000000000004000L;
        long lsb = (base.getLeastSignificantBits() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
