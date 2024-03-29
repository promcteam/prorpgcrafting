package com.gotofinal.darkrise.crafting.gui;

import com.gotofinal.darkrise.crafting.ProRPGCrafting;
import com.gotofinal.darkrise.crafting.InventoryPattern;
import com.gotofinal.darkrise.crafting.gui.slot.Slot;
import me.travja.darkrise.core.legacy.cmds.DelayedCommand;
import me.travja.darkrise.core.legacy.cmds.R;
import me.travja.darkrise.core.legacy.util.ItemUtils;
import me.travja.darkrise.core.legacy.util.message.MessageData;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class CustomGUI implements Listener {
    protected final     String             name;
    protected final     String             inventoryName;
    protected           Slot[]             slots;
    protected transient ArrayList<Integer> resultSlots  = new ArrayList<>(20);
    protected transient ArrayList<Integer> blockedSlots = new ArrayList<>(20);
    protected transient int                nextPage;
    protected transient int                prevPage;
    protected           InventoryPattern   pattern;
    protected final     InventoryPattern   defaultPattern;

    protected final LinkedHashMap<Player, PlayerCustomGUI> map = new LinkedHashMap<>(20);

    public CustomGUI(String name, String inventoryName, InventoryPattern pattern) {
        this.name = name;
        this.inventoryName = inventoryName;
        this.pattern = pattern;
        this.defaultPattern = pattern;
        mapSlots();
        Bukkit.getPluginManager().registerEvents(this, ProRPGCrafting.getInstance());
    }

    public void resetBlockedSlots(Player player, Inventory inv, int page, int totalItems, MessageData[] data) {
        resetBlockedSlots(player, inv, page, totalItems, data, false);
    }

    public void resetBlockedSlots(Player player, Inventory inv, int page, int totalItems, MessageData[] data, boolean includeBack) {
        int fullPages = totalItems / resultSlots.size();
        int rest      = totalItems % resultSlots.size();
        int pages     = (rest == 0) ? fullPages : (fullPages + 1);

        int                           k     = -1;
        HashMap<Character, ItemStack> items = pattern.getItems();

        ArrayList<Integer> leaveBlank = new ArrayList<>();
        for (String row : pattern.getPattern()) {
            for (char c : row.toCharArray()) {
                k++;
                ItemStack item = ItemUtils.replaceText(items.get(c), data);
                if (!includeBack && c == '<' && page <= 0) {
                    leaveBlank.add(k);
                    continue;
                }
                if (c == '>' && page + 1 >= pages) {
                    leaveBlank.add(k);
                    continue;
                }

                if (item != null) inv.setItem(k, item.clone());
            }
        }

        for (Integer index : leaveBlank) {
            if (inv.getSize() > index + 1)
                inv.setItem(index, inv.getItem(index + 1));
            else
                inv.setItem(index, inv.getItem(index - 1));
        }
    }

    private void mapSlots() {
        this.resultSlots.clear();
        this.slots = new Slot[pattern.getPattern().length * 9];
        int k        = -1;
        int prevPage = -1, nextPage = -1;
        for (String row : this.pattern.getPattern()) {
            for (char c : row.toCharArray()) {
                k++;
                switch (c) {
                    case '=':
                    case 'o':
                        this.slots[k] = Slot.BASE_RESULT_SLOT;
                        this.resultSlots.add(k);
                        break;
                    case '>':
                        this.slots[k] = Slot.BLOCKED_SLOT;
                        nextPage = k;
                        break;
                    case '<':
                        this.slots[k] = Slot.BLOCKED_SLOT;
                        prevPage = k;
                        break;
                    default:
                        this.slots[k] = Slot.BLOCKED_SLOT;
                        this.blockedSlots.add(k);
                        break;
                }
            }
        }
        this.nextPage = nextPage;
        this.prevPage = prevPage;
    }

    public String getName() {
        return this.name;
    }

    public String getInventoryName() {
        return this.inventoryName;
    }

    public void setPattern(InventoryPattern pattern) {
        this.pattern = pattern;
        mapSlots();
    }

    public void resetPattern() {
        this.pattern = defaultPattern;
        mapSlots();
    }

    public InventoryPattern getPattern() {
        return pattern;
    }

    public void setSlot(int i, Slot slot) {
        this.slots[i] = slot;
    }

    public Slot getSlot(int i) {
        return this.slots[i];
    }

    public PlayerCustomGUI open(Player p, PlayerCustomGUI playerCustomGUI) {
        InventoryView iv = p.getOpenInventory();
        if ((iv != null) && (iv.getTopInventory() != null)) {
            this.map.remove(p);
            p.closeInventory();
        }

        if (playerCustomGUI != null) {
            this.map.put(p, playerCustomGUI);
        }
        return playerCustomGUI;
    }

    /**
     * Executes the commands for the gui on the given character
     *
     * @param c      The character to execute commands of
     * @param player The player to use in execution
     */
    public void executeCommands(Character c, HumanEntity player) {
        Collection<DelayedCommand> patternCommands = getPattern().getCommands(c);
        if (patternCommands != null && !patternCommands.isEmpty()) {
            DelayedCommand.invoke(ProRPGCrafting.getInstance(), player, patternCommands,
                    R.r("{crafting}", getName()),
                    R.r("{inventoryName}", getInventoryName()));
        }
    }

    private boolean isThis(InventoryView inv) {
        return inventoryName != null && inv.getTitle().equals(ChatColor.translateAlternateColorCodes('&', this.inventoryName));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        Inventory inv = e.getView().getTopInventory();
        if (e.getRawSlot() < 0) {
            return;
        }
//        System.out.println("CLICK(" + e.getRawSlot() + ")..." + (e.getRawSlot() >= this.slots.length ? "NOPE" : this.slots[e.getRawSlot()]) + ", " + e.getAction() + ", " + inv + ", " + this.isThis(inv));
        if ((inv == null) || (!(e.getWhoClicked() instanceof Player)) || !this.isThis(e.getView())) {
//            System.out.println("Ugh, fail!");
            return;
        }
        Player          p               = (Player) e.getWhoClicked();
        PlayerCustomGUI playerCustomGUI = this.map.get(p);
        if (playerCustomGUI == null) {
            return;
        }
        playerCustomGUI.onClick(e);
    }

    private void reloadRecipeTask(Player p) {
        ProRPGCrafting.getInstance().runSync(() -> this.reloadRecipe(p));
    }

    private void reloadRecipe(Player p) {
        PlayerCustomGUI playerCustomGUI = this.map.get(p);
        if (playerCustomGUI == null) {
            return;
        }
        playerCustomGUI.reloadRecipes();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        Inventory inv = e.getView().getTopInventory();
        if ((inv == null) || !(e.getWhoClicked() instanceof Player)) {
            return;
        }
        if (this.isThis(e.getView())) {
            if (e.getOldCursor().getType() == Material.BARRIER)
                e.setCancelled(true);
            if (e.getRawSlots().stream().anyMatch(i -> (i < this.slots.length) && (!Objects.equals(this.slots[i], Slot.SPECIAL_CRAFTING_SLOT)))) {
                e.setResult(Result.DENY);
                return;
            }

            if (e.getNewItems().values().stream().anyMatch(i -> Slot.SPECIAL_CRAFTING_SLOT.canHoldItem(i) == null)) {
                e.setResult(Result.DENY);
            }
            this.reloadRecipeTask((Player) e.getWhoClicked());
        }
    }

    @EventHandler
    public void drop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (player.getOpenInventory() != null && isThis(player.getOpenInventory())) {
            ItemStack stack = event.getItemDrop().getItemStack();
            if (stack.getType() == Material.BARRIER) {
                event.getItemDrop().remove();
                if (player.getOpenInventory().getCursor() == null || player.getOpenInventory().getCursor().getType() == Material.AIR)
                    player.getOpenInventory().setCursor(stack);

                PlayerCustomGUI customGUI = this.map.get(player);
                if (customGUI != null) customGUI.cancel();
            }
        }
    }

    private void onClose(Player p) {
        InventoryView v = p.getOpenInventory();
        if (v != null) {
            this.onClose(p, v.getTopInventory());
        }
    }

    private void onClose(Player p, Inventory inv) {
        if (inv == null) {
            return;
        }
        Inventory pInventory = p.getInventory();
        if (this.isThis(p.getOpenInventory())) {
            for (int i = 0; i < this.slots.length; i++) {
                if (this.slots[i].equals(Slot.BLOCKED_SLOT) || this.slots[i].equals(Slot.BASE_RESULT_SLOT)) {
                    continue;
                }
                ItemStack it = inv.getItem(i);
                if (it != null) {
                    pInventory.addItem(it)
                            .values()
                            .stream()
                            .filter(Objects::nonNull)
                            .forEach(itemStack -> p.getWorld().dropItem(p.getLocation(), itemStack));
                }
            }
            PlayerCustomGUI customGUI = this.map.get(p);
            if (customGUI != null) {
                customGUI.cancel();
            }
            this.map.remove(p);
            inv.clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClose(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            return;
        }

        PlayerCustomGUI customGUI = this.map.get(e.getEntity());
        if (customGUI == null) {
            return;
        }
        customGUI.reloadRecipesTask();
    }

    @EventHandler(ignoreCancelled = true)
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player && e.getInventory() != null) {
            this.onClose((Player) e.getPlayer(), e.getInventory());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExit(PlayerQuitEvent e) {
        this.onClose(e.getPlayer());
    }

    public void closeAll() {
        new ArrayList<>(this.map.keySet()).forEach(h -> {
            this.onClose(h);
            h.closeInventory();
        });
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("name", this.name)
                .append("inventoryName", this.inventoryName)
                .append("slots", this.slots)
                .append("pattern", this.pattern)
                .toString();
    }
}
