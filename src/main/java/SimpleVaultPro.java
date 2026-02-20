package me.simplevault;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class SimpleVaultPro extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Map<Material, Integer>> vault = new HashMap<>();
    private final Map<UUID, Inventory> openVault = new HashMap<>();
    private final int RADIUS = 7;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("kho").setExecutor(this);
    }

    @Override
    public void onDisable() {
        saveData();
    }

    // ===== MỞ KHO =====
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        Inventory inv = Bukkit.createInventory(null, 54, "§6KHO CÁ NHÂN");

        if (vault.containsKey(uuid)) {
            for (Material mat : vault.get(uuid).keySet()) {
                int amount = vault.get(uuid).get(mat);
                ItemStack item = new ItemStack(mat);
                item.setAmount(Math.min(amount, 64));
                inv.addItem(item);
            }
        }

        openVault.put(uuid, inv);
        player.openInventory(inv);
        return true;
    }

    // ===== CLICK TRONG KHO =====
    @EventHandler
    public void onClick(InventoryClickEvent e) {

        if (!(e.getWhoClicked() instanceof Player)) return;

        Player player = (Player) e.getWhoClicked();
        UUID uuid = player.getUniqueId();

        if (!openVault.containsKey(uuid)) return;
        if (!e.getInventory().equals(openVault.get(uuid))) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Material mat = clicked.getType();
        int stored = vault.getOrDefault(uuid, new HashMap<>()).getOrDefault(mat, 0);

        if (stored <= 0) return;

        int withdraw = Math.min(64, stored);

        player.getInventory().addItem(new ItemStack(mat, withdraw));
        vault.get(uuid).put(mat, stored - withdraw);

        if (stored - withdraw <= 0)
            vault.get(uuid).remove(mat);

        refreshGUI(player);
        saveData();
    }

    // ===== ĐÓNG KHO (CẤT VẬT PHẨM) =====
    @EventHandler
    public void onClose(InventoryCloseEvent e) {

        if (!(e.getPlayer() instanceof Player)) return;
        Player player = (Player) e.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!openVault.containsKey(uuid)) return;
        if (!e.getInventory().equals(openVault.get(uuid))) return;

        vault.putIfAbsent(uuid, new HashMap<>());

        for (ItemStack item : e.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                Material mat = item.getType();
                int amount = item.getAmount();

                vault.get(uuid).put(mat,
                        vault.get(uuid).getOrDefault(mat, 0) + amount);
            }
        }

        openVault.remove(uuid);
        saveData();
    }

    // ===== HÚT PHẠM VI =====
    @EventHandler
    public void onMove(PlayerMoveEvent e) {

        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!vault.containsKey(uuid)) return;

        for (Item item : player.getWorld()
                .getNearbyEntities(player.getLocation(), RADIUS, RADIUS, RADIUS)
                .stream()
                .filter(en -> en instanceof Item)
                .map(en -> (Item) en)
                .toList()) {

            Material mat = item.getItemStack().getType();

            if (vault.get(uuid).containsKey(mat)) {
                int amount = item.getItemStack().getAmount();

                vault.get(uuid).put(mat,
                        vault.get(uuid).getOrDefault(mat, 0) + amount);

                item.remove();
            }
        }
    }

    // ===== REFRESH GUI =====
    private void refreshGUI(Player player) {

        UUID uuid = player.getUniqueId();
        Inventory inv = openVault.get(uuid);

        inv.clear();

        for (Material mat : vault.get(uuid).keySet()) {
            int amount = vault.get(uuid).get(mat);
            ItemStack item = new ItemStack(mat);
            item.setAmount(Math.min(amount, 64));
            inv.addItem(item);
        }
    }

    // ===== SAVE & LOAD =====
    private void saveData() {

        FileConfiguration config = getConfig();
        config.set("vault", null);

        for (UUID uuid : vault.keySet()) {
            for (Material mat : vault.get(uuid).keySet()) {
                config.set("vault." + uuid + "." + mat.name(),
                        vault.get(uuid).get(mat));
            }
        }

        saveConfig();
    }

    private void loadData() {

        FileConfiguration config = getConfig();
        if (!config.contains("vault")) return;

        for (String uuidStr : config.getConfigurationSection("vault").getKeys(false)) {

            UUID uuid = UUID.fromString(uuidStr);
            Map<Material, Integer> map = new HashMap<>();

            for (String mat : config.getConfigurationSection("vault." + uuidStr).getKeys(false)) {
                map.put(Material.valueOf(mat),
                        config.getInt("vault." + uuidStr + "." + mat));
            }

            vault.put(uuid, map);
        }
    }
}
