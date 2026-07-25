package com.charselect.server;

import com.charselect.CharSelect;
import com.charselect.api.AttachmentDataHandler;
import com.charselect.api.CharacterDataHandler;
import com.charselect.api.CharacterDataScope;
import com.charselect.api.RegisterCharacterDataEvent;
import com.charselect.config.CharSelectConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything other mods have declared about their own data, gathered once during startup.
 *
 * <p>The bucket a handler's data lands in is its declared default, unless the player named
 * its id in the {@code worldLocalData} config or switched modded data off entirely.
 */
public final class CharacterDataRegistry {

    private static final Map<ResourceLocation, CharacterDataHandler> HANDLERS = new LinkedHashMap<>();
    private static boolean collected;

    private CharacterDataRegistry() {
    }

    /**
     * Built-in registrations for mods worth supporting out of the box. These are declared by
     * id rather than against the mod's classes, so there is no compile-time dependency and
     * nothing to break when the mod is absent.
     */
    private static void registerBuiltIns() {
        // Curios keeps a player's accessory slots in a serializable NeoForge attachment.
        // Accessories are worn gear, so they belong to the character, like the inventory.
        add(new AttachmentDataHandler(
                ResourceLocation.fromNamespaceAndPath("curios", "inventory"),
                CharacterDataScope.SHARED));
    }

    /** Fires the registration event. Called once, after mod construction. */
    public static synchronized void collect() {
        if (collected) {
            return;
        }
        collected = true;

        registerBuiltIns();
        ModLoader.postEvent(new RegisterCharacterDataEvent(CharacterDataRegistry::add));

        if (!HANDLERS.isEmpty()) {
            CharSelect.LOGGER.info("Character data handlers registered: {}", HANDLERS.keySet());
        }
    }

    private static void add(CharacterDataHandler handler) {
        CharacterDataHandler previous = HANDLERS.put(handler.id(), handler);
        if (previous != null) {
            CharSelect.LOGGER.warn("Character data handler {} was registered twice; keeping the last",
                    handler.id());
        }
    }

    public static List<CharacterDataHandler> handlers() {
        return new ArrayList<>(HANDLERS.values());
    }

    /** Where this handler's data should actually go, after the player's config has its say. */
    public static CharacterDataScope scopeOf(CharacterDataHandler handler) {
        if (!CharSelectConfig.INSTANCE.transferModdedData.get()) {
            return CharacterDataScope.PER_WORLD;
        }
        String id = handler.id().toString();
        for (String forced : CharSelectConfig.INSTANCE.worldLocalData.get()) {
            if (id.equalsIgnoreCase(forced)) {
                return CharacterDataScope.PER_WORLD;
            }
        }
        return handler.defaultScope();
    }
}
