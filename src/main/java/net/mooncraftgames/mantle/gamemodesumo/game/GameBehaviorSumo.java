package net.mooncraftgames.mantle.gamemodesumo.game;

import cn.nukkit.Player;
import cn.nukkit.entity.item.EntityFirework;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.ItemFirework;
import cn.nukkit.level.Level;
import cn.nukkit.level.ParticleEffect;
import cn.nukkit.level.Sound;
import cn.nukkit.utils.TextFormat;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.mooncraftgames.mantle.gamemodesumo.victorytracking.SessionLeaderboardEntry;
import net.mooncraftgames.mantle.gamemodesumo.victorytracking.SessionLeaderboardPlayerEntry;
import net.mooncraftgames.mantle.newgamesapi.Utility;
import net.mooncraftgames.mantle.newgamesapi.game.GameBehavior;
import net.mooncraftgames.mantle.newgamesapi.kits.Kit;
import net.mooncraftgames.mantle.newgamesapi.team.DeadTeam;
import net.mooncraftgames.mantle.newgamesapi.team.SpectatingTeam;
import net.mooncraftgames.mantle.newgamesapi.team.Team;
import net.mooncraftgames.mantle.newgamesapi.team.TeamPresets;

import java.util.ArrayList;
import java.util.HashMap;

public class GameBehaviorSumo extends GameBehavior {

    private static final int COUNTDOWN_LENGTH = 3;

    private int maxRounds;
    private boolean isTiebreakerEnabled;

    private int roundNumber;
    private int roundCountdownTracking;
    private boolean isRoundActive;

    private String[] roundWinners;
    protected ArrayList<SessionLeaderboardEntry> sumoSessionLeaderboard;
    private HashMap<String, Player> playerLookup;
    private HashMap<Player, Kit> retainedKits;

    @Override
    public void onInitialCountdownEnd() {
        maxRounds = getSessionHandler().getPrimaryMapID().getIntegers().getOrDefault("rounds", 5);
        if(maxRounds == 0){ maxRounds = 5;}
        isTiebreakerEnabled = getSessionHandler().getPrimaryMapID().getSwitches().getOrDefault("tiebreaker_enabled", true);

        roundNumber = 0;
        isRoundActive = false;

        roundWinners = isTiebreakerEnabled ? new String[maxRounds+1] : new String[maxRounds];
        sumoSessionLeaderboard = new ArrayList<>();
        playerLookup = new HashMap<>();
        setupLookups();
    }

    @Override
    public void registerGameSchedulerTasks() {
        // Small little hack to apply this as soon as the game has started.
        moveAllToDead();
        getSessionHandler().getGameScheduler().registerGameTask(this::startRound, 0, 0);

    }

    public void startRound(){
        roundNumber++;
        sendRoundParagraphs();
        sendRoundNumber();
        reviveRoundPlayers();
        resetCountdown();
        getSessionHandler().getGameScheduler().registerGameTask(this::countdownToRoundStart, 60, 20);
    }

    public void endRound(){
        isRoundActive = false;
        moveAllToDead();
        displayRoundWinner();
        sumoSessionLeaderboard = computeLeaderboard();
        if(roundNumber >= maxRounds) {
            if (sumoSessionLeaderboard.get(0).isTied() && isTiebreakerEnabled) {
                getSessionHandler().getGameScheduler().registerGameTask(this::startRound, 20, 0);
            } else {
                Player[] winners = sumoSessionLeaderboard.get(0).getPlayers();
                getSessionHandler().declareVictoryForPlayer(winners[0]);
            }
        } else {
            getSessionHandler().getGameScheduler().registerGameTask(this::startRound, 20, 0);
        }
    }

