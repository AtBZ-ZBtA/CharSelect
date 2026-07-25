package com.charselect.client.skin;

import com.charselect.character.SkinRef;
import com.mojang.blaze3d.platform.NativeImage;

import javax.annotation.Nullable;

/**
 * Normalises a raw skin PNG the way vanilla does before it reaches the renderer: expands the
 * 64x32 pre-1.8 layout to 64x64, and forces the body regions opaque so a skin authored with
 * a stray alpha channel does not render see-through.
 *
 * <p>This mirrors {@code HttpTexture#processLegacySkin}, which is private, so uploaded skins
 * behave exactly like ones Minecraft downloads itself.
 */
public final class SkinImage {

    private SkinImage() {
    }

    /**
     * Returns a 64x64 image ready to upload. The input is closed; the caller owns the result.
     * Returns null if the image is not skin-shaped.
     */
    @Nullable
    public static NativeImage normalise(NativeImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width != 64 || (height != 32 && height != 64)) {
            source.close();
            return null;
        }

        NativeImage image = source;
        boolean legacy = height == 32;
        if (legacy) {
            NativeImage expanded = new NativeImage(64, 64, true);
            expanded.copyFrom(source);
            source.close();
            image = expanded;

            expanded.fillRect(0, 32, 64, 32, 0);
            // Mirror the single arm and leg the old layout provided onto the second limb.
            expanded.copyRect(4, 16, 16, 32, 4, 4, true, false);
            expanded.copyRect(8, 16, 16, 32, 4, 4, true, false);
            expanded.copyRect(0, 20, 24, 32, 4, 12, true, false);
            expanded.copyRect(4, 20, 16, 32, 4, 12, true, false);
            expanded.copyRect(8, 20, 8, 32, 4, 12, true, false);
            expanded.copyRect(12, 20, 16, 32, 4, 12, true, false);
            expanded.copyRect(44, 16, -8, 32, 4, 4, true, false);
            expanded.copyRect(48, 16, -8, 32, 4, 4, true, false);
            expanded.copyRect(40, 20, 0, 32, 4, 12, true, false);
            expanded.copyRect(44, 20, -8, 32, 4, 12, true, false);
            expanded.copyRect(48, 20, -16, 32, 4, 12, true, false);
            expanded.copyRect(52, 20, -8, 32, 4, 12, true, false);
        }

        setOpaque(image, 0, 0, 32, 16);
        if (legacy) {
            // Old skins that filled the hat layer with opaque pixels meant "no hat", not "solid hat".
            stripFullyOpaqueOverlay(image, 32, 0, 64, 32);
        }
        setOpaque(image, 0, 16, 64, 32);
        setOpaque(image, 16, 48, 48, 64);
        return image;
    }

    /**
     * Guesses arm width from the skin itself: slim skins leave column 54 of the right arm
     * transparent, since the arm is only 3 pixels wide there. Used as the initial suggestion
     * for uploaded skins, which carry no model metadata.
     */
    public static SkinRef.SkinModel guessModel(NativeImage image) {
        if (image.getWidth() != 64 || image.getHeight() < 64) {
            return SkinRef.SkinModel.WIDE;
        }
        // The 4th column of the wide arm's front face. Blank there means a 3-wide (slim) arm.
        for (int y = 20; y < 32; y++) {
            if ((image.getPixelRGBA(54, y) >> 24 & 0xFF) != 0) {
                return SkinRef.SkinModel.WIDE;
            }
        }
        return SkinRef.SkinModel.SLIM;
    }

    private static void setOpaque(NativeImage image, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                image.setPixelRGBA(x, y, image.getPixelRGBA(x, y) | 0xFF000000);
            }
        }
    }

    private static void stripFullyOpaqueOverlay(NativeImage image, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                if ((image.getPixelRGBA(x, y) >> 24 & 0xFF) < 128) {
                    return;
                }
            }
        }
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                image.setPixelRGBA(x, y, image.getPixelRGBA(x, y) & 0x00FFFFFF);
            }
        }
    }
}
