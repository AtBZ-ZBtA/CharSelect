package com.charselect.client;

import com.charselect.CharSelect;
import com.charselect.character.CharacterGameMode;
import com.charselect.character.CharacterProfile;
import com.charselect.character.CharacterStore;
import com.charselect.character.SkinRef;
import com.charselect.config.CharSelectConfig;
import net.minecraft.client.Minecraft;

/**
 * Makes the mod's first launch on an established installation feel like nothing happened.
 *
 * <p>Dropping this mod into a modpack someone already plays would otherwise greet them with
 * an empty character list and, behind it, worlds they appear to have lost everything in. So
 * on the first run a character is made for them under their own Minecraft name, wearing
 * their own skin, and it inherits their existing worlds the moment they walk back in.
 */
public final class LegacyCharacterImport {

    private static boolean checked;

    private LegacyCharacterImport() {
    }

    /** Called when the character list is first opened. Does nothing after the first time. */
    public static void ensureStarterCharacter() {
        if (checked) {
            return;
        }
        checked = true;

        if (!CharSelectConfig.INSTANCE.adoptExistingWorlds.get()) {
            return;
        }
        CharacterStore store = CharacterStore.get();
        if (store.count() > 0) {
            // Already using the mod; nothing to migrate.
            return;
        }

        String name = accountName();
        CharacterProfile profile = store.create(name, CharacterGameMode.SURVIVAL, SkinRef.ACCOUNT);
        CharSelect.LOGGER.info("First run: created the starter character '{}' from this account, "
                + "which will pick up any worlds played before the mod was installed", name);
    }

    private static String accountName() {
        Minecraft minecraft = Minecraft.getInstance();
        String name = minecraft.getUser() == null ? "" : minecraft.getUser().getName();
        if (name == null || name.isBlank()) {
            return "Player";
        }
        return name.length() > CharacterProfile.MAX_NICKNAME_LENGTH
                ? name.substring(0, CharacterProfile.MAX_NICKNAME_LENGTH)
                : name;
    }
}
