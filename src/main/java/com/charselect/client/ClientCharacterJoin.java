package com.charselect.client;

import com.charselect.character.ActiveCharacter;
import com.charselect.character.CharacterProfile;
import com.charselect.character.CharacterUploadFilter;
import com.charselect.client.gui.CharacterSelectScreen;
import com.charselect.net.CharacterJoinPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The client side of the configuration-phase handshake a remote connection goes through -
 * see {@code server.net.CharacterUploadTask} for why this has to happen before the player
 * ever spawns, rather than as an ordinary play-phase packet.
 *
 * <p>Both screens shown here hold onto {@code context} across however long the player takes
 * to answer - the underlying connection listener {@code context.reply} sends through is the
 * same long-lived object for the whole connection, not something scoped to a single packet
 * handler invocation, so this is safe even if the player leaves the picker open a while.
 */
public final class ClientCharacterJoin {

    private ClientCharacterJoin() {
    }

    public static void onRequestUpload(CharacterJoinPayloads.RequestCharacterUpload payload,
                                       IPayloadContext context) {
        LegacyCharacterImport.ensureStarterCharacter();

        if (payload.promptItemsTransferPolicy()) {
            Minecraft.getInstance().setScreen(new ConfirmScreen(
                    allowItems -> {
                        context.reply(new CharacterJoinPayloads.ItemsTransferPolicyAnswer(allowItems));
                        openPicker(allowItems, context);
                    },
                    Component.translatable("charselect.join.items_transfer.title"),
                    Component.translatable("charselect.join.items_transfer.message"),
                    Component.translatable("charselect.join.items_transfer.allow"),
                    Component.translatable("charselect.join.items_transfer.deny")));
            return;
        }

        openPicker(payload.itemsTransferAllowed(), context);
    }

    /**
     * The connection is about to be torn down over a dead character (see
     * {@code net.CharacterJoinNetwork}'s upload handler) - shown immediately, rather than
     * waiting on whatever screen the ensuing disconnect leaves behind. A configuration-phase
     * disconnect has no in-game HUD to fall back to if that transition does not visibly take,
     * so the picker would otherwise just sit there looking unresponsive to every further
     * click, indistinguishable from a softlock.
     */
    public static void onUploadRefused(CharacterJoinPayloads.UploadRefused payload) {
        Minecraft.getInstance().setScreen(new DisconnectedScreen(new TitleScreen(),
                Component.translatable("disconnect.lost"),
                Component.translatable(payload.messageKey(), payload.argument())));
    }

    private static void openPicker(boolean itemsTransferAllowed, IPayloadContext context) {
        ActiveServerPolicy.set(itemsTransferAllowed);
        Minecraft.getInstance().setScreen(CharacterSelectScreen.forRemote(profile -> {
            // Set locally too, not just uploaded: this is what the *existing* cosmetics-
            // announce flow (CharSelectClientEvents.onLoggingIn, which fires later, once play
            // phase actually starts) reads to tell everyone else what this player looks like.
            // Without it, the join upload would still correctly resolve inventory/gamemode/
            // etc., but nobody else would ever see this player's chosen nickname or skin.
            ActiveCharacter.select(profile);
            CharacterProfile toSend = CharacterUploadFilter.forItemsTransfer(profile, itemsTransferAllowed);
            context.reply(new CharacterJoinPayloads.UploadCharacter(toSend.save()));
        }));
    }
}
