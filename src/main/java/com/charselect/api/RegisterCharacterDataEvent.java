package com.charselect.api;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.function.Consumer;

/**
 * Fired on the mod event bus so other mods can say which of their data belongs to the
 * character rather than the world.
 *
 * <p>Character Select already carries anything it finds in the player's saved NBT, so a mod
 * that stores its data there works with no code at all. Registering here buys two things:
 * a stable id the player can name in the {@code worldLocalData} config, and the ability to
 * choose a different default scope from "follows the character".
 *
 * <pre>{@code
 * @SubscribeEvent
 * public static void onRegisterCharacterData(RegisterCharacterDataEvent event) {
 *     // A NeoForge attachment, by its registry id.
 *     event.registerAttachment(
 *             ResourceLocation.fromNamespaceAndPath("mymod", "backpack"),
 *             CharacterDataScope.SHARED);
 *
 *     // Plain keys written into the player's NBT.
 *     event.registerNbtKeys(
 *             ResourceLocation.fromNamespaceAndPath("mymod", "home"),
 *             CharacterDataScope.PER_WORLD,
 *             "mymod_home_pos", "mymod_home_dim");
 * }
 * }</pre>
 *
 * <p>Listening for this event does not require a hard dependency: guard the subscriber with
 * {@code ModList.get().isLoaded("charselect")}, or ship the class so it is only loaded when
 * the event fires.
 */
public class RegisterCharacterDataEvent extends Event implements IModBusEvent {

    private final Consumer<CharacterDataHandler> sink;

    public RegisterCharacterDataEvent(Consumer<CharacterDataHandler> sink) {
        this.sink = sink;
    }

    /** Registers a handler with full control over capture and restore. */
    public void register(CharacterDataHandler handler) {
        sink.accept(handler);
    }

    /**
     * Declares that a NeoForge attachment belongs to the character.
     *
     * @param attachmentId the attachment type's registry id, for example {@code curios:inventory}
     */
    public void registerAttachment(ResourceLocation attachmentId, CharacterDataScope scope) {
        sink.accept(new AttachmentDataHandler(attachmentId, scope));
    }

    /**
     * Declares that some top-level keys in the player's saved NBT belong to the character.
     *
     * @param id    a stable id for this group, used in logs and the config
     * @param keys  the NBT keys to claim
     */
    public void registerNbtKeys(ResourceLocation id, CharacterDataScope scope, String... keys) {
        sink.accept(new NbtKeyDataHandler(id, scope, keys));
    }
}
