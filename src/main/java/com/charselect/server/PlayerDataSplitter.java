package com.charselect.server;

import com.charselect.CharSelect;
import com.charselect.api.AttachmentDataHandler;
import com.charselect.api.CharacterDataHandler;
import com.charselect.api.CharacterDataScope;
import com.charselect.character.CharacterProfile;
import com.charselect.character.WorldSlot;
import com.charselect.config.CharSelectConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Splits a saved player into the part that follows the character everywhere and the part
 * that stays with one world, and puts it back together on the way in.
 *
 * <p>A config entry set to {@code false} does not hand that data back to the world file -
 * worlds never hold player data under this mod. It narrows the data's scope to a single
 * world inside the character, which gives the same behaviour without needing the character
 * to have its own account UUID.
 */
public final class PlayerDataSplitter {

    /**
     * Keys that only mean anything in the world they were written in. Always world-scoped,
     * whatever the config says.
     */
    private static final Set<String> POSITIONAL = Set.of(
            "Pos", "Rotation", "Motion", "Dimension", "OnGround", "FallDistance", "FallFlying",
            "SpawnX", "SpawnY", "SpawnZ", "SpawnAngle", "SpawnDimension", "SpawnForced",
            "respawn", "enteredNetherPosition", "LastDeathLocation", "PortalCooldown",
            "SleepingX", "SleepingY", "SleepingZ", "RootVehicle", "Passengers", "Vehicle");

    /** Rewritten from the live entity on every load, so carrying it over is pointless. */
    private static final Set<String> DROPPED = Set.of("UUID", "id");

    /** Where registered handlers keep their data inside a bucket, one compound each. */
    private static final String HANDLER_BUCKET = "charselect:handlers";

    private record Group(Supplier<ModConfigSpec.BooleanValue> toggle, Set<String> keys) {
        boolean transfers() {
            return toggle.get().get();
        }
    }

    private static final List<Group> GROUPS = List.of(
            new Group(() -> CharSelectConfig.INSTANCE.transferInventory,
                    Set.of("Inventory", "SelectedItemSlot")),
            new Group(() -> CharSelectConfig.INSTANCE.transferEnderChest,
                    Set.of("EnderItems")),
            new Group(() -> CharSelectConfig.INSTANCE.transferVitals,
                    Set.of("Health", "AbsorptionAmount", "HurtTime", "HurtByTimestamp", "DeathTime",
                            "Fire", "Air", "foodLevel", "foodSaturationLevel", "foodExhaustionLevel",
                            "foodTickTimer")),
            new Group(() -> CharSelectConfig.INSTANCE.transferExperience,
                    Set.of("XpLevel", "XpP", "XpTotal", "XpSeed", "Score")),
            new Group(() -> CharSelectConfig.INSTANCE.transferEffects,
                    Set.of("active_effects")),
            new Group(() -> CharSelectConfig.INSTANCE.transferAttributes,
                    Set.of("attributes")),
            new Group(() -> CharSelectConfig.INSTANCE.transferRecipeBook,
                    Set.of("recipeBook")));

    private PlayerDataSplitter() {
    }

    /**
     * Files the player's saved state into the profile, sorting each key into the shared
     * bucket or this world's bucket.
     */
    public static void capture(CompoundTag full, CharacterProfile profile, String worldKey) {
        CompoundTag shared = new CompoundTag();
        CompoundTag world = new CompoundTag();

        // Work on a copy: handlers remove what they claim, and the live tag must not be
        // mutated out from under the caller.
        full = full.copy();

        // Registered handlers get first refusal, so a mod can scope its own data. Each gets a
        // private compound inside the bucket, so one handler cannot tread on another and
        // nothing it stores can be mistaken for real player NBT later.
        for (CharacterDataHandler handler : CharacterDataRegistry.handlers()) {
            CompoundTag target = CharacterDataRegistry.scopeOf(handler) == CharacterDataScope.SHARED
                    ? shared : world;
            CompoundTag mine = new CompoundTag();
            try {
                handler.capture(full, mine);
            } catch (Exception e) {
                CharSelect.LOGGER.error("Data handler {} failed while saving; its data is being "
                        + "left to the default rules", handler.id(), e);
                continue;
            }
            if (!mine.isEmpty()) {
                handlerBucket(target).put(handler.id().toString(), mine);
            }
        }

        // Attachments nobody claimed still move as one unit, following the modded-data switch.
        if (!CharSelectConfig.INSTANCE.transferModdedData.get()
                && full.contains(AttachmentDataHandler.ATTACHMENTS_KEY)) {
            world.put(AttachmentDataHandler.ATTACHMENTS_KEY,
                    full.get(AttachmentDataHandler.ATTACHMENTS_KEY).copy());
            full.remove(AttachmentDataHandler.ATTACHMENTS_KEY);
        }

        for (String key : full.getAllKeys()) {
            if (DROPPED.contains(key)) {
                continue;
            }
            Tag value = full.get(key);
            if (value == null) {
                continue;
            }
            // Anything the mod does not recognise is treated as player progress and follows
            // the character, which matches the "everything player-owned" default.
            (isWorldScoped(key) ? world : shared).put(key, value.copy());
        }

        profile.setSharedData(shared);
        WorldSlot slot = profile.worldSlot(worldKey);
        slot.setData(world);
        slot.setLastPlayed(System.currentTimeMillis());
        profile.setLastWorld(worldKey);
    }

