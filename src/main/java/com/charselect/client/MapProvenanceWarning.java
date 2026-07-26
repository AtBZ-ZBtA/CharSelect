package com.charselect.client;

import com.charselect.CharSelect;
import com.charselect.character.ActiveCharacter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Warns when a held map's picture cannot show, because a character carried it into a world
 * that never generated it.
 *
 * <p>A map item only ever stores a small numeric id; the actual pixels live in the world's
 * own save data, keyed by that id. Ordinarily the two never separate, but this mod's whole
 * purpose is carrying inventory between worlds, and a map's id means nothing outside the
 * world that issued it. Vanilla already copes by rendering the map blank rather than
 * crashing - {@link MapItem#getSavedData} returns null and everything downstream treats
 * that as "nothing to draw" - so this only adds a line explaining why, using that exact
 * same check.
 *
 * <p>Not a complete fix and not trying to be one: this cannot tell a map with no data in
 * this world apart from a map whose id happens to coincide with an unrelated one this world
 * generated on its own, since ids are just per-world counters with nothing identifying
 * where they came from. That is a rarer case with no reliable fix short of tracking every
 * map's origin explicitly, which is more machinery than a wrong picture on an item tooltip
 * warrants.
 */
@EventBusSubscriber(modid = CharSelect.MODID, value = Dist.CLIENT)
public final class MapProvenanceWarning {

    private MapProvenanceWarning() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        // Only meaningful with a character actually in play - without this mod's inventory
        // carrying items between saves, a map's id and its world are never separated at all.
        if (ActiveCharacter.get().isEmpty()) {
            return;
        }
        if (!(event.getItemStack().getItem() instanceof MapItem)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        MapItemSavedData data = MapItem.getSavedData(event.getItemStack(), minecraft.level);
        if (data == null) {
            event.getToolTip().add(Component.translatable("charselect.map.wrong_world")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
