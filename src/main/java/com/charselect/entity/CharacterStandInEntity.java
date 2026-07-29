package com.charselect.entity;

import com.charselect.CharSelect;
import com.charselect.character.CharacterProfile;
import com.charselect.net.CharacterCosmetics;
import com.charselect.net.CosmeticsPayloads;
import com.charselect.server.StandInRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * What a character looks like left standing where its player last was - see the
 * {@code charactersStayBehind} gamerule. A normal registered entity, not a fake or a
 * projection: it persists exactly the way any other mob does (including across a server
 * restart) and can be punched.
 *
 * <p>Deliberately holds no items and mimics no combat behaviour - an earlier version dressed
 * it in the character's armour and dropped/held its inventory, which turned out to have more
 * edge cases (duplication risk, sync races on reclaim) than it was worth.
 *
 * <p>Killing this does not end it in place - {@link #die} replaces it outright with a
 * {@link CharacterCorpseEntity} at the same spot, carrying the same account and character id,
 * and discards this one entirely. They are genuinely separate entities, not one entity
 * switching modes: this one is always still alive and reclaimable, the corpse never is.
 */
public class CharacterStandInEntity extends AbstractCharacterMarkerEntity {

    public CharacterStandInEntity(EntityType<? extends CharacterStandInEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    /**
     * Replaces this with a permanent {@link CharacterCorpseEntity} at the same spot and
     * discards this entity outright - {@code super.die} is deliberately never called, since
     * vanilla's own death sequence (loot, the works) has nothing to do here and this needs to
     * control removal itself rather than let that schedule it.
     *
     * <p>The character this represented is not marked dead here - there is no live player
     * session for a real death to happen to yet, so nothing about drops or hardcore status can
     * be resolved. That is deferred entirely to the moment the character is next loaded, and
     * decided purely by whether the join handshake finds a corpse standing in for it - see
     * {@code net.CharacterJoinNetwork}.
     */
    @Override
    public void die(DamageSource source) {
        UUID accountId = accountId();
        UUID characterId = characterId();
        if (accountId != null && characterId != null && this.level() instanceof ServerLevel level) {
            CharacterCosmetics cosmetics = StandInRegistry.cosmeticsFor(accountId, characterId);
            if (cosmetics == null) {
                cosmetics = CharacterCosmetics.NONE;
            }
            String nickname = this.getCustomName() != null ? this.getCustomName().getString() : "";
            CharacterCorpseEntity corpse =
                    CharacterCorpseEntity.spawn(level, this.position(), accountId, characterId, nickname);
            StandInRegistry.register(accountId, characterId, corpse.getUUID(), cosmetics);
            PacketDistributor.sendToAllPlayers(new CosmeticsPayloads.Apply(corpse.getUUID(), cosmetics));
            CharSelect.LOGGER.info("'{}' was killed while left behind", nickname.isEmpty() ? characterId : nickname);
        }
        this.discard();
    }

    /** Places a stand-in for the given account's character at the given position. */
    public static CharacterStandInEntity spawn(ServerLevel level, Vec3 pos, UUID accountId,
                                               CharacterProfile profile) {
        CharacterStandInEntity entity = ModEntityTypes.CHARACTER_STAND_IN.get().create(level);
        if (entity == null) {
            throw new IllegalStateException("Could not create a character stand-in entity");
        }
        entity.setAccountId(accountId);
        entity.setCharacterId(profile.id());
        entity.setPos(pos.x, pos.y, pos.z);
        entity.setCustomName(Component.literal(profile.nickname()));
        entity.setCustomNameVisible(true);
        level.addFreshEntity(entity);
        return entity;
    }
}
