package me.miyeoukman.lschat;

import me.miyeoukman.lschat.api.event.AsyncLiveChatEvent;
import me.miyeoukman.lschat.api.event.AsyncLiveDonationEvent;
import me.miyeoukman.lschat.command.LSChatCommand;
import me.miyeoukman.lschat.platform.LivePlatform;
import me.miyeoukman.lschat.platform.chzzk.ChzzkPlatform;
import me.miyeoukman.lschat.platform.youtube.YouTubePlatform;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class Main extends JavaPlugin implements Listener {

    private String viewTag;
    private boolean donationAlertEnabled;
    private Sound donationSound;
    private String donationFormat;
    private final List<LivePlatform> platforms = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("lschat").setExecutor(new LSChatCommand(this));

        initPlatforms();

        getLogger().info("LSChat has been enabled!");
    }
    
    public void reload() {
        // Stop existing platforms
        for (LivePlatform platform : platforms) {
            platform.stop();
        }
        platforms.clear();
        
        reloadConfig();
        loadConfigValues();
        initPlatforms();
    }

    private void initPlatforms() {
        if (getConfig().getBoolean("platforms.chzzk.enabled")) {
            String channelId = getConfig().getString("platforms.chzzk.channel-id");
            ChzzkPlatform chzzk = new ChzzkPlatform(this, channelId);
            chzzk.start();
            platforms.add(chzzk);
            getLogger().info("Chzzk platform initialized.");
        }

        /* YouTube Disabled for now
        if (getConfig().getBoolean("platforms.youtube.enabled")) {
            String videoId = getConfig().getString("platforms.youtube.video-id");
            String apiKey = getConfig().getString("platforms.youtube.api-key");
            YouTubePlatform youtube = new YouTubePlatform(this, videoId, apiKey);
            youtube.start();
            platforms.add(youtube);
            getLogger().info("YouTube platform initialized.");
        }
        */
    }

    private void loadConfigValues() {
        viewTag = getConfig().getString("settings.view-tag", "lschat_viewer");
        donationAlertEnabled = getConfig().getBoolean("settings.donations.enable-alert", true);
        String soundName = getConfig().getString("settings.donations.sound", "ENTITY_PLAYER_LEVELUP");
        if (soundName == null) soundName = "ENTITY_PLAYER_LEVELUP";
        try {
            donationSound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException | NullPointerException e) {
            donationSound = Sound.ENTITY_PLAYER_LEVELUP;
        }
        donationFormat = getConfig().getString("settings.donations.format", "&6&l[DONATION] &e{streamer} &7- &f{message} &b({amount})");
    }

    @EventHandler
    public void onAsyncChat(AsyncLiveChatEvent event) {
        String platformColor = "&8"; // Default Dark Gray
        if (event.getPlatform().equalsIgnoreCase("Chzzk")) {
            platformColor = "&a"; // Green
        } else if (event.getPlatform().equalsIgnoreCase("YouTube")) {
            platformColor = "&c"; // Red
        }

        String message = ChatColor.translateAlternateColorCodes('&', 
            String.format("%s[%s] &f%s: %s", platformColor, event.getPlatform(), event.getSender(), event.getMessage()));
        
        // Broadcast on main thread to be safe
        Bukkit.getScheduler().runTask(this, () -> broadcastToTaggedPlayers(message));
    }

    @EventHandler
    public void onAsyncDonation(AsyncLiveDonationEvent event) {
        if (!donationAlertEnabled) return;

        String formatted = donationFormat
                .replace("{streamer}", event.getSender())
                .replace("{message}", event.getMessage())
                .replace("{amount}", String.valueOf(event.getAmount()));
        
        String message = ChatColor.translateAlternateColorCodes('&', formatted);

        // Broadcast on main thread
        Bukkit.getScheduler().runTask(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getScoreboardTags().contains(viewTag)) {
                    player.sendMessage(message);
                    player.playSound(player.getLocation(), donationSound, 1.0f, 1.0f);
                }
            }
        });
    }

    private void broadcastToTaggedPlayers(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getScoreboardTags().contains(viewTag)) {
                player.sendMessage(message);
            }
        }
    }

    @Override
    public void onDisable() {
        for (LivePlatform platform : platforms) {
            platform.stop();
        }
        getLogger().info("LSChat has been disabled!");
    }
}