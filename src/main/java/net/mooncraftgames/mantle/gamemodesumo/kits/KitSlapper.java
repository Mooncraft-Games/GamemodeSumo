package net.mooncraftgames.mantle.gamemodesumo.kits;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemFish;
import cn.nukkit.item.ItemHelmetDiamond;
import cn.nukkit.item.ItemStick;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.utils.TextFormat;
import net.mooncraftgames.mantle.newgamesapi.kits.Kit;

import java.util.Optional;

public class KitSlapper extends Kit {

    @Override
    public String getKitID() {
        return "slapper";
    }

    @Override
    public String getKitDisplayName() {
        return "Slapper";
    }

    @Override
    public String getKitDescription() {
        return "A basic kit, the slapper does more knockback than the others kits at the cost of no easy way to recover.";
    }

    @Override
    public Item[] getKitItems() {
        Item slapItem =  new ItemFish().setCustomName(""+TextFormat.GREEN+TextFormat.BOLD+"Ol' Slappy");
        slapItem.addEnchantment(Enchantment.get(Enchantment.ID_PROTECTION_ALL).setLevel(1));
        return new Item[]{
           slapItem
        };
    }

    @Override
    public Optional<Item> getKitHelmet() {
        return Optional.of(new ItemHelmetDiamond());
    }
}
