package net.mooncraftgames.mantle.gamemodesumo.victorytracking;

import cn.nukkit.Player;

import java.util.ArrayList;
import java.util.Arrays;

public class SessionLeaderboardPlayerEntry extends SessionLeaderboardEntry {

    protected ArrayList<Player> players;

    protected SessionLeaderboardPlayerEntry(int trackedScore, Player... players) {
        super(trackedScore);
        this.players = new ArrayList<>(Arrays.asList(players));
    }

    public void addPlayer(Player player, int score){
        if(score == getTrackedScore()) players.add(player);
    }

    public boolean isTied(){
        return players.size() > 1;
    }

    public Player[] getPlayers(){
        return players.toArray(new Player[0]);
    }
}
