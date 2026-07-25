package com.charselect.client.skin;

import com.charselect.CharSelect;
import com.charselect.character.SkinRef;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a {@link SkinRef} into something the renderer can use.
 *
 * <p>Lookups are non-blocking: an unloaded skin renders as its default until the PNG has been
 * read off disk and uploaded on the render thread, which takes a frame or two at most.
 */
public final class SkinTextureCache {
    public static final ResourceLocation STEVE =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    public static final ResourceLocation ALEX =
            ResourceLocation.withDefaultNamespace("textures/entity/player/slim/alex.png");

    private static final Map<String, ResourceLocation> LOADED = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private SkinTextureCache() {
    }

    /** The full skin description used by the renderer and the paper doll widget. */
    public static PlayerSkin playerSkin(SkinRef ref) {
        if (ref.usesAccountSkin()) {
            return accountSkin();
        }
        PlayerSkin.Model model = ref.model().slim() ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
        return new PlayerSkin(texture(ref), null, null, null, model, true);
    }

    /**
     * The player's own account skin, for characters that never wanted an override. Uses the
     * insecure lookup so it resolves straight away rather than a frame or two later.
     */
    public static PlayerSkin accountSkin() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getSkinManager().getInsecureSkin(minecraft.getGameProfile());
    }

    /**
     * The texture for this ref right now. Kicks off a load if needed and falls back to the
     * matching vanilla default in the meantime.
     */
    public static ResourceLocation texture(SkinRef ref) {
        if (!ref.hasCustomTexture()) {
            return defaultTexture(ref);
        }
        ResourceLocation loaded = LOADED.get(ref.hash());
        if (loaded != null) {
            return loaded;
        }
        load(ref.hash());
        return defaultTexture(ref);
    }

    public static ResourceLocation defaultTexture(SkinRef ref) {
        return ref.model().slim() ? ALEX : STEVE;
    }

    /** True once the custom PNG is uploaded, or immediately for default skins. */
    public static boolean isReady(SkinRef ref) {
        return !ref.hasCustomTexture() || LOADED.containsKey(ref.hash());
    }

    /** Loads a skin ahead of time, so a screen can show it without a first-frame flicker. */
    public static CompletableFuture<Void> preload(SkinRef ref) {
        if (!ref.hasCustomTexture() || LOADED.containsKey(ref.hash())) {
            return CompletableFuture.completedFuture(null);
        }
        return load(ref.hash());
    }

    private static CompletableFuture<Void> load(String hash) {
        if (!LOADING.add(hash)) {
            return CompletableFuture.completedFuture(null);
        }
        if (FAILED.contains(hash)) {
            LOADING.remove(hash);
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return SkinStorage.read(hash);
                    } catch (Exception e) {
                        CharSelect.LOGGER.error("Could not read cached skin {}", hash, e);
                        return null;
                    }
                }, Util.ioPool())
                .thenAcceptAsync(bytes -> {
                    try {
                        if (bytes == null) {
                            FAILED.add(hash);
                            return;
                        }
                        upload(hash, bytes);
                    } finally {
                        LOADING.remove(hash);
                    }
                }, Minecraft.getInstance());
    }

    /** Must run on the render thread: uploading a texture touches GL state. */
    private static void upload(String hash, byte[] bytes) {
        NativeImage raw;
        try {
            raw = NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            CharSelect.LOGGER.error("Cached skin {} is not a readable PNG", hash, e);
            FAILED.add(hash);
            return;
        }

        NativeImage image = SkinImage.normalise(raw);
        if (image == null) {
            CharSelect.LOGGER.error("Cached skin {} is not skin-shaped", hash);
            FAILED.add(hash);
            return;
        }

        ResourceLocation location = CharSelect.id("skins/" + hash);
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
        LOADED.put(hash, location);
    }

    /** Drops a skin's GPU texture, for when the last character using it is deleted. */
    public static void forget(String hash) {
        ResourceLocation location = LOADED.remove(hash);
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
        }
        FAILED.remove(hash);
    }
}
