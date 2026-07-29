package com.charselect.entity;

import com.charselect.character.CharacterProfile;
import com.charselect.character.CharacterStore;
import com.charselect.server.RemoteCharacterStore;
import com.charselect.server.StandInRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared plumbing for the two entities that stand in for a character with nobody playing it -
 * {@link CharacterStandInEntity} while it is still alive and reclaimable, {@link
 * CharacterCorpseEntity} once it has been killed. Both need to know exactly the same thing
 * (which account, which character) and resolve it the same way, and both must survive a
 * server restart and never quietly despawn - the only real difference between them is what
 * happens next, which is why they are genuinely separate entities rather than one entity with
 * a mode flag: killing one discards it outright and spawns the other in its place, see
 * {@link CharacterStandInEntity#die}.
 */
public abstract class AbstractCharacterMarkerEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> DATA_ACCOUNT_ID =
            SynchedEntityData.defineId(AbstractCharacterMarkerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_CHARACTER_ID =
            SynchedEntityData.defineId(AbstractCharacterMarkerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final String ACCOUNT_ID_KEY = "CharSelectAccountId";
    private static final String CHARACTER_ID_KEY = "CharSelectCharacterId";

    /**
     * Not persisted - true again for every fresh load, which is exactly when this needs
     * checking: once per time this entity's chunk loads, not every tick.
     */
    private boolean checkedOrphan;

    protected AbstractCharacterMarkerEntity(EntityType<? extends AbstractCharacterMarkerEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACCOUNT_ID, Optional.empty());
        builder.define(DATA_CHARACTER_ID, Optional.empty());
    }

    @Nullable
    public UUID accountId() {
        return this.entityData.get(DATA_ACCOUNT_ID).orElse(null);
    }

    @Nullable
    public UUID characterId() {
        return this.entityData.get(DATA_CHARACTER_ID).orElse(null);
    }

    public void setAccountId(UUID accountId) {
        this.entityData.set(DATA_ACCOUNT_ID, Optional.of(accountId));
    }

    public void setCharacterId(UUID characterId) {
        this.entityData.set(DATA_CHARACTER_ID, Optional.of(characterId));
    }

    /**
     * The profile this entity represents, wherever it actually lives. Tries
     * {@link RemoteCharacterStore} first (a real dedicated-server connection, keyed by
     * account) and falls back to the local {@link CharacterStore} (the integrated server's
     * own host, keyed by the character itself) - the two never collide in practice, since a
     * given account is only ever one or the other for a given world.
     */
    @Nullable
    protected CharacterProfile resolveProfile(MinecraftServer server) {
        UUID accountId = accountId();
        UUID characterId = characterId();
        if (accountId == null || characterId == null) {
            return null;
        }
        CharacterProfile remote = RemoteCharacterStore.get(server, accountId);
        if (remote != null && remote.id().equals(characterId)) {
            return remote;
        }
        return CharacterStore.get().byId(characterId).orElse(null);
    }

    /**
     * Left standing indefinitely - vanilla's usual "wandered too far, no player nearby, time
     * to despawn" bookkeeping must never apply here, or it could quietly vanish long before
     * anyone reclaims or discovers it.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID accountId = accountId();
        if (accountId != null) {
            tag.putUUID(ACCOUNT_ID_KEY, accountId);
        }
        UUID characterId = characterId();
        if (characterId != null) {
            tag.putUUID(CHARACTER_ID_KEY, characterId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(ACCOUNT_ID_KEY)) {
            setAccountId(tag.getUUID(ACCOUNT_ID_KEY));
        }
        if (tag.hasUUID(CHARACTER_ID_KEY)) {
            setCharacterId(tag.getUUID(CHARACTER_ID_KEY));
        }
    }

    /**
     * {@link StandInRegistry} is deliberately in-memory only (see its own doc comment) and
     * does not survive a server restart, even though this entity's own NBT does. An entity
     * left standing from a previous server run is real and safe to leave alone gameplay-wise,
     * but with no registry entry it can never be found the fast way again, never receives a
     * cosmetics catch-up, and would otherwise just stand there forever as a mute, Steve-skinned
     * relic. Removed instead: whatever it represented simply resumes fresh on its next load,
     * the same as if it had never been left behind at all.
     *
     * <p>Checked once per load rather than at NBT-read time - the entity is not fully part of
     * the level yet while {@link #readAdditionalSaveData} runs, so discarding itself there is
     * riskier than waiting the one tick until it is unambiguously safe to remove.
     */
    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.checkedOrphan && !this.level().isClientSide()) {
            this.checkedOrphan = true;
            UUID accountId = accountId();
            UUID characterId = characterId();
            if (accountId != null && characterId != null
                    && !this.getUUID().equals(StandInRegistry.entityFor(accountId, characterId))) {
                this.discard();
            }
        }
    }
}
