package com.charselect.client.skin;

import com.charselect.CharSelect;
import com.charselect.character.CharacterStore;
import com.mojang.blaze3d.platform.NativeImage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Keeps skin PNGs on disk under {@code charselect/skins/}, named by content hash so the
 * same skin imported twice costs one file and two characters can share it.
 */
public final class SkinStorage {

    /** Vanilla skins are 64x64, or 64x32 for the pre-1.8 layout. */
    private static final int WIDTH = 64;
    private static final int TALL_HEIGHT = 64;
    private static final int LEGACY_HEIGHT = 32;

    /** A 64x64 RGBA PNG is ~16KB; anything far past that is not a skin. */
    private static final long MAX_BYTES = 1024L * 1024L;

    private SkinStorage() {
    }

    /** Thrown when a candidate file is not usable as a Minecraft skin. */
    public static class InvalidSkinException extends Exception {
        public InvalidSkinException(String message) {
            super(message);
        }
    }

    /**
     * Checks that the bytes are a PNG of skin dimensions and writes them into the skins
     * folder, returning the content hash that names the file.
     */
    public static String store(byte[] png) throws InvalidSkinException, IOException {
        validate(png);
        String hash = hash(png);
        Path target = pathFor(hash);
        if (!Files.exists(target)) {
            Files.createDirectories(target.getParent());
            Files.write(target, png);
            CharSelect.LOGGER.debug("Stored skin {}", hash);
        }
        return hash;
    }

    public static void validate(byte[] png) throws InvalidSkinException {
        if (png.length == 0) {
            throw new InvalidSkinException("charselect.skin.error.empty");
        }
        if (png.length > MAX_BYTES) {
            throw new InvalidSkinException("charselect.skin.error.too_large");
        }
        try (NativeImage image = NativeImage.read(new ByteArrayInputStream(png))) {
            int w = image.getWidth();
            int h = image.getHeight();
            if (w != WIDTH || (h != TALL_HEIGHT && h != LEGACY_HEIGHT)) {
                throw new InvalidSkinException("charselect.skin.error.dimensions");
            }
        } catch (IOException e) {
            throw new InvalidSkinException("charselect.skin.error.not_png");
        }
    }

    public static Path pathFor(String hash) {
        return CharacterStore.get().skinsDir().resolve(hash + ".png");
    }

    public static boolean exists(String hash) {
        return !hash.isEmpty() && Files.isRegularFile(pathFor(hash));
    }

    public static byte[] read(String hash) throws IOException {
        return Files.readAllBytes(pathFor(hash));
    }

    private static String hash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by the JVM spec", e);
        }
    }
}
