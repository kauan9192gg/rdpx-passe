package com.passe;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Menu (bau) do passe. */
public class PasseGui implements Listener {

    private static final int MAX_NIVEIS = 45;

    private static class Holder implements InventoryHolder {
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final PassePlugin plugin;

    public PasseGui(PassePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        List<PassePlugin.Nivel> list = plugin.getNiveis();
        int count = Math.min(list.size(), MAX_NIVEIS);
        int rows = Math.max(1, (count + 8) / 9) + 1; // +1 linha para o item de info
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, rows * 9, PassePlugin.c("&6&lPasse de Jogo"));
        h.inv = inv;
        fill(inv, p);
        p.openInventory(inv);
    }

    private void fill(Inventory inv, Player p) {
        inv.clear();
        PlayerData d = plugin.getData(p);
        List<PassePlugin.Nivel> list = plugin.getNiveis();
        for (int i = 0; i < list.size() && i < MAX_NIVEIS; i++) {
            inv.setItem(i, levelItem(list.get(i), d));
        }
        inv.setItem(inv.getSize() - 5, infoItem(d, list.size()));
    }

    private ItemStack levelItem(PassePlugin.Nivel n, PlayerData d) {
        boolean claimed = d.claimed.contains(n.numero);
        boolean unlocked = d.minutes >= n.minutos;
        short color = claimed ? (short) 7 : (unlocked ? (short) 5 : (short) 14);
        ItemStack it = new ItemStack(Material.STAINED_GLASS_PANE, 1, color);
        ItemMeta m = it.getItemMeta();
        String cor = claimed ? "&7" : (unlocked ? "&a" : "&c");
        m.setDisplayName(PassePlugin.c(cor + "N\u00edvel " + n.numero));

        List<String> lore = new ArrayList<String>();
        lore.add(PassePlugin.c("&7Tempo necess\u00e1rio: &f" + PassePlugin.formatTempo(n.minutos)));
        lore.add("");
        lore.add(PassePlugin.c("&7Recompensas:"));
        for (String line : plugin.describe(n)) {
            lore.add(PassePlugin.c("&8- &f" + line));
        }
        lore.add("");
        if (claimed) {
            lore.add(PassePlugin.c("&7J\u00e1 resgatado"));
        } else if (unlocked) {
            lore.add(PassePlugin.c("&aClique para resgatar!"));
            m.addEnchant(Enchantment.DURABILITY, 1, true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(PassePlugin.c("&cFaltam &f" + PassePlugin.formatTempo(n.minutos - d.minutes)));
        }
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack infoItem(PlayerData d, int total) {
        ItemStack it = new ItemStack(Material.WATCH);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(PassePlugin.c("&6Seu tempo de jogo"));
        List<String> lore = new ArrayList<String>();
        lore.add(PassePlugin.c("&7Total: &f" + PassePlugin.formatTempo(d.minutes)));
        lore.add(PassePlugin.c("&7N\u00edveis resgatados: &f" + d.claimed.size() + "/" + total));
        lore.add("");
        lore.add(PassePlugin.c("&7Use &f/passe resgatar &7para pegar tudo de uma vez."));
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= e.getInventory().getSize()) {
            return;
        }
        List<PassePlugin.Nivel> list = plugin.getNiveis();
        if (raw >= list.size() || raw >= MAX_NIVEIS) {
            return;
        }
        plugin.tryClaim(p, list.get(raw));
        fill(e.getInventory(), p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
