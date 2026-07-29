package com.charselect.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A permanent, unkillable marker left exactly where a character's stand-in was killed (see
 * {@code charactersStayBehind} and {@link CharacterStandInEntity#die}) - a record that this
 * happened and exactly where, and the thing the join handshake looks for to know a character
 * owes a real death the next time it loads, standing on this same spot: see
 * {@code net.CharacterJoinNetwork}.
 *
 * <p>A genuinely separate entity from the stand-in it replaces, not the same entity switched
 * into a "dead" mode - the stand-in discards itself outright the moment one of these is
 * spawned in its place. Nothing here is ever reclaimable, and it never becomes anything else.
 */
public class CharacterCorpseEntity extends AbstractCharacterMarkerEntity {

    public CharacterCorpseEntity(EntityType<? extends CharacterCorpseEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    @Override
    protected void registerGoals() {
        // None - a corpse never acts on its own.
    }

    /** Never pushed, never pushes back - "not be able to be pushed around" per the request. */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Re-asserted every tick rather than trusted to stick from spawn() alone - nothing
        // should ever be moving this, but this is what actually guarantees it stays lying
        // down, facing one way, and rooted to the spot regardless.
        //
        // Pose.DYING alone only shrinks the hitbox - the actual lying-down *rotation* comes
        // from LivingEntityRenderer#setupRotations reading deathTime > 0 (fully applied once
        // it reaches 20), a detail easy to miss since nothing about the pose enum itself
        // mentions it. deathTime is never auto-incremented (and the entity never auto-removed
        // for it) unless isDeadOrDying() is true, which a full-health, invulnerable entity
        // never is - so setting it once and leaving it is safe and permanent.
        this.setPose(Pose.DYING);
        this.deathTime = 20;
        this.setDeltaMovement(Vec3.ZERO);
        this.setYHeadRot(this.getYRot());
        this.setYBodyRot(this.getYRot());
    }

    /** Places a corpse marker for the given account's character at the given position. */
    public static CharacterCorpseEntity spawn(ServerLevel level, Vec3 pos, UUID accountId,
                                              UUID characterId, String nickname) {
        CharacterCorpseEntity entity = ModEntityTypes.CHARACTER_CORPSE.get().create(level);
        if (entity == null) {
            throw new IllegalStateException("Could not create a character corpse entity");
        }
        entity.setAccountId(accountId);
        entity.setCharacterId(characterId);
        entity.setPos(pos.x, pos.y, pos.z);
        entity.setPose(Pose.DYING);
        entity.deathTime = 20;
        if (!nickname.isEmpty()) {
            entity.setCustomName(Component.literal(nickname).append(Component.literal(" (deceased)")));
            entity.setCustomNameVisible(true);
        }
        level.addFreshEntity(entity);
        return entity;
    }
}
