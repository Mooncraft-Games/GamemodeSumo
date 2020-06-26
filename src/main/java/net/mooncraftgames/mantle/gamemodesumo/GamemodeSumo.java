package net.mooncraftgames.mantle.gamemodesumo;

import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.PluginLogger;
import net.mooncraftgames.mantle.gamemodesumo.game.GameBehaviorSumo;
import net.mooncraftgames.mantle.gamemodesumo.kits.KitSlapper;
import net.mooncraftgames.mantle.newgamesapi.NewGamesAPI1;
import net.mooncraftgames.mantle.newgamesapi.game.GameHandler;
import net.mooncraftgames.mantle.newgamesapi.game.GameID;
import net.mooncraftgames.mantle.newgamesapi.game.GameProperties;
import net.mooncraftgames.mantle.newgamesapi.kits.KitGroup;
import net.mooncraftgames.mantle.newgamesapi.registry.GameRegistry;
import net.mooncraftgames.mantle.newgamesapi.registry.KitRegistry;

public class GamemodeSumo extends PluginBase {

    public static GamemodeSumo gamemodeSumo;

    @Override
    public void onEnable() {
        if(!getServer().getPluginManager().isPluginEnabled(NewGamesAPI1.get())) return;
        gamemodeSumo = this;

        KitRegistry.get().registerKitGroup(new KitGroup("sumo", new KitSlapper()));

        GameProperties sumoProperties = new GameProperties(GameHandler.AutomaticWinPolicy.MANUAL_CALLS_ONLY)
                .setCanPlayersMoveDuringCountdown(false)
                .setCanWorldBeManipulated(false)
                .setDefaultCountdownLength(10)
                .setMinimumPlayers(4)
                .setGuidelinePlayers(6)
                .setMaximumPlayers(16);
        GameID sumoID = new GameID("sumo", "sumo", "Sumo", "Slap players off the platform in a multi-rounded gamemode! The last standing wins each round!", "sumo", new String[]{"sumo"}, 1, sumoProperties, GameBehaviorSumo.class);

        GameRegistry.get().registerGame(sumoID);
    }

    public static GamemodeSumo get(){ return gamemodeSumo; }
    public static PluginLogger getPlgLogger(){ return gamemodeSumo.getLogger(); }

}