    /**
     * Rebuilds a full player tag for this character in this world, or null if the character
     * has never stored anything and should just spawn fresh.
     */
    public static CompoundTag merge(CharacterProfile profile, String worldKey) {
        CompoundTag shared = profile.sharedData();
        WorldSlot slot = profile.peekWorldSlot(worldKey);

        // A slot can meaningfully exist holding only a remembered position and no other NBT
        // yet (a character reclaimed from a stand-in that never captured anything else in
        // this world) - checking data() alone would treat that as "nothing to load" and
        // silently discard the position along with the rest of applyPosition never running.
        boolean slotEmpty = slot == null || (slot.data().isEmpty() && !slot.hasPosition());
        if (shared.isEmpty() && slotEmpty) {
            return null;
        }

        CompoundTag merged = shared.copy();
        if (slot != null) {
            CompoundTag world = slot.data();
            for (String key : world.getAllKeys()) {
                Tag value = world.get(key);
                if (value == null) {
                    continue;
                }
                // Attachments live in one compound on both sides, so merge rather than replace,
                // or a world-local attachment would wipe every shared one.
                if (AttachmentDataHandler.ATTACHMENTS_KEY.equals(key)
                        && merged.contains(AttachmentDataHandler.ATTACHMENTS_KEY)
                        && value instanceof CompoundTag worldAttachments) {
                    CompoundTag combined = merged.getCompound(AttachmentDataHandler.ATTACHMENTS_KEY);
                    worldAttachments.getAllKeys()
                            .forEach(k -> combined.put(k, worldAttachments.get(k).copy()));
                    continue;
                }
                // A world slot can still be holding a key that the config now keeps with the
                // character instead - left behind by an older config, or by an older build that
                // sorted it differently. Letting that shadow the shared copy is how a character
                // ends up with a separate, frozen inventory per world: whatever it picks up
                // anywhere else is filed into the shared bucket and then overwritten right back
                // out on every load, so singleplayer and a server can never see each other's.
                // Capture rewrites the slot without the stale key, so this self-heals on save.
                if (!isWorldScoped(key)
                        && !HANDLER_BUCKET.equals(key)
                        && !AttachmentDataHandler.ATTACHMENTS_KEY.equals(key)
                        && merged.contains(key)) {
                    CharSelect.LOGGER.warn("Ignoring a stale world-local '{}' for '{}' in {} - "
                            + "that data follows the character now", key, profile.nickname(), worldKey);
                    continue;
                }
                merged.put(key, value.copy());
            }
            if (!CharSelectConfig.INSTANCE.rememberPositionPerWorld.get()) {
                // Drop where the character stood so the world spawns it at its own spawn point.
                POSITIONAL.forEach(merged::remove);
            }
        }
        // Handlers put their own data back, from whichever bucket they were filed into.
        CompoundTag worldData = slot == null ? new CompoundTag() : slot.data();
        for (CharacterDataHandler handler : CharacterDataRegistry.handlers()) {
            CompoundTag source = CharacterDataRegistry.scopeOf(handler) == CharacterDataScope.SHARED
                    ? shared : worldData;
            CompoundTag mine = source.getCompound(HANDLER_BUCKET).getCompound(handler.id().toString());
            try {
                handler.restore(mine, merged);
            } catch (Exception e) {
                CharSelect.LOGGER.error("Data handler {} failed while loading; that data is being "
                        + "left as the world had it", handler.id(), e);
            }
        }

        // Bookkeeping, not player data - the player must never be loaded from it.
        merged.remove(HANDLER_BUCKET);
        DROPPED.forEach(merged::remove);
        return merged;
    }

    /**
     * Every NBT key any of the transfer categories above might place in shared/world-slot
     * data, plus modded attachments. Used by the dedicated-server join upload to strip a
     * character down to identity-only when the {@code itemsTransfer} gamerule is off -
     * deriving this from {@link #GROUPS} directly means a new transfer category never needs
     * a second, easily-forgotten update here.
     */
    public static Set<String> itemBearingKeys() {
        Set<String> keys = new java.util.HashSet<>();
        GROUPS.forEach(group -> keys.addAll(group.keys()));
        keys.add(AttachmentDataHandler.ATTACHMENTS_KEY);
        return keys;
    }

    private static CompoundTag handlerBucket(CompoundTag bucket) {
        if (!bucket.contains(HANDLER_BUCKET)) {
            bucket.put(HANDLER_BUCKET, new CompoundTag());
        }
        return bucket.getCompound(HANDLER_BUCKET);
    }

    private static boolean isWorldScoped(String key) {
        if (POSITIONAL.contains(key)) {
            return true;
        }
        for (Group group : GROUPS) {
            if (group.keys().contains(key)) {
                return !group.transfers();
            }
        }
        return false;
    }
}
