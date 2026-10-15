package com.passe;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Marca o jogador como "ativo" (usado para ignorar AFK). */
public class ActivityListener implements Listener {

    private final PassePlugin plugin;

    public ActivityListener(PassePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Location a = e.getFrom();
        Location b = e.getTo();
        if (b == null) {
            return;
        }
        if (a.getBlockX() != b.getBlockX() || a.getBlockY() != b.getBlockY() || a.getBlockZ() != b.getBlockZ()) {
            plugin.touch(e.getPlayer());
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        plugin.touch(e.getPlayer());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        plugin.touch(e.getPlayer());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        plugin.touch(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.touch(e.getPlayer());
        plugin.getData(e.getPlayer()); // garante o registro e atualiza o nome
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.forget(e.getPlayer());
    }
}
