package com.charselect.client.skin;

import com.charselect.CharSelect;
import com.charselect.character.SkinRef;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Lets the player pick a skin PNG from disk through the OS file dialog, then validates it
 * and copies it into the skins folder.
 */
public final class SkinFilePicker {

    private SkinFilePicker() {
    }

    /** An import that failed, carrying a translation key for the GUI. */
    public static class SkinImportException extends RuntimeException {
        private final String translationKey;

        SkinImportException(String translationKey, @Nullable Throwable cause) {
            super(translationKey, cause);
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    /**
     * Opens the dialog and imports the chosen file. The future completes with an empty
     * optional if the player cancelled, and fails with {@link SkinImportException} if the
     * file was not usable.
     */
    public static CompletableFuture<Optional<SkinRef>> pick() {
        return CompletableFuture.supplyAsync(SkinFilePicker::openDialog, dialogExecutor())
                .thenApplyAsync(path -> path.map(SkinFilePicker::importFrom), Util.ioPool());
    }

    /**
     * macOS insists native dialogs run on the main thread, so there we accept the frozen
     * frame. Everywhere else the dialog runs off-thread and the game keeps rendering.
     */
    private static Executor dialogExecutor() {
        return Util.getPlatform() == Util.OS.OSX ? Minecraft.getInstance() : Util.ioPool();
    }

    private static Optional<Path> openDialog() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png"));
            filters.flip();

            String chosen = TinyFileDialogs.tinyfd_openFileDialog(
                    "Select a skin", null, filters, "Minecraft skin (*.png)", false);
            return Optional.ofNullable(chosen).map(Path::of);
        } catch (Exception e) {
            CharSelect.LOGGER.error("The skin file dialog could not be opened", e);
            throw new SkinImportException("charselect.skin.error.dialog_failed", e);
        }
    }

    private static SkinRef importFrom(Path path) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (Exception e) {
            throw new SkinImportException("charselect.skin.error.unreadable", e);
        }

        try {
            SkinStorage.validate(bytes);
            String hash = SkinStorage.store(bytes);
            return SkinRef.file(fileLabel(path), hash, guessModel(bytes));
        } catch (SkinStorage.InvalidSkinException e) {
            throw new SkinImportException(e.getMessage(), e);
        } catch (Exception e) {
            throw new SkinImportException("charselect.skin.error.save_failed", e);
        }
    }

    /** Uploaded skins carry no model metadata, so arm width is inferred from the pixels. */
    private static SkinRef.SkinModel guessModel(byte[] bytes) {
        try (NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes))) {
            return SkinImage.guessModel(image);
        } catch (Exception e) {
            return SkinRef.SkinModel.WIDE;
        }
    }

    private static String fileLabel(Path path) {
        String name = path.getFileName().toString();
        return name.length() > 32 ? name.substring(0, 31) + "…" : name;
    }
}
