package me.miyeoukman.lschat.platform.youtube;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.miyeoukman.lschat.Main;
import me.miyeoukman.lschat.api.event.AsyncLiveChatEvent;
import me.miyeoukman.lschat.api.event.AsyncLiveDonationEvent;
import me.miyeoukman.lschat.platform.LivePlatform;
import me.miyeoukman.lschat.util.HttpUtil;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

public class YouTubePlatform implements LivePlatform {
    private final Main plugin;
    private final String videoId;
    private final String apiKey;
    private boolean running = false;
    private String liveChatId;
    private String nextPageToken;
    private long pollingInterval = 5000; // Default 5 seconds (YouTube recommends adaptive polling)

    public YouTubePlatform(Main plugin, String videoId, String apiKey) {
        this.plugin = plugin;
        this.videoId = videoId;
        this.apiKey = apiKey;
    }

    @Override
    public String getName() { return "YouTube"; }

    @Override
    public void start() {
        if (videoId == null || videoId.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            plugin.getLogger().warning("YouTube Video ID or API Key is missing.");
            return;
        }
        running = true;

        // Run setup asynchronously
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!fetchLiveChatId()) {
                    plugin.getLogger().warning("Failed to get YouTube Live Chat ID. Check Video ID or if the stream is live.");
                    running = false;
                    return;
                }
                
                // Start polling loop
                startPolling();
            }
        }.runTaskAsynchronously(plugin);
    }

    private boolean fetchLiveChatId() {
        try {
            String url = "https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails&id=" + videoId + "&key=" + apiKey;
            String response = HttpUtil.get(url);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            JsonArray items = json.getAsJsonArray("items");
            
            if (items.size() == 0) return false;
            
            JsonObject details = items.get(0).getAsJsonObject().getAsJsonObject("liveStreamingDetails");
            if (details != null && details.has("activeLiveChatId")) {
                liveChatId = details.get("activeLiveChatId").getAsString();
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error fetching Live Chat ID: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    private void startPolling() {
        // Initial poll
        scheduleNextPoll(0);
    }
    
    private void scheduleNextPoll(long delayMillis) {
        if (!running) return;
        
        // Convert millis to ticks (20 ticks = 1 second, so millis / 50)
        long delayTicks = Math.max(1, delayMillis / 50);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!running) return;
                try {
                    long nextInterval = pollMessages();
                    scheduleNextPoll(nextInterval);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error polling YouTube chat: " + e.getMessage());
                    // Retry after 10 seconds on error
                    scheduleNextPoll(10000);
                }
            }
        }.runTaskLaterAsynchronously(plugin, delayTicks);
    }
    
    // Returns the next polling interval in milliseconds
    private long pollMessages() throws Exception {
        String url = "https://www.googleapis.com/youtube/v3/liveChat/messages?liveChatId=" + liveChatId + "&part=snippet,authorDetails&key=" + apiKey;
        if (nextPageToken != null) {
            url += "&pageToken=" + nextPageToken;
        }

        String response = HttpUtil.get(url);
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        
        long nextInterval = 5000; // Default fallback
        if (json.has("pollingIntervalMillis")) {
             nextInterval = json.get("pollingIntervalMillis").getAsLong();
        }

        if (json.has("nextPageToken")) {
            nextPageToken = json.get("nextPageToken").getAsString();
        }

        JsonArray items = json.getAsJsonArray("items");
        if (items != null) {
            for (JsonElement item : items) {
                processMessage(item.getAsJsonObject());
            }
        }
        
        return nextInterval;
    }

    private void processMessage(JsonObject item) {
        JsonObject snippet = item.getAsJsonObject("snippet");
        JsonObject authorDetails = item.getAsJsonObject("authorDetails");
        
        String type = snippet.get("type").getAsString();
        String displayName = authorDetails.get("displayName").getAsString();
        String displayMessage = "";
        
        if (snippet.has("displayMessage")) {
            displayMessage = snippet.get("displayMessage").getAsString();
        }
        
        // Handle SuperChat and Standard Chat
        if ("superChatEvent".equals(type)) {
            JsonObject superChatDetails = snippet.getAsJsonObject("superChatDetails");
            String amount = superChatDetails.get("amountDisplayString").getAsString();
            String userComment = superChatDetails.has("userComment") ? superChatDetails.get("userComment").getAsString() : "";
            
            // Extract numeric amount (simplified)
            double amountValue = 0;
            try {
                amountValue = Double.parseDouble(amount.replaceAll("[^0-9.]", ""));
            } catch (NumberFormatException ignored) {}
            
            Bukkit.getPluginManager().callEvent(new AsyncLiveDonationEvent("YouTube", displayName, userComment, amountValue));
            
        } else if ("textMessageEvent".equals(type)) { // Standard chat
            Bukkit.getPluginManager().callEvent(new AsyncLiveChatEvent("YouTube", displayName, displayMessage));
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() { return running; }
}