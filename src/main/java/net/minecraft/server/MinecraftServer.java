package net.minecraft.server;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

// CraftBukkit start
import java.io.PrintStream;
import java.net.UnknownHostException;
import jline.ConsoleReader;
import joptsimple.OptionSet;
import org.bukkit.World.Environment;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.LoggerOutputStream;
import org.bukkit.craftbukkit.command.ColouredConsoleSender;
import org.bukkit.craftbukkit.scheduler.CraftScheduler;
import org.bukkit.craftbukkit.util.ServerShutdownThread;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginLoadOrder;
// CraftBukkit end

public class MinecraftServer implements Runnable, ICommandListener {

    private int onTicktime = 0;
    private int onEntitytime = 0;

    public static Logger log = Logger.getLogger("Minecraft");
    public static HashMap trackerList = new HashMap();
    public NetworkListenThread networkListenThread;
    public PropertyManager propertyManager;
    // public WorldServer[] worldServer; // CraftBukkit - removed!
    public ServerConfigurationManager serverConfigurationManager;
    public ConsoleCommandHandler consoleCommandHandler; // CraftBukkit - made public
    private boolean isRunning = true;
    public boolean isStopped = false;
    int ticks = 0;
    public String i;
    public int j;
    private List s = new ArrayList();
    private List t = Collections.synchronizedList(new ArrayList());
    // public EntityTracker[] tracker = new EntityTracker[2]; // CraftBukkit - removed!
    public boolean onlineMode;
    public boolean spawnAnimals;
    public boolean pvpMode;
    public boolean allowFlight;
    public String p;

    // CraftBukkit start
    public List<WorldServer> worlds = new ArrayList<WorldServer>();
    public CraftServer server;
    public OptionSet options;
    public ColouredConsoleSender console;
    public ConsoleReader reader;
    public static int currentTick;
    // CraftBukkit end

