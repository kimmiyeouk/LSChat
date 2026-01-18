package me.miyeoukman.lschat.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class AsyncLiveChatEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String platform;
    private final String sender;
    private final String message;

    public AsyncLiveChatEvent(String platform, String sender, String message) {
        super(true); // Async
        this.platform = platform;
        this.sender = sender;
        this.message = message;
    }

    public String getPlatform() { return platform; }
    public String getSender() { return sender; }
    public String getMessage() { return message; }

    @NotNull
    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
