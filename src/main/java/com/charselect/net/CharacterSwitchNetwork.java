package com.charselect.net;

import com.charselect.CharSelect;
import com.charselect.client.ClientCharacterSwitch;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers {@link CharacterSwitchPayloads}, behind {@code /character reconnect}. */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class CharacterSwitchNetwork {

    private CharacterSwitchNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToClient(CharacterSwitchPayloads.PrepareReconnect.TYPE,
                CharacterSwitchPayloads.PrepareReconnect.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(ClientCharacterSwitch::onPrepareReconnect));
    }
}