    public void activateRound(){
        Level level = getSessionHandler().getPrimaryMap();
        for(Team team: getTeams()){
            for(Player player: team.getPlayers()) {
                level.addSound(player.getPosition(), Sound.RAID_HORN, 0.25f, 1.5f, player);
                player.sendTitle(""+TextFormat.BLUE+TextFormat.BOLD+"SUMO!", " ", 4, 12, 4);
                if (team.isActiveGameTeam()) {
                    player.setImmobile(false);
                    retainedKits.get(player).applyKit(player, getSessionHandler(), true);
                }
            }
        }
        isRoundActive = true;
    }

    public void countdownToRoundStart(){
        roundCountdownTracking--;
        if(roundCountdownTracking <= 0){
            activateRound();
        } else {
            for(Player player: getSessionHandler().getPlayers()) {
                player.sendTitle("" + TextFormat.DARK_AQUA + TextFormat.BOLD + roundCountdownTracking, TextFormat.BLUE + "Get ready to...", 4, 12, 4);
                getSessionHandler().getPrimaryMap().addSound(player.getPosition(), Sound.NOTE_BANJO, 1f, 0.8f, player);
            }
        }
    }

    public void moveAllToDead(){
        DeadTeam deadTeam = (DeadTeam) getSessionHandler().getTeams().get(TeamPresets.DEAD_TEAM_ID);
        for(Team team: getSessionHandler().getTeams().values()){
            if(!(team instanceof SpectatingTeam)){
                for(Player player: new ArrayList<>(team.getPlayers())){
                    getSessionHandler().switchPlayerToTeam(player, deadTeam, true);
                }
            }
        }
    }

    public void reviveRoundPlayers(){
        //TODO: For tiebreaker, only select the tied teams.
        if(roundNumber > maxRounds && isTiebreakerEnabled){
            DeadTeam deadTeam = (DeadTeam) getSessionHandler().getTeams().get(TeamPresets.DEAD_TEAM_ID);
            for(Player player: sumoSessionLeaderboard.get(0).getPlayers()){
                if(deadTeam.getPlayers().contains(player)){
                    getSessionHandler().revivePlayerFromDeadTeam(player);
                }
            }
        } else {
            Team deadTeam = getSessionHandler().getTeams().get(TeamPresets.DEAD_TEAM_ID);
            for(Player player: deadTeam.getPlayers()){
                getSessionHandler().revivePlayerFromDeadTeam(player);
            }
        }
    }

    public void displayRoundWinner(){
        getSessionHandler().getPlayers().forEach(player -> {
            player.sendTitle(""+TextFormat.GOLD+TextFormat.BOLD+"Round Winner:", getRoundWinnerDisplayName(roundNumber), 8, 28, 4);
            getSessionHandler().getPrimaryMap().addSound(player.getPosition(), Sound.RANDOM_LEVELUP, 0.8f, 1f, player);
        });
        //TODO: Look for firework positions on map.
    }

    public void sendRoundParagraphs(){
        String[] paras = new String[maxRounds+2];
        paras[0] = TextFormat.BOLD+"Rounds: ";
        paras[1] = "";
        if(isTiebreakerEnabled && (roundNumber == maxRounds+1)){
            for(int i = 1; i < maxRounds; i++){
                paras[i+1] = "" +TextFormat.RED + TextFormat.BOLD + String.format("%s: %s%s", i, TextFormat.RESET, getRoundWinnerDisplayName(i));
            }
            paras[maxRounds+2] = ">" +TextFormat.GOLD + TextFormat.BOLD + String.format("> %s: %s%s", "Finals", TextFormat.RESET, getRoundWinnerDisplayName(maxRounds+1));
        } else {
            for(int i = 1; i < maxRounds; i++){
                paras[i+1] = "" +TextFormat.RED + TextFormat.BOLD + String.format("%s %s: %s%s", roundNumber == i? ">":"", i, TextFormat.RESET, getRoundWinnerDisplayName(i));
            }
        }

        String finalMessage = Utility.generateUnlimitedParagraph(paras, TextFormat.DARK_RED, TextFormat.GOLD, 35);
        for(Player player: getSessionHandler().getPlayers()){
            player.sendMessage(finalMessage);
        }
    }

