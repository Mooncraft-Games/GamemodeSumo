package net.mooncraftgames.mantle.gamemodesumo.victorytracking;

import cn.nukkit.Player;

import java.util.ArrayList;

public abstract class SessionLeaderboardEntry {

    private int trackedScore;

    protected SessionLeaderboardEntry (int trackedScore){
        this.trackedScore = trackedScore;
    }

    public int getTrackedScore(){
        return trackedScore;
    }

    public abstract boolean isTied();

    public abstract Player[] getPlayers();
}
