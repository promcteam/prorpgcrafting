package com.gotofinal.darkrise.crafting.gui;

import com.gotofinal.darkrise.crafting.CraftingTable;
import com.gotofinal.darkrise.crafting.ProRPGCrafting;
import com.gotofinal.darkrise.crafting.Utils;
import com.gotofinal.darkrise.crafting.cfg.BrowseConfig;
import com.gotofinal.darkrise.crafting.cfg.Cfg;
import com.gotofinal.darkrise.crafting.cfg.PConfigManager;
import com.gotofinal.darkrise.crafting.cfg.PlayerConfig;
import com.gotofinal.darkrise.crafting.gui.slot.Slot;
import com.gotofinal.darkrise.crafting.util.PlayerUtil;
import me.travja.darkrise.core.legacy.util.Vault;
import me.travja.darkrise.core.legacy.util.item.ItemBuilder;
import me.travja.darkrise.core.legacy.util.message.MessageData;
import me.travja.darkrise.core.legacy.util.message.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BrowseGUI implements Listener {

    private static HashMap<UUID, Inventory> map = new HashMap<>();

    protected final String inventoryName;
    private final Map<Integer, String> slotMap = new HashMap<>();
    private final UUID opener;

    private ArrayList<Integer> slots = new ArrayList<>();

    private ItemStack fillItem;

    public BrowseGUI(String inventoryName, Player player, ItemStack fill) {
        this.inventoryName = inventoryName;
        this.opener = player.getUniqueId();

        if (fill == null)
            fill = BrowseConfig.getBrowseFill();

        this.fillItem = fill;

        int k = -1;

        for (String row : BrowseConfig.getBrowsePattern().getPattern()) {
            for (char c : row.toCharArray()) {
                k++;
                switch (c) {
                    case '=':
                    case 'o':
                        this.slots.add(k);
                        break;
                }
            }
        }

        Bukkit.getServer().getPluginManager().registerEvents(this, ProRPGCrafting.getInstance());
    }

    public static BrowseGUI open(Player player) {
        Inventory inv = null;
        String title = ChatColor.translateAlternateColorCodes('&', BrowseConfig.getBrowseName());
        try {
            BrowseGUI gui = new BrowseGUI(title, player, null);

            inv = Bukkit.createInventory(player, BrowseConfig.getBrowsePattern().getPattern().length * 9, title);
            int i = 0;
            int k = gui.slots.get(i);

            HashMap<Character, ItemStack> specItems = BrowseConfig.getBrowsePattern().getItems();
            int slot = 0;
            for (String pat : BrowseConfig.getBrowsePattern().getPattern()) {
                for (char c : pat.toCharArray()) {
                    if (specItems.containsKey(c))
                        inv.setItem(slot, specItems.get(c));
                    slot++;
                }
            }

//            for (CraftingTable table : Cfg.getMap().values()) {
            for (String profession : BrowseConfig.getProfessions()) {
                if (i >= gui.slots.size()) break;
                CraftingTable table = Cfg.getTable(profession);
                // Removes items from the menu if the player doesn't have permission.
                if (table == null || !Utils.hasCraftingUsePermission(player, table.getName().toLowerCase()))
                    continue;

                ItemStack item = table.getIconItem() != null ? table.getIconItem().create() : ItemBuilder.newItem(Material.BEDROCK)
                        .name(ChatColor.RED + table.getName()).newLoreLine(ChatColor.RED + "Missing icon in config.").newLoreLine(ChatColor.RED + "Add 'icon: econ-item' under the profession.")
                        .build();

                inv.setItem(k, item);
                gui.slotMap.put(k, table.getName());

                k = gui.slots.get(++i);
            }

            for (int j = i; j < gui.slots.size(); j++) {
                inv.setItem(gui.slots.get(j), gui.fillItem);
            }

            player.openInventory(inv);
            map.put(player.getUniqueId(), inv);

            return gui;
        } catch (Exception e) {
            if (inv != null) {
                inv.clear();
            }
            map.remove(player.getUniqueId());
            player.closeInventory();
            throw new RuntimeException("Exception was thrown on gui open for: " + player.getName(), e);
        }
    }


    private boolean isThis(InventoryView inv, Player player) {
        return inventoryName != null && inv.getTitle().equals(ChatColor.translateAlternateColorCodes('&', this.inventoryName)) &&
                this.opener != null && this.opener.equals(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        Inventory inv = e.getView().getTopInventory();
        if (e.getRawSlot() < 0) {
            return;
        }

        if ((inv == null) || (!(e.getWhoClicked() instanceof Player)) || !this.isThis(e.getView(), (Player) e.getWhoClicked()))
            return;

        Player p = (Player) e.getWhoClicked();
        e.setCancelled(true);

        CustomGUI guiToOpen = Cfg.getGUI(this.slotMap.get(e.getRawSlot()));
        if (guiToOpen == null) return;

        String profession = guiToOpen.getName();
        PlayerConfig conf = PConfigManager.getPlayerConfig(p);

        int unlocked = conf.getUnlockedProfessions().size();
        int allowed = PlayerUtil.getPermOption(p, "crafting.limit"); //Set the max number of unlockable professions.
        int cost = BrowseConfig.getProfCost(profession);

        MessageData[] data = {
                new MessageData("profession", profession),
                new MessageData("unlocked", unlocked),
                new MessageData("limit", allowed),
                new MessageData("cost", cost),
                new MessageData("bal", Vault.getMoney(p))
        };

        if (conf.hasProfession(profession)) {
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 1f);
            MessageUtil.sendMessage("crafting.error.profAlreadyUnlocked", p, data);
            return;
        }

        if (unlocked >= allowed) {
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 1f);
            MessageUtil.sendMessage("crafting.error.limitReached", p, data);
            return;
        }

        if (cost > 0 && !Vault.canPay(p, cost)) {
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 1f);
            MessageUtil.sendMessage("crafting.error.profNoFunds", p, data);
            return;
        }

        conf.unlockProfession(profession);
        if (cost > 0)
            Vault.pay(p, cost);
        data[1] = new MessageData("unlocked", unlocked + 1);
        MessageUtil.sendMessage("crafting.unlockedProfession", p, data);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

