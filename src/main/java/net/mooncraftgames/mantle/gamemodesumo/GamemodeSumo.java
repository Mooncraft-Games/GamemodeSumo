package net.mooncraftgames.mantle.gamemodesumo;

import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.PluginLogger;
import net.mooncraftgames.mantle.newgamesapi.NewGamesAPI1;

public class GamemodeSumo extends PluginBase {

    public static GamemodeSumo gamemodeSumo;

    @Override
    public void onEnable() {
        if(!getServer().getPluginManager().isPluginEnabled(NewGamesAPI1.get())) return;
        gamemodeSumo = this;
    }

    public static GamemodeSumo get(){ return gamemodeSumo; }
    public static PluginLogger getPlgLogger(){ return gamemodeSumo.getLogger(); }

}
