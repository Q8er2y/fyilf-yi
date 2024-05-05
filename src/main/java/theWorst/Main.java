package theWorst;

import arc.Core;
import arc.Events;

import arc.graphics.Color;
import arc.graphics.g2d.BitmapFont;
import arc.graphics.g2d.GlyphLayout;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.net.Administration.Config;
import mindustry.plugin.Plugin;
import mindustry.ui.Fonts;
import mindustry.world.blocks.logic.MessageBlock;
import theWorst.tools.Millis;
import theWorst.database.*;
import theWorst.helpers.Destroyable;
import theWorst.helpers.Administration;
import theWorst.helpers.Hud;
import theWorst.helpers.maps.MapManager;
import theWorst.helpers.gameChangers.Factory;
import theWorst.helpers.gameChangers.Loadout;
import theWorst.helpers.gameChangers.ShootingBooster;

import java.util.ArrayList;
import java.util.Date;

import static arc.util.Log.info;
import static mindustry.Vars.*;
import static theWorst.tools.Commands.*;

public class Main extends Plugin {
    static ArrayList<Destroyable> destroyable = new ArrayList<>();
    static InGameCommands inGameCommands = new InGameCommands();
    static GlyphLayout layout = new GlyphLayout();

    public Main() {
        new Administration();
        new Hud();
        Events.on(EventType.BlockDestroyEvent.class, e ->{
            if(Global.config.alertPrefix == null) return;
            if(e.tile == null) {
                info("Tile is null for some reason.");
                return;
            }
            if (e.tile.entity instanceof MessageBlock.MessageBlockEntity) {
                MessageBlock.MessageBlockEntity mb = (MessageBlock.MessageBlockEntity) e.tile.entity;
                if (mb.message.startsWith(Global.config.alertPrefix)) {
                    Hud.addAd("blank", 10, mb.message.replace(Global.config.alertPrefix, ""), "!scarlet", "!gray");
                }
            }
        });

        Events.on(EventType.PlayEvent.class, e->{
            float original = state.rules.respawnTime;
            float spawnBoost = .1f;
            state.rules.respawnTime = spawnBoost;
            Timer.schedule(()-> state.rules.respawnTime = original, playerGroup.size() * spawnBoost + 1f);
        });

        Events.on(EventType.WorldLoadEvent.class,e-> destroyable.forEach(Destroyable::destroy));

        Events.on(EventType.ServerLoadEvent.class, e->{
            Config.showConnectMessages.set(false);
            Ranks.loadRanks();
            Ranks.loadBuildIn();
            Global.loadConfig();
            Global.loadLimits();
            new ShootingBooster();
            Database.init();
            new MapManager();
            Bot.init();
            MapManager.cleanMaps();
        });
    }