//
//        PlayerInitialGUI.open(guiToOpen, p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        Inventory inv = e.getView().getTopInventory();
        if ((inv == null) || !(e.getWhoClicked() instanceof Player)) {
            return;
        }
        if (this.isThis(e.getView(), (Player) e.getWhoClicked())) {
            if (e.getOldCursor().getType() == Material.BARRIER)
                e.setCancelled(true);
            if (e.getRawSlots().stream().anyMatch(i -> (this.slots.contains(i)))) {
                e.setResult(Event.Result.DENY);
                return;
            }

            if (e.getNewItems().values().stream().anyMatch(i -> Slot.SPECIAL_CRAFTING_SLOT.canHoldItem(i) == null)) {
                e.setResult(Event.Result.DENY);
            }
        }
    }

    @EventHandler
    public void drop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (player.getOpenInventory() != null && isThis(player.getOpenInventory(), event.getPlayer())) {
            ItemStack stack = event.getItemDrop().getItemStack();
            if (stack.getType() == Material.BARRIER) {
                event.getItemDrop().remove();
                if (player.getOpenInventory().getCursor() == null || player.getOpenInventory().getCursor().getType() == Material.AIR)
                    player.getOpenInventory().setCursor(stack);
            }

        }
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (isThis(event.getView(), (Player) event.getPlayer()))
            map.remove(event.getPlayer().getUniqueId());
    }

    public static void closeAll() {
        for (UUID id : map.keySet()) {
            Player target = Bukkit.getPlayer(id);
            if (target != null && target.isOnline())
                target.closeInventory();
        }

        map.clear();
    }
}
