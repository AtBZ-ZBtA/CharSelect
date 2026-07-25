package com.charselect.mixin.client;

import com.charselect.character.ActiveCharacter;
import com.charselect.character.CharacterGameMode;
import com.charselect.character.WorldSeparation;
import com.charselect.config.CharSelectConfig;
import com.charselect.world.PendingWorldFlags;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a new world's gamemode matched to the character creating it.
 *
 * <p>A creative character can only ever enter creative worlds, so letting it create a
 * survival one would produce a world it is immediately locked out of. Clamping at the state
 * object rather than the button catches every path that sets the value.
 */
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {

    @Shadow
    public abstract void setGameMode(WorldCreationUiState.SelectedGameMode mode);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void charselect$applyCharacterDefault(CallbackInfo ci) {
        if (!charselect$clamps()) {
            return;
        }
        ActiveCharacter.gameMode().ifPresent(mode -> {
            if (mode == CharacterGameMode.CREATIVE) {
                setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            }
        });
    }

    @ModifyVariable(method = "setGameMode", at = @At("HEAD"), argsOnly = true)
    private WorldCreationUiState.SelectedGameMode charselect$clamp(
            WorldCreationUiState.SelectedGameMode requested) {
        CharacterGameMode character = ActiveCharacter.gameMode().orElse(null);
        if (character == null || !charselect$clamps()) {
            return requested;
        }
        return switch (character) {
            // Hardcore still produces a survival world, so survival characters may pick it.
            case SURVIVAL -> requested == WorldCreationUiState.SelectedGameMode.HARDCORE
                    ? requested
                    : WorldCreationUiState.SelectedGameMode.SURVIVAL;
            case CREATIVE -> WorldCreationUiState.SelectedGameMode.CREATIVE;
        };
    }

    /**
     * Keeps cheats off while the world is being made to require clean characters, whichever
     * tab the player tries to turn them on from.
     */
    @ModifyVariable(method = "setAllowCommands", at = @At("HEAD"), argsOnly = true)
    private boolean charselect$clampCheats(boolean requested) {
        return PendingWorldFlags.banCheatedCharacters() ? false : requested;
    }

    /**
     * Only the separation levels that actually hide worlds need the new world forced to
     * match. Under WARN and OFF the player can enter anything anyway, so pinning the
     * gamemode selector would just be an unexplained restriction.
     */
    @Unique
    private static boolean charselect$clamps() {
        WorldSeparation separation = CharSelectConfig.INSTANCE.worldSeparation.get();
        return separation == WorldSeparation.STRICT
                || separation == WorldSeparation.CREATIVE_SUPERSET;
    }
}
