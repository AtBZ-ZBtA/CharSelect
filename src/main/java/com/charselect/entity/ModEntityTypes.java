package com.charselect.entity;

import com.charselect.CharSelect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types this mod adds: the character stand-in, and the corpse it leaves once killed. */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CharSelect.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CharacterStandInEntity>> CHARACTER_STAND_IN =
            ENTITY_TYPES.register("character_stand_in", () -> EntityType.Builder
                    .of(CharacterStandInEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build("character_stand_in"));

    public static final DeferredHolder<EntityType<?>, EntityType<CharacterCorpseEntity>> CHARACTER_CORPSE =
            ENTITY_TYPES.register("character_corpse", () -> EntityType.Builder
                    .of(CharacterCorpseEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build("character_corpse"));

    private ModEntityTypes() {
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(CHARACTER_STAND_IN.get(), CharacterStandInEntity.createAttributes().build());
        event.put(CHARACTER_CORPSE.get(), CharacterCorpseEntity.createAttributes().build());
    }
}