    public MinecraftServer(OptionSet options) { // CraftBukkit - adds argument OptionSet
        new ThreadSleepForever(this);

        // CraftBukkit start
        this.options = options;
        try {
            this.reader = new ConsoleReader();
        } catch (IOException ex) {
            Logger.getLogger(MinecraftServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        Runtime.getRuntime().addShutdownHook(new ServerShutdownThread(this));
        // CraftBukkit end
    }

    private boolean init() throws UnknownHostException { // CraftBukkit - added throws UnknownHostException
        this.consoleCommandHandler = new ConsoleCommandHandler(this);
        ThreadCommandReader threadcommandreader = new ThreadCommandReader(this);

        threadcommandreader.setDaemon(true);
        threadcommandreader.start();
        ConsoleLogManager.init(this); // CraftBukkit

        // CraftBukkit start
        System.setOut(new PrintStream(new LoggerOutputStream(log, Level.INFO), true));
        System.setErr(new PrintStream(new LoggerOutputStream(log, Level.SEVERE), true));
        // CraftBukkit end

        log.info("Starting minecraft server version Beta 1.8.1");
        if (Runtime.getRuntime().maxMemory() / 1024L / 1024L < 512L) {
            log.warning("**** NOT ENOUGH RAM!");
            log.warning("To start the server with more ram, launch it as \"java -Xmx1024M -Xms1024M -jar minecraft_server.jar\"");
        }

        log.info("Loading properties");
        this.propertyManager = new PropertyManager(this.options); // CraftBukkit - CLI argument support
        String s = this.propertyManager.getString("server-ip", "");

        this.onlineMode = this.propertyManager.getBoolean("online-mode", true);
        this.spawnAnimals = this.propertyManager.getBoolean("spawn-animals", true);
        this.pvpMode = this.propertyManager.getBoolean("pvp", true);
        this.allowFlight = this.propertyManager.getBoolean("allow-flight", false);
        this.p = this.propertyManager.getString("motd", "A Minecraft Server");
        this.p.replace('\u00a7', '$');
        InetAddress inetaddress = null;

        if (s.length() > 0) {
            inetaddress = InetAddress.getByName(s);
        }

        int i = this.propertyManager.getInt("server-port", 25565);

        log.info("Starting Minecraft server on " + (s.length() == 0 ? "*" : s) + ":" + i);

        try {
            this.networkListenThread = new NetworkListenThread(this, inetaddress, i);
        } catch (Throwable ioexception) { // CraftBukkit - IOException -> Throwable
            log.warning("**** FAILED TO BIND TO PORT!");
            log.log(Level.WARNING, "The exception was: " + ioexception.toString());
            log.warning("Perhaps a server is already running on that port?");
            return false;
        }

        if (!this.onlineMode) {
            log.warning("**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!");
            log.warning("The server will make no attempt to authenticate usernames. Beware.");
            log.warning("While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.");
            log.warning("To change this, set \"online-mode\" to \"true\" in the server.settings file.");
        }

        this.serverConfigurationManager = new ServerConfigurationManager(this);
        // CraftBukkit - removed trackers
        long j = System.nanoTime();
        String s1 = this.propertyManager.getString("level-name", "world");
        String s2 = this.propertyManager.getString("level-seed", "");
        long k = (new Random()).nextLong();

        if (s2.length() > 0) {
            try {
                k = Long.parseLong(s2);
            } catch (NumberFormatException numberformatexception) {
                k = (long) s2.hashCode();
            }
        }

        log.info("Preparing level \"" + s1 + "\"");
        this.a(new WorldLoaderServer(new File(".")), s1, k);

        // CraftBukkit start
        long elapsed = System.nanoTime() - j;
        String time = String.format("%.3fs", elapsed / 10000000000.0D);
        log.info("Done (" + time + ")! For help, type \"help\" or \"?\"");

        if (this.propertyManager.properties.containsKey("spawn-protection")) {
            log.info("'spawn-protection' in server.properties has been moved to 'settings.spawn-radius' in bukkit.yml. I will move your config for you.");
            this.server.setSpawnRadius(this.propertyManager.getInt("spawn-protection", 16));
            this.propertyManager.properties.remove("spawn-protection");
            this.propertyManager.savePropertiesFile();
        }
        return true;
    }

    private void a(Convertable convertable, String s, long i) {
        if (convertable.isConvertable(s)) {
            log.info("Converting map!");
            convertable.convert(s, new ConvertProgressUpdater(this));
        }

        // CraftBukkit - removed world array
        int j = this.propertyManager.getInt("gamemode", 0);

        j = WorldSettings.a(j);
        log.info("Default game type: " + j);

        // CraftBukkit start (+ removed worldsettings and servernbtmanager)
        for (int k = 0; k < (this.propertyManager.getBoolean("allow-nether", true) ? 2 : 1); ++k) {
            WorldServer world;
            int dimension = k == 0 ? 0 : -1;
            String worldType = Environment.getEnvironment(dimension).toString().toLowerCase();
            String name = (dimension == 0) ? s : s + "_" + worldType;

            ChunkGenerator gen = this.server.getGenerator(name);
            WorldSettings settings = new WorldSettings(i, j, true);

            if (k == 0) {
                world = new WorldServer(this, new ServerNBTManager(new File("."), s, true), s, dimension, settings, org.bukkit.World.Environment.getEnvironment(dimension), gen); // CraftBukkit
            } else {
                String dim = "DIM-1";

                File newWorld = new File(new File(name), dim);
                File oldWorld = new File(new File(s), dim);

                if ((!newWorld.isDirectory()) && (oldWorld.isDirectory())) {
                    log.info("---- Migration of old " + worldType + " folder required ----");
                    log.info("Unfortunately due to the way that Minecraft implemented multiworld support in 1.6, Bukkit requires that you move your " + worldType + " folder to a new location in order to operate correctly.");
                    log.info("We will move this folder for you, but it will mean that you need to move it back should you wish to stop using Bukkit in the future.");
                    log.info("Attempting to move " + oldWorld + " to " + newWorld + "...");

                    if (newWorld.exists()) {
                        log.severe("A file or folder already exists at " + newWorld + "!");
                        log.info("---- Migration of old " + worldType + " folder failed ----");
                    } else if (newWorld.getParentFile().mkdirs()) {
                        if (oldWorld.renameTo(newWorld)) {
                            log.info("Success! To restore the nether in the future, simply move " + newWorld + " to " + oldWorld);
                            log.info("---- Migration of old " + worldType + " folder complete ----");
                        } else {
                            log.severe("Could not move folder " + oldWorld + " to " + newWorld + "!");
                            log.info("---- Migration of old " + worldType + " folder failed ----");
                        }
                    } else {
                        log.severe("Could not create path for " + newWorld + "!");
                        log.info("---- Migration of old " + worldType + " folder failed ----");
                    }
                }

                world = new SecondaryWorldServer(this, new ServerNBTManager(new File("."), name, true), name, dimension, settings, this.worlds.get(0), org.bukkit.World.Environment.getEnvironment(dimension), gen); // CraftBukkit
            }

            if (gen != null) {
                world.getWorld().getPopulators().addAll(gen.getDefaultPopulators(world.getWorld()));
            }

            this.server.getPluginManager().callEvent(new WorldInitEvent(world.getWorld()));

            world.tracker = new EntityTracker(this, dimension);
            world.addIWorldAccess(new WorldManager(this, world));
            world.spawnMonsters = this.propertyManager.getInt("difficulty", 1);
            world.setSpawnFlags(this.propertyManager.getBoolean("spawn-monsters", true), this.spawnAnimals);
            world.p().d(j);
            this.worlds.add(world);
            this.serverConfigurationManager.setPlayerFileData(this.worlds.toArray(new WorldServer[0]));
        }
        // CraftBukkit end

        short short1 = 196;
        long l = System.currentTimeMillis();

        // CraftBukkit start
        for (int i1 = 0; i1 < this.worlds.size(); ++i1) {
            // if (i1 == 0 || this.propertyManager.getBoolean("allow-nether", true)) {
            WorldServer worldserver = this.worlds.get(i1);
            log.info("Preparing start region for level " + i1 + " (Seed: " + worldserver.getSeed() + ")");
            if (worldserver.getWorld().getKeepSpawnInMemory()) {
                // CraftBukkit end
                ChunkCoordinates chunkcoordinates = worldserver.getSpawn();

                for (int j1 = -short1; j1 <= short1 && this.isRunning; j1 += 16) {
                    for (int k1 = -short1; k1 <= short1 && this.isRunning; k1 += 16) {
                        long l1 = System.currentTimeMillis();

                        if (l1 < l) {
                            l = l1;
                        }

                        if (l1 > l + 1000L) {
                            int i2 = (short1 * 2 + 1) * (short1 * 2 + 1);
                            int j2 = (j1 + short1) * (short1 * 2 + 1) + k1 + 1;

                            this.a("Preparing spawn area", j2 * 100 / i2);
                            l = l1;
                        }

                        worldserver.chunkProviderServer.getChunkAt(chunkcoordinates.x + j1 >> 4, chunkcoordinates.z + k1 >> 4);

                        while (worldserver.v() && this.isRunning) {
                            ;
                        }
                    }
                }
            } // CraftBukkit
        }

        // CraftBukkit start
        for (World world : this.worlds) {
            this.server.getPluginManager().callEvent(new WorldLoadEvent(world.getWorld()));
        }
        // CraftBukkit end

        this.e();
    }

    private void a(String s, int i) {
        this.i = s;
        this.j = i;
        log.info(s + ": " + i + "%");
    }

    private void e() {
        this.i = null;
        this.j = 0;

        this.server.enablePlugins(PluginLoadOrder.POSTWORLD); // CraftBukkit
    }

    void saveChunks() { // CraftBukkit - private -> default
        log.info("Saving chunks");

        // CraftBukkit start
        for (int i = 0; i < this.worlds.size(); ++i) {
            WorldServer worldserver = this.worlds.get(i);

            worldserver.save(true, (IProgressUpdate) null);
            worldserver.saveLevel();

            WorldSaveEvent event = new WorldSaveEvent(worldserver.getWorld());
            this.server.getPluginManager().callEvent(event);
        }

        WorldServer world = this.worlds.get(0);
        if (!world.canSave) {
            this.serverConfigurationManager.savePlayers();
        }
        // CraftBukkit end
    }

    public void stop() { // CraftBukkit - private -> public
        log.info("Stopping server");
        // CraftBukkit start
        if (this.server != null) {
            this.server.disablePlugins();
        }
        // CraftBukkit end

        if (this.serverConfigurationManager != null) {
            this.serverConfigurationManager.savePlayers();
        }

        // CraftBukkit start - multiworld is handled in saveChunks() already.
        WorldServer worldserver = this.worlds.get(0);

        if (worldserver != null) {
            this.saveChunks();
        }
        // CraftBukkit end
    }

    public void a() {
        this.isRunning = false;
    }

    public void run() {
        try {
            if (this.init()) {
                long i = System.currentTimeMillis();

                for (long j = 0L; this.isRunning; Thread.sleep(1L)) {
                    long k = System.currentTimeMillis();
                    long l = k - i;

                    if (l > 2000L) {
//                        log.warning("Can\'t keep up! Did the system time change, or is the server overloaded?");
                        l = 2000L;
                    }

                    if (l < 0L) {
                        log.warning("Time ran backwards! Did the system time change?");
                        l = 0L;
                    }

                    j += l;
                    i = k;
                    if (this.worlds.get(0).everyoneDeeplySleeping()) { // CraftBukkit
                        this.h();
                        j = 0L;
                    } else {
                        while (j > 50L) {
                            MinecraftServer.currentTick = (int) (System.currentTimeMillis() / 50); // CraftBukkit
                            j -= 50L;
                            this.h();
                        }
                    }
                }
            } else {
                while (this.isRunning) {
                    this.b();

                    try {
                        Thread.sleep(10L);
                    } catch (InterruptedException interruptedexception) {
                        interruptedexception.printStackTrace();
                    }
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            log.log(Level.SEVERE, "Unexpected exception", throwable);

            while (this.isRunning) {
                this.b();

                try {
                    Thread.sleep(10L);
                } catch (InterruptedException interruptedexception1) {
                    interruptedexception1.printStackTrace();
                }
            }
        } finally {
            try {
                this.stop();
                this.isStopped = true;
            } catch (Throwable throwable1) {
                throwable1.printStackTrace();
            } finally {
                System.exit(0);
            }
        }
    }

    private void h() {
        ArrayList arraylist = new ArrayList();
        Iterator iterator = trackerList.keySet().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();
            int i = ((Integer) trackerList.get(s)).intValue();

            if (i > 0) {
                trackerList.put(s, Integer.valueOf(i - 1));
            } else {
                arraylist.add(s);
            }
        }

        int j;

        for (j = 0; j < arraylist.size(); ++j) {
            trackerList.remove(arraylist.get(j));
        }

        AxisAlignedBB.a();
        Vec3D.a();
        ++this.ticks;

        ((CraftScheduler) this.server.getScheduler()).mainThreadHeartbeat(this.ticks); // CraftBukkit

        for (j = 0; j < this.worlds.size(); ++j) { // CraftBukkit
            // if (j == 0 || this.propertyManager.getBoolean("allow-nether", true)) { // CraftBukkit
                WorldServer worldserver = this.worlds.get(j); // CraftBukkit

                if (this.ticks % 20 == 0) {
                    // CraftBukkit start - only send timeupdates to the people in that world
                    for (int i = 0; i < this.serverConfigurationManager.players.size(); ++i) {
                        EntityPlayer entityplayer = (EntityPlayer) this.serverConfigurationManager.players.get(i);
                        entityplayer.netServerHandler.sendPacket(new Packet4UpdateTime(entityplayer.getPlayerTime())); // Add support for per player time
                    }
                    // CraftBukkit end
                }

                long time = System.currentTimeMillis();
                worldserver.doTick();
                onTicktime += (System.currentTimeMillis()-time);

                while (worldserver.v()) {
                    ;
                }

                time = System.currentTimeMillis();
                worldserver.cleanUp();
                onEntitytime += (System.currentTimeMillis()-time);
            }
        // } // CraftBukkit
        int loggingTick = 1200;
        String logPrefix = "[Performance Log]";
        if(this.ticks%loggingTick == 0) {
            log.log(Level.INFO, logPrefix + ((float)onTicktime/(loggingTick)) + " ms on tick() " + ((float)onEntitytime/(loggingTick)) + " ms on updateEntities(). " + Math.min(((float)1000/((float)(onTicktime+onEntitytime)/loggingTick)),20) + " TPS. " + serverConfigurationManager.players.size() + " Players. " + ((float)1000/((float)(onTicktime+onEntitytime)/loggingTick)) + " potential TPS.");
            onTicktime = 0;
            onEntitytime = 0;

            HashMap<String, Long> times = this.server.getPluginManager().getEventTime();
            Set<String> keys = times.keySet();
            long totaltime = 0;
            for (String pluginName : keys) {
                totaltime += times.get(pluginName);
            }
			float timeSummary = (float)totaltime/(loggingTick*1000*1000);

            log.log(Level.INFO, logPrefix + "callEvent() times");
            for (String pluginName : keys) {
				long time = times.get(pluginName);
				float timeOnPlugin = (float)time/(loggingTick*1000*1000);
				log.log(Level.INFO, logPrefix + pluginName + ": " + timeOnPlugin + " ms. (" + (timeOnPlugin/timeSummary)*100 + "%)");
				times.put(pluginName, 0L);
			}
        }

        this.networkListenThread.a();
        this.serverConfigurationManager.b();

        // CraftBukkit start
        for (j = 0; j < this.worlds.size(); ++j) {
            this.worlds.get(j).tracker.updatePlayers();
        }
        // CraftBukkit end

        for (j = 0; j < this.s.size(); ++j) {
            ((IUpdatePlayerListBox) this.s.get(j)).a();
        }

        try {
            this.b();
        } catch (Exception exception) {
            log.log(Level.WARNING, "Unexpected exception while parsing console command", exception);
        }
    }

    public void issueCommand(String s, ICommandListener icommandlistener) {
        this.t.add(new ServerCommand(s, icommandlistener));
    }

    public void b() {
        while (this.t.size() > 0) {
            ServerCommand servercommand = (ServerCommand) this.t.remove(0);

            // CraftBukkit start - ServerCommand for preprocessing
            ServerCommandEvent event = new ServerCommandEvent(this.console, servercommand.command);
            this.server.getPluginManager().callEvent(event);
            servercommand = new ServerCommand(event.getCommand(), servercommand.b);
            // CraftBukkit end

            // this.consoleCommandHandler.handle(servercommand); // CraftBukkit - Removed its now called in server.dispatchCommand
            this.server.dispatchCommand(this.console, servercommand); // CraftBukkit
        }
    }

    public void a(IUpdatePlayerListBox iupdateplayerlistbox) {
        this.s.add(iupdateplayerlistbox);
    }

    public static void main(final OptionSet options) { // CraftBukkit - replaces main(String args[])
        StatisticList.a();

        try {
            MinecraftServer minecraftserver = new MinecraftServer(options); // CraftBukkit - pass in the options

            // CraftBukkit - remove gui

            (new ThreadServerApplication("Server thread", minecraftserver)).start();
        } catch (Exception exception) {
            log.log(Level.SEVERE, "Failed to start the minecraft server", exception);
        }
    }

    public File a(String s) {
        return new File(s);
    }

    public void sendMessage(String s) {
        log.info(s);
    }

    public void c(String s) {
        log.warning(s);
    }

    public String getName() {
        return "CONSOLE";
    }

    public WorldServer getWorldServer(int i) {
        // CraftBukkit start
        for (WorldServer world : this.worlds) {
            if (world.dimension == i) {
                return world;
            }
        }

        return this.worlds.get(0);
        // CraftBukkit end
    }

    public EntityTracker getTracker(int i) {
        return this.getWorldServer(i).tracker; // CraftBukkit
    }

    public static boolean isRunning(MinecraftServer minecraftserver) {
        return minecraftserver.isRunning;
    }
}
