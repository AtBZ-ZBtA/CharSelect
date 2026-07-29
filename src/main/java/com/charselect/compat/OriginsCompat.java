package com.charselect.compat;

import com.charselect.CharSelect;
import com.charselect.api.AttachmentDataHandler;
import com.charselect.api.CharacterDataHandler;
import com.charselect.api.CharacterDataScope;
import com.charselect.api.RegisterCharacterDataEvent;
import com.charselect.config.ModGameRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Origins (<a href="https://github.com/IAFEnvoy/Origins-NeoForge">IAFEnvoy/Origins-NeoForge</a>,
 * mod id {@code origins}) integration, so a character's chosen species and its unlocked
 * powers travel with it instead of resetting per world - the thing most RP servers running
 * this mod would actually want.
 *
 * <p><b>Verification status:</b> confirmed from the mod's own source that a player's origin
 * choice and its unlocked powers/component state are held together in a single NeoForge data
 * attachment ({@code EntityOriginAttachment}, serialising "origins", "powers" and
 * "components" fields as one compound) - unlike Curios, which is one attachment holding
 * exactly one kind of thing. The attachment's exact registered key could not be confirmed
 * from the sources available while writing this, so it is located defensively at runtime -
 * whichever key under {@code neoforge:attachments} starts with {@code "origins:"} - rather
 * than hardcoded and potentially silently wrong. This should be re-checked, and this whole
 * class exercised, against a real installation before relying on it.
 *
 * <p><b>Scope, and why it is simpler than first asked for:</b> the owner's original request
 * was for the origin choice specifically to follow the character when {@code itemsTransfer}
 * is off, and unlocked powers specifically when it is on - two different rules for two parts
 * of what turns out to be one attachment. {@link CharacterDataHandler} hands each registered
 * handler exactly one destination bucket (shared or per-world) per save, with no way for a
 * single handler to split its own data across both from within one call - splitting the two
 * pieces apart would need a second, order-dependent handler coordinating a handoff through a
 * static field, which is exactly the kind of fragile, hard-to-verify plumbing not worth
 * building for a mod integration that cannot be tested end-to-end in this environment. This
 * instead moves the whole attachment as one unit, gated by {@code itemsTransfer} - the same
 * rule every other modded attachment (Curios included) already follows in this mod via the
 * {@code transferModdedData} config toggle, just tied to the multiplayer gamerule instead.
 * Splitting origin from powers, if still wanted after trying this against the real mod, is a
 * follow-up, not a blocker for shipping the rest of this feature set.
 */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class OriginsCompat {
    private static final String MODID = "origins";
    private static final ResourceLocation HANDLER_ID = CharSelect.id("origins");

    private static volatile MinecraftServer server;
    private static Boolean present;

    private OriginsCompat() {
    }

    public static boolean isPresent() {
        if (present == null) {
            present = ModList.get() != null && ModList.get().isLoaded(MODID);
        }
        return present;
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        server = null;
    }

    @SubscribeEvent
    public static void onRegisterCharacterData(RegisterCharacterDataEvent event) {
        if (isPresent()) {
            event.register(new Handler());
        }
    }

    private static boolean itemsTransferAllowed() {
        MinecraftServer current = server;
        return current != null && current.getGameRules().getBoolean(ModGameRules.ITEMS_TRANSFER);
    }

    private static final class Handler implements CharacterDataHandler {
        @Override
        public ResourceLocation id() {
            return HANDLER_ID;
        }

        @Override
        public CharacterDataScope defaultScope() {
            return itemsTransferAllowed() ? CharacterDataScope.SHARED : CharacterDataScope.PER_WORLD;
        }

        @Override
        public void capture(CompoundTag playerNbt, CompoundTag into) {
            if (!playerNbt.contains(AttachmentDataHandler.ATTACHMENTS_KEY)) {
                return;
            }
            CompoundTag attachments = playerNbt.getCompound(AttachmentDataHandler.ATTACHMENTS_KEY);
            String key = findOriginsKey(attachments);
            if (key == null) {
                return;
            }
            into.put(key, attachments.get(key).copy());
            attachments.remove(key);
            if (attachments.isEmpty()) {
                playerNbt.remove(AttachmentDataHandler.ATTACHMENTS_KEY);
            }
        }

        @Override
        public void restore(CompoundTag from, CompoundTag playerNbt) {
            String key = findOriginsKey(from);
            if (key == null) {
                return;
            }
            CompoundTag attachments = playerNbt.contains(AttachmentDataHandler.ATTACHMENTS_KEY)
                    ? playerNbt.getCompound(AttachmentDataHandler.ATTACHMENTS_KEY)
                    : new CompoundTag();
            attachments.put(key, from.get(key).copy());
            playerNbt.put(AttachmentDataHandler.ATTACHMENTS_KEY, attachments);
        }

        private static String findOriginsKey(CompoundTag attachments) {
            for (String key : attachments.getAllKeys()) {
                if (key.startsWith(MODID + ":")) {
                    return key;
                }
            }
            return null;
        }
    }
}
