package xyz.dcicu.shimmerfix.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ArmorStandUtil {

    /**
     * 获取盔甲架头盔物品的纹理值（Base64字符串）。
     * 如果头盔不是 player_head 或没有纹理，返回 null。
     *
     * @param stand 盔甲架
     * @return 纹理值，或 null
     */
    @Nullable
    public static String getHelmetTexture(ArmorStand stand) {
        if (stand == null) return null;
        ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
        if (head.isEmpty() || head.getItem() != Items.PLAYER_HEAD) {
            return null;
        }
        ResolvableProfile profile = head.get(DataComponents.PROFILE);
        if (profile == null) return null;
        GameProfile gameProfile = profile.partialProfile();
        Collection<Property> textures = gameProfile.properties().get("textures");
        if (textures.isEmpty()) return null;
        // 通常只有一个 texture 属性
        return textures.iterator().next().value();
    }

    /**
     * 判断盔甲架头盔的纹理是否与指定纹理值匹配。
     *
     * @param stand     盔甲架
     * @param texture   要比较的纹理值
     * @return 是否匹配
     */
    public static boolean hasTexture(ArmorStand stand, String texture) {
        String helmetTexture = getHelmetTexture(stand);
        return helmetTexture != null && helmetTexture.equals(texture);
    }
}