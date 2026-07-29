package com.charselect;

import com.charselect.compat.EssentialCompat;
import com.charselect.config.CharSelectConfig;
import com.charselect.config.ModGameRules;
import com.charselect.entity.ModEntityTypes;
import com.charselect.server.CharacterDataRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Character Select splits player data away from world data, Terraria-style.
 *
 * <p>A character owns its nickname, skin, gamemode and (per config) everything a player
 * normally accumulates. A world owns only terrain. Picking a character happens before
 * picking a world, and a character's gamemode decides which worlds it may enter at all.
 */
@Mod(CharSelect.MODID)
public class CharSelect {
    public static final String MODID = "charselect";
    public static final Logger LOGGER = LoggerFactory.getLogger("CharacterSelect");

    public CharSelect(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CharSelectConfig.SPEC);
        // Must happen before any GameRules instance can be constructed (i.e. before any
        // world), since registration is a side effect of this class loading.
        ModGameRules.init();
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        EssentialCompat.logStatus();
        event.enqueueWork(CharacterDataRegistry::collect);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
