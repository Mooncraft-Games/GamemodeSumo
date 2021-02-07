package net.mooncraftgames.mantle.gamemodesumo.game;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;
import cn.nukkit.scheduler.Task;
import cn.nukkit.utils.TextFormat;
import net.mooncraftgames.mantle.gamemodesumo.victorytracking.SessionLeaderboardEntry;
import net.mooncraftgames.mantle.gamemodesumo.victorytracking.SessionLeaderboardPlayerEntry;
import net.mooncraftgames.mantle.newgamesapi.Utility;
import net.mooncraftgames.mantle.newgamesapi.game.GameBehavior;
import net.mooncraftgames.mantle.newgamesapi.game.events.GamePlayerDeathEvent;
import net.mooncraftgames.mantle.newgamesapi.kits.Kit;
import net.mooncraftgames.mantle.newgamesapi.team.DeadTeam;
import net.mooncraftgames.mantle.newgamesapi.team.Team;
import net.mooncraftgames.mantle.newgamesapi.team.TeamPresets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class GameBehaviorSumo extends GameBehavior {

    protected static final int COUNTDOWN_LENGTH = 3;

    protected int maxRounds;
    protected boolean isTiebreakerEnabled;

    protected int roundNumber;
    protected AtomicInteger roundCountdownTracking;
    protected boolean isRoundActive;

    protected float knockbackConstant;
    protected float tiebreakerKnockbackConstant;

    protected String[] roundWinners;
    protected ArrayList<SessionLeaderboardEntry> sumoSessionLeaderboard;
    private HashMap<String, Player> playerLookup;
    private HashMap<Player, Kit> retainedKits;

    @Override
    public void onInitialCountdownEnd() {
        this.maxRounds = getSessionHandler().getPrimaryMapID().getIntegers().getOrDefault("rounds", 5);
        if(this.maxRounds == 0){ this.maxRounds = 5;}
        this.isTiebreakerEnabled = getSessionHandler().getPrimaryMapID().getSwitches().getOrDefault("tiebreaker_enabled", true);

        this.roundNumber = 0;
        this.roundCountdownTracking = new AtomicInteger();
        this.isRoundActive = false;

        // Vanilla value for the constant is 0.3f - Bumped to 0.6f just to be more impactful for the base game.
        this.knockbackConstant = getSessionHandler().getPrimaryMapID().getFloats().getOrDefault("knockback", 0.35f);
        this.tiebreakerKnockbackConstant = getSessionHandler().getPrimaryMapID().getFloats().getOrDefault("tiebreaker_knockback", 0.6f);

        this.roundWinners = this.isTiebreakerEnabled ? new String[this.maxRounds+1] : new String[this.maxRounds];
        this.sumoSessionLeaderboard = new ArrayList<>();
        this.playerLookup = new HashMap<>();
    }

    @Override
    public void registerGameSchedulerTasks() {
        setupLookups();
        moveAllToDead();
        getSessionHandler().getGameScheduler().registerGameTask(this::startRound, 0, 0);
    }

    @Override
    public void onPlayerLeaveGame(Player player) {
        if(isRoundActive) onHandleDeath(player);
    }

    @Override public void onGameMiscDeathEvent(GamePlayerDeathEvent event) { onHandleDeath(event.getDeathCause().getVictim());  }
    @Override public void onGameDeathByPlayer(GamePlayerDeathEvent event) { onHandleDeath(event.getDeathCause().getVictim());  }
    @Override public void onGameDeathByBlock(GamePlayerDeathEvent event) { onHandleDeath(event.getDeathCause().getVictim());  }
    @Override public void onGameDeathByEntity(GamePlayerDeathEvent event) { onHandleDeath(event.getDeathCause().getVictim()); }
    public void onHandleDeath(Player victim){
        if(deathCheck(victim)){
            endRound();
        }
    }

    public void startRound(){
        roundNumber++;
        sendRoundParagraphs();
        sendRoundNumber();
        reviveRoundPlayers();
        roundCountdownTracking.set(COUNTDOWN_LENGTH);
        getSessionHandler().getGameScheduler().registerSelfCancellableGameTask(this::countdownToRoundStart, 100, 20);
    }

    public void endRound(){
        isRoundActive = false;
        moveAllToDead();
        displayRoundWinner();
        getSessionHandler().getGameScheduler().registerGameTask(() -> {
                sumoSessionLeaderboard = computeLeaderboard();
                if (roundNumber >= maxRounds) {
                    if (sumoSessionLeaderboard.get(0).isTied() && isTiebreakerEnabled) {
                        getSessionHandler().getGameScheduler().registerGameTask(this::startRound, 20, 0);
                    } else {
                        Player[] winners = sumoSessionLeaderboard.get(0).getPlayers();
                        getSessionHandler().declareVictoryForPlayer(winners[0]);
                    }
                } else {
                    getSessionHandler().getGameScheduler().registerGameTask(this::startRound, 20, 0);
                }
        }, 20, 0);
    }

    public void activateRound(){
        Level level = getSessionHandler().getPrimaryMap();
        for(Team team: getSessionHandler().getTeams().values()){
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

    public boolean deathCheck(Player killedPlayer){
        if(isRoundActive){
            ArrayList<Player> playersAlive = new ArrayList<>();
            for(Team team: getSessionHandler().getTeams().values()){
                if(team.isActiveGameTeam()){
                    playersAlive.addAll(team.getPlayers());
                }
            }
            if(killedPlayer != null) playersAlive.remove(killedPlayer);
            if(playersAlive.size() == 1){
                Player player = playersAlive.get(0);
                roundWinners[roundNumber-1] = player.getName();
                return true;
            } else if (playersAlive.size() == 0) {
                roundWinners[roundNumber-1] = null;
                return true;
            }
        }
        return false;
    }

    public void countdownToRoundStart(Task handler){
        if(!isRoundActive) {
            int countdown = roundCountdownTracking.getAndDecrement();
            if (countdown <= 0) {
                activateRound();
                handler.cancel();
            } else {
                for (Player player : getSessionHandler().getPlayers()) {
                    player.sendTitle("" + TextFormat.DARK_AQUA + TextFormat.BOLD + countdown, TextFormat.BLUE + "Get ready to...", 4, 12, 4);
                    getSessionHandler().getPrimaryMap().addSound(player.getPosition(), Sound.NOTE_BANJO, 1f, 0.8f, player);
                }
            }
        }
    }

    public void moveAllToDead(){
        DeadTeam deadTeam = (DeadTeam) getSessionHandler().getTeams().get(TeamPresets.DEAD_TEAM_ID);
        for(Team team: getSessionHandler().getTeams().values()){
            if(team.isActiveGameTeam()){
                for(Player player: new ArrayList<>(team.getPlayers())){
                    getSessionHandler().switchPlayerToTeam(player, deadTeam, true);
                    Optional<Kit> kit = getSessionHandler().removePlayerKit(player, true);
                    kit.ifPresent(k -> retainedKits.put(player, k));
                }
            }
        }
    }

    public void reviveRoundPlayers(){
        if(roundNumber > maxRounds && isTiebreakerEnabled){
            DeadTeam deadTeam = (DeadTeam) getSessionHandler().getTeams().get(TeamPresets.DEAD_TEAM_ID);
            for(Player player: sumoSessionLeaderboard.get(0).getPlayers()){
                if(deadTeam.getPlayers().contains(player)){
                    getSessionHandler().getDeathManager().revivePlayerFromDeadTeam(player);
                    player.setImmobile(true);
                }
            }
        } else {
            Team deadTeam = getSessionHandler().getTeams().get(TeamPresets.DEAD_TEAM_ID);
            for(Player player: new ArrayList<>(deadTeam.getPlayers())){
                getSessionHandler().getDeathManager().revivePlayerFromDeadTeam(player);
                player.setImmobile(true);
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
        String[] paras = new String[roundNumber+2];
        paras[0] = TextFormat.BOLD+"Rounds: ";
        paras[1] = "";

        for(int i = 1; i <= roundNumber; i++){
            if(roundNumber == i) {
                TextFormat colourMain;
                TextFormat colourHighlight;
                String roundTag;
                if(roundNumber > maxRounds){
                    colourMain = TextFormat.GOLD;
                    colourHighlight = TextFormat.YELLOW;
                    roundTag = "TIE";
                } else {
                    colourMain = TextFormat.RED;
                    colourHighlight = TextFormat.GRAY;
                    roundTag = String.valueOf(i);
                }
                paras[i + 1] = String.format("%s%s> %s%s%s%s: %s...", colourHighlight, TextFormat.BOLD, TextFormat.RESET, colourMain, TextFormat.BOLD, roundTag, TextFormat.RESET);
            } else {
                paras[i + 1] = "" + TextFormat.RED + TextFormat.BOLD + String.format("%s: %s%s", i, TextFormat.RESET, getRoundWinnerDisplayName(i));
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
            if(id == null){
                return TextFormat.DARK_RED+"No Contest";
            }
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
                    sessionLeaderboard.add(i, getUpdatedLeaderboardEntryForID(winner, copy.get(i)));
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event){
        if(event.getEntity() instanceof Player ){
            Player player = (Player) event.getEntity();
            if(getSessionHandler().getPlayers().contains(player)){
                event.setCancelled(true);

                //I have no clue what this does. EntityLiving#attack() uses it though sooo...
                double deltaX = player.getX() - event.getDamager().getX();
                double deltaZ = player.getZ() - event.getDamager().getZ();
                double knockbackValue = (isTiebreakerEnabled && (roundNumber > maxRounds)) ? tiebreakerKnockbackConstant:knockbackConstant;
                player.knockBack(event.getDamager(), 0, deltaX, deltaZ, knockbackValue);
            }
        }
    }

}