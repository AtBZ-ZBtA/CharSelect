package com.charselect.client;

import com.charselect.CharSelect;
import com.charselect.client.entity.CharacterStandInRenderer;
import com.charselect.entity.ModEntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only registration that has to run against the mod bus, not the game bus. */
@EventBusSubscriber(modid = CharSelect.MODID, value = Dist.CLIENT)
public final class CharSelectClientSetup {

    private CharSelectClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.CHARACTER_STAND_IN.get(), CharacterStandInRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.CHARACTER_CORPSE.get(), CharacterStandInRenderer::new);
    }
}