    public static void addDestroyable(Destroyable destroyable){
        Main.destroyable.add(destroyable);
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("test", "<text...>", "", args ->{

        });
        handler.register("dbdrop","Do not touch this if you don't want to erase database.",args->{
            if(state.is(GameState.State.playing)){
                logInfo("dbdrop-refuse-because-playing");
                return;
            }
            Database.clear();
            logInfo("dbdrop-erased");
        });

        handler.removeCommand("reloadmaps");
        handler.register("reloadmaps", "Reload all maps from disk.", arg -> {
            int beforeMaps = maps.all().size;
            maps.reload();
            MapManager.cleanMaps();
            if(maps.all().size > beforeMaps){
                info("&lc{0}&ly new map(s) found and reloaded.", maps.all().size - beforeMaps);
            }else{
                info("&lyMaps reloaded.");
            }
        });

        handler.register("dbbackup","<add/remove/load/show> [idx]",
                "Shows backups and their indexes or adds, removes or loads the backup by index.",args->{
            if(args.length==1){
                switch (args[0]){
                    case "show":
                        int idx = 0;
                        logInfo("backup");
                        for(String s : BackupManager.getIndexedBackups()){
                            Log.info(String.format("%d : %s", idx, new Date(Long.parseLong(s)).toString()));
                        }
                        return;
                    case "add":
                        BackupManager.addBackup();
                        logInfo("backup-add");
                        return;
                }
            } else {
                if(!Strings.canParsePostiveInt(args[1])){
                    logInfo("refuse-because-not-integer","3");
                    return;
                }
                switch (args[0]){
                    case "load":
                        if(BackupManager.loadBackup(Integer.parseInt(args[1]))){
                            logInfo("backup-load",args[1]);
                        } else {
                            logInfo("backup-out-of-range");
                        }
                        return;
                    case "remove":
                        if(BackupManager.removeBackup(Integer.parseInt(args[1]))){
                            logInfo("backup-remove",args[1]);
                        } else {
                            logInfo("backup-out-of-range");
                        }
                        return;
                }
            }
            logInfo("invalid-mode");
        });

        handler.register("unkick", "<ID/uuid>", "Erases kick status of player player.", args -> {
            Doc pd = Database.findData(args[0]);
            if (pd == null) {
                logInfo("player-not-found");
                return;
            }
            netServer.admins.getInfo(pd.getUuid()).lastKicked = Millis.now();
            logInfo("unkick",pd.getName());
        });

        handler.register("mapstats","Shows all maps with statistics.",args-> Log.info(MapManager.statistics()));

        handler.register("wconfig","<target/help>", "Loads the targeted config.", args -> {
            switch (args[0]){
                case "help":
                    logInfo("show-modes","ranks, pets, general, limits, discord, discordrolerestrict, loadout, factory, weapons");
                    return;
                case "ranks":
                    Ranks.loadRanks();
                    return;
                case "pets":
                    ShootingBooster.loadPets();
                    return;
                case "general":
                    Global.loadConfig();
                    Database.reconnect();
                    return;
                case "limits":
                    Global.loadLimits();
                    return;
                case "discord":
                    Bot.connect();
                    return;
                case "discordrolerestrict":
                    Bot.loadRestrictions();
                    return;
                case "loadout":
                    Loadout.loadConfig();
                    return;
                case "factory":
                    Factory.loadConfig();
                    return;
                case "weapons":
                    ShootingBooster.loadWeapons();
                    return;
                default:
                    logInfo("invalid-mode");
            }
        });

        handler.register("wload","<target/help>", "Reloads the save file.", args -> {
            switch (args[0]){
                case "help":
                    logInfo("show-modes","subnet,loadout,factory");
                    return;
                case "factory":
                    InGameCommands.factory.loadUnits();
                    return;
                case "subnet":
                    Database.loadSubnet(Database.subnet);
                    return;
                case "vpn":
                    Database.loadSubnet(Database.vpn);
                    return;
                case "loadout":
                    InGameCommands.loadout.loadRes();
                    return;
                default:
                    logInfo("invalid-mode");
            }
        });

        handler.register("setrank", "<uuid/name/id> <rank/restart> [reason...]",
                "Sets rank of the player.", args -> {
            switch (setRank(null,args[0],args[1],args.length==3 ? args[2] : null)){
                case notFound:
                    logInfo("player-not-found");
                    break;
                case invalid:
                    logInfo("rank-not-found");
                    logInfo("rank-s", Ranks.buildIn.keySet().toString());
                    logInfo("rank-s-custom", Ranks.special.keySet().toString());
            }
        });

        handler.register("emergency","<time/permanent/stop>","Emergency control.",args->{
            switch (setEmergency(args)) {
                case success:
                    logInfo("emergency-started");
                    break;
                case stopSuccess:
                    logInfo("emergency-stopped");
                    break;
                case invalid:
                    logInfo("emergency-ongoing");
                    break;
                case invalidStop:
                    logInfo("emergency-cannot-stop");
                    break;
                case invalidNotInteger:
                    logInfo("refuse-not-integer","1");
                    break;
                case permanentSuccess:
                    logInfo("emergency-permanent-started");
            }
        });

        handler.removeCommand("exit");
        handler.register("exit", "Exit the server application.", arg -> {
            info("Shutting down server.");
            net.dispose();
            Bot.disconnect();
            Core.app.exit();
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        //just to split it a little 
        inGameCommands.register(handler);
    }
}
