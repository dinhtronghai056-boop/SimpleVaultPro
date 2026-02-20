package me.simplevault;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SimpleVaultPro extends JavaPlugin implements Listener, CommandExecutor {

    private Map<UUID, Map<Material, Integer>> vault = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("kho").setExecutor(this);

        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            getDataFolder().mkdirs();
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void loadData() {
        for (String uuidStr : data.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            Map<Material, Integer> map = new HashMap<>();
            for (String mat : data.getConfigurationSection(uuidStr).getKeys(false)) {
                map.put(Material.valueOf(mat), data.getInt(uuidStr + "." + mat));
            }
            vault.put(uuid, map);
        }
    }

    private void saveData() {
        for (UUID uuid : vault.keySet()) {
            for (Material mat : vault.get(uuid).keySet()) {
                data.set(uuid.toString() + "." + mat.name(), vault.get(uuid).get(mat));
            }
        }
        try { data.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // ---------------- GUI ----------------
    private void openGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6KHO CỦA BẠN");
        Map<Material, Integer> map = vault.getOrDefault(p.getUniqueId(), new HashMap<>());

        for (Material mat : map.keySet()) {
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§eSố lượng: §a" + map.get(mat));
            item.setItemMeta(meta);
            inv.addItem(item);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals("§6KHO CỦA BẠN")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;

            Player p = (Player) e.getWhoClicked();
            Material mat = e.getCurrentItem().getType();
            Map<Material, Integer> map = vault.get(p.getUniqueId());

            if (map == null || !map.containsKey(mat)) return;

            int amount = map.get(mat);
            if (e.isShiftClick()) {
                p.getInventory().addItem(new ItemStack(mat, amount));
                map.remove(mat);
            } else {
                int give = Math.min(64, amount);
                p.getInventory().addItem(new ItemStack(mat, give));
                if (amount - give <= 0) map.remove(mat);
                else map.put(mat, amount - give);
            }
            openGUI(p);
        }
    }

    // ---------------- Right Click Store ----------------
    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (!e.getAction().toString().contains("RIGHT_CLICK")) return;
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;
        if (item.getType().name().contains("SHULKER_BOX")) {
            p.sendMessage("§cKhông thể cất Shulker!");
            return;
        }

        Map<Material, Integer> map = vault.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        map.put(item.getType(), map.getOrDefault(item.getType(), 0) + item.getAmount());
        p.getInventory().setItemInMainHand(null);
        p.sendMessage("§aĐã cất vào kho!");
    }

    // ---------------- Auto Ore ----------------
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Material type = e.getBlock().getType();

        if (!type.name().contains("ORE")) return;

        e.setDropItems(false);

        ItemStack tool = p.getInventory().getItemInMainHand();
        boolean silk = tool.containsEnchantment(Enchantment.SILK_TOUCH);
        int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);

        Material dropMat = silk ? type : type;
        int amount = 1 + new Random().nextInt(Math.max(1, fortune + 1));

        Map<Material, Integer> map = vault.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        map.put(dropMat, map.getOrDefault(dropMat, 0) + amount);
    }

    // ---------------- Command ----------------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        openGUI((Player) sender);
        return true;
    }
}
