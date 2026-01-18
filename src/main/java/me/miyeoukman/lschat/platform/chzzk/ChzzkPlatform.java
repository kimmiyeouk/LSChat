package me.miyeoukman.lschat.platform.chzzk;

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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public class ChzzkPlatform extends WebSocketListener implements LivePlatform {
    private final Main plugin;
    private final String channelId;
    private boolean running = false;
    private WebSocket webSocket;
    private final OkHttpClient client;
    private final Gson gson = new Gson();

    private String chatChannelId;
    private String accessToken;
    private String extraToken; // Not always needed but good to have context

    public ChzzkPlatform(Main plugin, String channelId) {
        this.plugin = plugin;
        this.channelId = channelId;
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // Keep-alive
                .pingInterval(20, TimeUnit.SECONDS) // Auto ping
                .build();
    }

    @Override
    public String getName() { return "Chzzk"; }

    @Override
    public void start() {
        if (channelId == null || channelId.isEmpty()) return;
        running = true;

        // Run connection logic asynchronously
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::connect);
    }

    private void connect() {
        try {
            // 1. Get Chat Channel ID
            String channelResponse;
            try {
                // Try live-detail first
                String channelUrl = "https://api.chzzk.naver.com/service/v1/channels/" + channelId + "/live-detail";
                channelResponse = HttpUtil.get(channelUrl);
            } catch (Exception e) {
                // If live-detail fails (e.g. 500 error), try live-status
                plugin.getLogger().warning("live-detail API failed (" + e.getMessage() + "), trying live-status API...");
                String fallbackUrl = "https://api.chzzk.naver.com/polling/v2/channels/" + channelId + "/live-status";
                channelResponse = HttpUtil.get(fallbackUrl);
            }
            
            JsonObject channelJson = JsonParser.parseString(channelResponse).getAsJsonObject();
            if (!channelJson.has("content") || channelJson.get("content").isJsonNull()) {
                plugin.getLogger().warning("Invalid Chzzk Channel ID or Channel Not Found. Response: " + channelResponse);
                return;
            }
            
            JsonObject contentJson = channelJson.get("content").getAsJsonObject();
            if (!contentJson.has("chatChannelId")) {
                plugin.getLogger().warning("Chzzk ChatChannelId not found in response. Response: " + channelResponse);
                return;
            }
            
            chatChannelId = contentJson.get("chatChannelId").getAsString();

            // 2. Get Access Token
            String tokenUrl = "https://comm-api.game.naver.com/nng_main/v1/chats/access-token?channelId=" + chatChannelId + "&chatType=STREAMING";
            String tokenResponse = HttpUtil.get(tokenUrl);
            
            JsonObject tokenJson = JsonParser.parseString(tokenResponse).getAsJsonObject();
            if (!tokenJson.has("content") || tokenJson.get("content").isJsonNull()) {
                 plugin.getLogger().warning("Failed to get Chzzk Access Token. Response: " + tokenResponse);
                 return;
            }
            
            JsonObject tokenContent = tokenJson.get("content").getAsJsonObject();
            accessToken = tokenContent.has("accessToken") ? tokenContent.get("accessToken").getAsString() : null;
            extraToken = tokenContent.has("extraToken") ? tokenContent.get("extraToken").getAsString() : "";

            if (accessToken == null) {
                plugin.getLogger().warning("Chzzk Access Token is null.");
                return;
            }

            // 3. Connect WebSocket
            // Server ID can be random, using kr-ss1 for now.
            String wsUrl = "wss://kr-ss1.chat.naver.com/chat";
            Request request = new Request.Builder().url(wsUrl).build();
            webSocket = client.newWebSocket(request, this);

        } catch (Exception e) {
            plugin.getLogger().severe("Error connecting to Chzzk: " + e.getMessage());
            e.printStackTrace();
            running = false;
        }
    }

    @Override
    public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
        plugin.getLogger().info("Connected to Chzzk WebSocket server.");
        
        // 4. Send Auth Packet (CMD 100)
        JsonObject authPacket = new JsonObject();
        authPacket.addProperty("ver", "2");
        authPacket.addProperty("cmd", 100);
        authPacket.addProperty("svcid", "game");
        authPacket.addProperty("cid", chatChannelId);

        JsonObject body = new JsonObject();
        body.add("uid", null);
        body.addProperty("devType", 2001);
        body.addProperty("accTkn", accessToken);
        body.addProperty("auth", "READ");

        authPacket.add("bdy", body);

        webSocket.send(gson.toJson(authPacket));
        
        // Start Ping Task (CMD 0) every 20 seconds
        startPingTask();
    }
    
    private void startPingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!running || webSocket == null) {
                    this.cancel();
                    return;
                }
                JsonObject ping = new JsonObject();
                ping.addProperty("ver", "2");
                ping.addProperty("cmd", 0);
                webSocket.send(gson.toJson(ping));
            }
        }.runTaskTimerAsynchronously(plugin, 400L, 400L); // 20 seconds
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (!json.has("cmd")) return;
            int cmd = json.get("cmd").getAsInt();
            
            if (cmd == 10100) {
                plugin.getLogger().info("Chzzk Authentication Successful!");
            }
            else if (cmd == 93101 || cmd == 93102) { // 93101 is standard for many live chats
                if (json.has("bdy") && json.get("bdy").isJsonArray()) {
                    JsonArray bdy = json.getAsJsonArray("bdy");
                    for (JsonElement element : bdy) {
                        processMessage(element.getAsJsonObject());
                    }
                }
            }
        } catch (Exception e) {
             // plugin.getLogger().warning("Error parsing Chzzk message: " + e.getMessage());
        }
    }
    
    private void processMessage(JsonObject messageObj) {
        // plugin.getLogger().info("Raw Msg: " + messageObj.toString()); 
        
        if (!messageObj.has("profile") || messageObj.get("profile").isJsonNull()) {
            return; 
        }

        String nickname = "Unknown";
        try {
            String profileJsonStr = messageObj.get("profile").getAsString();
            JsonObject profile = JsonParser.parseString(profileJsonStr).getAsJsonObject();
            nickname = profile.get("nickname").getAsString();
        } catch (Exception ignored) {}
        
        String msg = messageObj.has("msg") && !messageObj.get("msg").isJsonNull() ? messageObj.get("msg").getAsString() : "";
        int msgTypeCode = messageObj.has("msgTypeCode") ? messageObj.get("msgTypeCode").getAsInt() : 1;
        
        // Handle Chat (Standard=1, but we treat anything not 10 as chat if it has content or is type 1)
        if (msgTypeCode != 10) {
            // Even if message is empty (emojis only), we might want to show something or at least fire the event.
            // For now, let's fire if it's not empty or specifically type 1.
            if (!msg.isEmpty() || msgTypeCode == 1) {
                String finalMsg = msg.isEmpty() ? "(이모티콘)" : msg;
                Bukkit.getPluginManager().callEvent(new AsyncLiveChatEvent("Chzzk", nickname, finalMsg));
            }
        } else {
            // Donation (msgTypeCode == 10)
            if (messageObj.has("extras")) {
                 try {
                     String extrasStr = messageObj.get("extras").getAsString();
                     JsonObject extras = JsonParser.parseString(extrasStr).getAsJsonObject();
                     
                     if (extras.has("payAmount")) {
                         int payAmount = extras.get("payAmount").getAsInt();
                         String donationMsg = (msg == null || msg.isEmpty()) ? "치즈 후원" : msg; 
                         Bukkit.getPluginManager().callEvent(new AsyncLiveDonationEvent("Chzzk", nickname, donationMsg, payAmount));
                     }
                 } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        plugin.getLogger().info("Chzzk WebSocket closing: " + code + " / " + reason);
        webSocket.close(1000, null);
    }

    @Override
    public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
        if (running) {
             plugin.getLogger().warning("Chzzk WebSocket failure: " + t.getMessage());
             // Reconnect logic could be added here
        }
    }

    @Override
    public void stop() {
        running = false;
        if (webSocket != null) {
            webSocket.close(1000, "Plugin disabled");
        }
        client.dispatcher().executorService().shutdown();
    }

    @Override
    public boolean isRunning() { return running; }
}