package net.mooncraftgames.mantle.gamemodesumo.victorytracking;

import cn.nukkit.Player;
import net.mooncraftgames.mantle.newgamesapi.team.Team;

import java.util.ArrayList;
import java.util.Arrays;

public class SessionLeaderboardTeamEntry extends SessionLeaderboardEntry {

    protected ArrayList<Team> teams;

    protected SessionLeaderboardTeamEntry(int trackedScore, Team... teams) {
        super(trackedScore);
        this.teams = new ArrayList<>(Arrays.asList(teams));
    }

    public void addTeam(Team team, int score){
        if(score == getTrackedScore()) teams.add(team);
    }

    public boolean isTied(){
        return teams.size() > 1;
    }

    public Player[] getPlayers(){
        ArrayList<Player> collectedPlayers = new ArrayList<>();
        for(Team team: teams){
            collectedPlayers.addAll(team.getPlayers());
        }
        return collectedPlayers.toArray(new Player[0]);
    }
}