    public void sendRoundNumber(){
        if(roundNumber > maxRounds && isTiebreakerEnabled){
            for(Player player: getSessionHandler().getPlayers()) {
                player.sendTitle("" + TextFormat.GOLD + TextFormat.BOLD + "TIEBREAKER", TextFormat.RED + "The Final Round", 8, 24, 8);
                getSessionHandler().getPrimaryMap().addSound(player.getPosition(), Sound.RANDOM_TOTEM, 0.7f, 1, player);
            }
        } else {
            for(Player player: getSessionHandler().getPlayers()) {
                player.sendTitle("" + TextFormat.RED + TextFormat.BOLD + String.format("ROUND %s", roundNumber), " ", 8, 16, 4);
                getSessionHandler().getPrimaryMap().addSound(player.getPosition(), Sound.ARMOR_EQUIP_LEATHER, 0.8f, 1, player);
            }
        }
    }

    public String getRoundWinnerDisplayName(int round){
        int roundOffset = round-1;
        if(round > maxRounds){
            return "[Unintentional Bug. Don't mind me xoxo]";
        } else {
            String id = roundWinners[roundOffset];
            Player rw = playerLookup.get(id);
            if(rw == null){
                return TextFormat.RED+"[-] "+TextFormat.DARK_RED+TextFormat.STRIKETHROUGH+id;
            } else {
                return rw.getDisplayName();
            }
        }
    }

    public ArrayList<SessionLeaderboardEntry> computeLeaderboard(){
        HashMap<String, Integer> tally = new HashMap<>();
        for(String winner: roundWinners){
            int originalValue = tally.getOrDefault(winner, 0);
            tally.put(winner, originalValue+1);
        }
        ArrayList<SessionLeaderboardEntry> sessionLeaderboard = new ArrayList<>();
        for(String winner: tally.keySet()){
            ArrayList<SessionLeaderboardEntry> copy = new ArrayList<>(sessionLeaderboard);
            boolean foundPosition = false;
            for(int i = 0; i < copy.size(); i++){
                if(tally.get(winner) == copy.get(i).getTrackedScore()){
                    sessionLeaderboard.remove(i);
                    sessionLeaderboard.add(i, getUpdatedLeaderboardEntryForID(winner, sessionLeaderboard.get(i)));
                    foundPosition = true;
                    break;
                } else if(tally.get(winner) > copy.get(i).getTrackedScore()){
                    sessionLeaderboard.add(i, getNewLeaderboardEntryForID(winner, tally.get(winner)));
                    foundPosition = true;
                    break;
                }
            }
            if(!foundPosition){
                sessionLeaderboard.add(getNewLeaderboardEntryForID(winner, tally.get(winner)));
            }
        }
        return sessionLeaderboard;
    }

    protected SessionLeaderboardEntry getUpdatedLeaderboardEntryForID(String id, SessionLeaderboardEntry lastEntry){
        Player player = playerLookup.get(id);
        SessionLeaderboardPlayerEntry entry = (SessionLeaderboardPlayerEntry) lastEntry;
        entry.addPlayer(player, entry.getTrackedScore());
        return lastEntry;
    }

    protected SessionLeaderboardEntry getNewLeaderboardEntryForID(String id, int score){
        Player player = playerLookup.get(id);
        SessionLeaderboardPlayerEntry newEntry;
        newEntry = new SessionLeaderboardPlayerEntry(score, player);
        return newEntry;
    }

    public void setupLookups(){
        for(Player player: getSessionHandler().getPlayers()){
            playerLookup.put(player.getName(), player.getPlayer());
        }
        retainedKits = new HashMap<>(getSessionHandler().getAppliedSessionKits());
    }

    public void resetCountdown(){
        roundCountdownTracking = COUNTDOWN_LENGTH + 1;
    }


}