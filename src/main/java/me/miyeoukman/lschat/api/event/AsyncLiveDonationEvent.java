package me.miyeoukman.lschat.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class AsyncLiveDonationEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String platform;
    private final String sender;
    private final String message;
    private final double amount;

    public AsyncLiveDonationEvent(String platform, String sender, String message, double amount) {
        super(true); // Async
        this.platform = platform;
        this.sender = sender;
        this.message = message;
        this.amount = amount;
    }

    public String getPlatform() { return platform; }
    public String getSender() { return sender; }
    public String getMessage() { return message; }
    public double getAmount() { return amount; }

    @NotNull
    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
