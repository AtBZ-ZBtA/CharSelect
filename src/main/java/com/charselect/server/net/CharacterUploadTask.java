package com.charselect.server.net;

import com.charselect.CharSelect;
import com.charselect.net.CharacterJoinPayloads;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;

import java.util.function.Consumer;

/**
 * Blocks a remote connection in the configuration phase - before it ever spawns - until the
 * client has answered which character it is using.
 *
 * <p>Only ever registered for connections that are not the integrated server's own host; see
 * {@code net.CharacterJoinNetwork#registerTask}. The task finishes when the server receives
 * either {@code UploadCharacter} back, which calls {@code IPayloadContext#finishCurrentTask}
 * itself - see that handler for why the wait can safely be as long as it takes a player to
 * pick something on screen.
 */
public final class CharacterUploadTask implements ICustomConfigurationTask {
    public static final ConfigurationTask.Type TYPE =
            new ConfigurationTask.Type(CharSelect.id("character_upload"));

    private final boolean itemsTransferAllowed;
    private final boolean promptItemsTransferPolicy;

    public CharacterUploadTask(boolean itemsTransferAllowed, boolean promptItemsTransferPolicy) {
        this.itemsTransferAllowed = itemsTransferAllowed;
        this.promptItemsTransferPolicy = promptItemsTransferPolicy;
    }

    @Override
    public void run(Consumer<CustomPacketPayload> sender) {
        sender.accept(new CharacterJoinPayloads.RequestCharacterUpload(
                itemsTransferAllowed, promptItemsTransferPolicy));
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }
}
