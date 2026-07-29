package com.charselect.client.entity;

import com.charselect.client.ClientCosmetics;
import com.charselect.client.skin.SkinTextureCache;
import com.charselect.entity.AbstractCharacterMarkerEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a character stand-in or corpse as what it is standing in for: a player. Reuses
 * vanilla's own {@link PlayerModel} rather than a custom one, and the mod's existing cosmetics
 * lookup rather than any new render-data sync - see {@code server.net.CharacterDownloadOnLeave}
 * for how the entity's own UUID ends up populated in that same lookup a normal player uses.
 * Generic over both {@code entity.CharacterStandInEntity} and
 * {@code entity.CharacterCorpseEntity}: they look identical, only their behaviour differs.
 *
 * <p>Always the wide-arm model. A slim-armed character's marker will look subtly wrong at the
 * arms until this renders two variants keyed off the resolved skin's model, the same way
 * vanilla itself picks between Steve and Alex - a cosmetic gap worth taking now rather than
 * doubling the renderer for.
 */
public class CharacterStandInRenderer<T extends AbstractCharacterMarkerEntity>
        extends LivingEntityRenderer<T, PlayerModel<T>> {

    public CharacterStandInRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        PlayerSkin skin = ClientCosmetics.skinFor(entity.getUUID());
        return skin != null ? skin.texture() : SkinTextureCache.STEVE;
    }
}
