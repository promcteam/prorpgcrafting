package com.gotofinal.darkrise.crafting.gui;

import com.gotofinal.darkrise.crafting.*;
import com.gotofinal.darkrise.crafting.cfg.Cfg;
import com.gotofinal.darkrise.crafting.cfg.PConfigManager;
import com.gotofinal.darkrise.crafting.gui.slot.Slot;
import me.travja.darkrise.core.legacy.cmds.DelayedCommand;
import me.travja.darkrise.core.legacy.util.ItemUtils;
import me.travja.darkrise.core.legacy.util.Vault;
import me.travja.darkrise.core.legacy.util.message.MessageData;
import me.travja.darkrise.core.legacy.util.message.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class PlayerCustomGUI implements Listener {
    private final CustomGUI                          gui;
    private final Player                             player;
    private final Inventory                          inventory;
    private final Category                           category;
    private final HashMap<Integer, CalculatedRecipe> recipes;
    private       int                                page   = 0;
    private       BukkitTask                         craftingTask;
    private       BukkitTask                         barTask;
    private       BossBar                            bar;
    private       Collection<ItemStack>              refund = new ArrayList<>();
    private       ItemStack                          previousCursor;

    private boolean craftingSuccess = true;
    private Recipe  craftingRecipe  = null;

    public PlayerCustomGUI(CustomGUI gui, Player player, Inventory inventory, Category category) {
        this.gui = gui;
        this.player = player;
        this.inventory = inventory;
        this.recipes = new HashMap<>(20);
        this.category = category;
    }

    public static Collection<ItemStack> getPlayerItems(InventoryHolder player) {
        ItemStack[]     contents = ItemUtils.compact(false, player.getInventory().getContents());
        List<ItemStack> result   = new ArrayList<>(contents.length);
        for (ItemStack content : contents) {
            if (content == null) {
                continue;
            }
            result.add(content);
        }
        return result;
    }

    public static PlayerCustomGUI open(CustomGUI gui, Player player, Category category) {
        if (category.getPattern() != null)
            gui.setPattern(category.getPattern());
        else
            gui.resetPattern();
        Inventory inv = null;
        try {
            inv = Bukkit.createInventory(player, gui.slots.length, ChatColor.translateAlternateColorCodes('&', gui.inventoryName));
            gui.resetBlockedSlots(player, inv, 0, category.getRecipes().size(),
                    new MessageData[]{
                            new MessageData("level", LevelFunction.getLevel(player, Cfg.getTable(gui.name))),
                            new MessageData("category", category),
                            new MessageData("gui", gui.getName()),
                            new MessageData("player", player.getName()),
                            new MessageData("bal", Vault.getMoney(player))
                    });
//            int k = -1;
//            HashMap<Character, ItemStack> items = gui.pattern.getItems();
//            for (String row : gui.pattern.getPattern()) {
//                for (char c : row.toCharArray()) {
//                    k++;
//                    ItemStack item = ItemUtils.replaceText(items.get(c),
//                            new MessageData("level", LevelFunction.getLevel(player, Cfg.getTable(gui.name))),
//                            new MessageData("category", category),
//                            new MessageData("gui", gui.getName()),
//                            new MessageData("player", player.getName()));
//                    if (item != null) inv.setItem(k, item.clone());
//                }
//            }
            PlayerCustomGUI playerCustomGUI = new PlayerCustomGUI(gui, player, inv, category);
            gui.open(player, playerCustomGUI);
            player.openInventory(inv);
            playerCustomGUI.reloadRecipesTask();
            return playerCustomGUI;
        } catch (Exception e) {
            if (inv != null) {
                inv.clear();
            }
            player.closeInventory();
            throw new RuntimeException("Exception was thrown on gui open for: " + player.getName(), e);
        }
    }

    public void reloadRecipesTask() {
        ProRPGCrafting.getInstance().runSync(this::reloadRecipes);
    }

    public void reloadRecipes() {
        try {
            if (category.getPattern() != null)
                gui.setPattern(category.getPattern());
            else
                gui.resetPattern();
            CraftingTable      table      = Cfg.getTable(this.gui.name);
            ItemStack          fill       = table.getFillItem();
            Collection<Recipe> allRecipes = new ArrayList<>(category.getRecipes());
//            allRecipes.removeIf(r -> r.getNeededLevels() > LevelFunction.getLevel(player, table) + 5);
//            allRecipes.removeIf(r -> r.isMastery() && !MasteryManager.hasMastery(player, gui.name));
            allRecipes.removeIf(r -> !Utils.hasCraftingPermission(player, r.getName()));
            int pageSize       = this.gui.resultSlots.size();
            int allRecipeCount = allRecipes.size();
            int i              = 0;
            int page           = this.page;

            int fullPages = allRecipeCount / pageSize;
            int rest      = allRecipeCount % pageSize;
            int pages     = (rest == 0) ? fullPages : (fullPages + 1);
            if (page >= pages) {
                if (page > 0)
                    this.page = pages - 1;
                this.reloadRecipes();
                return;
            }

            Collection<ItemStack> playerItems       = getPlayerItems(this.player);
            CalculatedRecipe[]    calculatedRecipes = new CalculatedRecipe[(page < pages) ? pageSize : ((rest == 0) ? pageSize : rest)];
            Recipe[]              allRecipesArray   = allRecipes.toArray(new Recipe[allRecipeCount]);

            Integer[] slots = this.gui.resultSlots.toArray(new Integer[0]);
            for (int slot : slots) {
                this.inventory.setItem(slot, null);
            }

            this.gui.resetBlockedSlots(this.player, this.inventory, page, allRecipeCount,
                    new MessageData[]{
                            new MessageData("level", LevelFunction.getLevel(player, Cfg.getTable(gui.name))),
                            new MessageData("category", category),
                            new MessageData("gui", gui.getName()),
                            new MessageData("player", player.getName()),
                            new MessageData("bal", Vault.getMoney(player))
                    }, category.hasPrevious());

            for (int k = (page * pageSize), e = Math.min(slots.length, calculatedRecipes.length); (k < allRecipesArray.length) && (i < e); k++, i++) {
                Recipe           recipe           = allRecipesArray[k];
                int              slot             = slots[i];
                CalculatedRecipe calculatedRecipe = CalculatedRecipe.create(recipe, playerItems, this.player, table);
                this.recipes.put(slot, calculatedRecipes[i] = calculatedRecipe);
                this.inventory.setItem(slot, calculatedRecipe.getIcon().clone());
            }
            for (int k = 0; k < inventory.getSize(); k++) {
                if (inventory.getItem(k) != null && inventory.getItem(k).getType() != Material.AIR)
                    continue;

                inventory.setItem(k, fill);
            }
        } catch (Exception e) {
            this.inventory.clear();
            this.player.closeInventory();
            throw new RuntimeException("Exception was thrown when reloading recipes for: " + this.player.getName(), e);
        }
    }

    public Category getCategory() {
        return category;
    }

    public String getName() {
        return gui.getName();
    }

    public void onClick(InventoryClickEvent e) {
        if ((e.getRawSlot() >= this.gui.slots.length)) {
            if (e.getCursor().getType() == Material.BARRIER)
                e.setCancelled(true);
            return;
        }
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            e.setResult(Result.DENY);
            return;
        }

        Character c = gui.getPattern().getSlot(e.getRawSlot());
        gui.executeCommands(c, e.getWhoClicked());

        //Close on click
        if (gui.getPattern().getCloseOnClickSlots().contains(c)) {
            Bukkit.getScheduler().runTask(ProRPGCrafting.getInstance(), () -> e.getWhoClicked().closeInventory());
        }

        if (this.gui.slots[e.getRawSlot()].equals(Slot.BLOCKED_SLOT)) {
            e.setResult(Result.DENY);
            if ((this.gui.nextPage != -1) && (e.getSlot() == this.gui.nextPage)) {
                this.nextPage();
                return;
            }
            if (this.gui.prevPage != -1 && e.getSlot() == this.gui.prevPage) {
                this.prevPage();
            }
            return;
        }
        if (this.gui.slots[e.getRawSlot()].equals(Slot.BASE_RESULT_SLOT)) {
            e.setCancelled(true);
            e.setResult(Result.DENY);
            ProRPGCrafting.getInstance().runSync(() ->
            {
                this.reloadRecipes();
                this.craft(e.getRawSlot(), false);
                this.reloadRecipesTask();
            });
            return;
        }
        if (e.getCursor().getType() != Material.AIR) {

            if (Slot.SPECIAL_CRAFTING_SLOT.canHoldItem(e.getCursor()) == null) {
                e.setResult(Result.DENY);
                return;
            }
        }
        this.reloadRecipesTask();
    }

    private boolean validatePageCount() {
        if (this.page <= 0) {
            this.reloadRecipesTask();
            return false;
        }
        CraftingTable      table          = Cfg.getTable(this.gui.name);
        Collection<Recipe> allRecipes     = table.getRecipes().values();
        int                pageSize       = this.gui.resultSlots.size();
        int                allRecipeCount = allRecipes.size();
        int                i              = 0;
        int                page           = this.page;

        int fullPages = allRecipeCount / pageSize;
        int rest      = allRecipeCount % pageSize;
        int pages     = (rest == 0) ? fullPages : (fullPages + 1);
        if (page >= pages) {
            this.page = pages;
            this.reloadRecipesTask();
            return false;
        }
        return true;
    }

    private void prevPage() {
        if (this.page <= 0) {
            cancel();
            PlayerInitialGUI.open(gui, player);
            return;
        }
        this.page--;
        if (this.validatePageCount()) {
            this.reloadRecipesTask();
        }
    }

    private void nextPage() {
        this.page++;
        if (this.validatePageCount()) {
            this.reloadRecipesTask();
        }
    }

    private boolean canCraft(CalculatedRecipe calculatedRecipe, int slot) {
        Recipe recipe = calculatedRecipe.getRecipe();
        if (calculatedRecipe != null && calculatedRecipe.getRecipe().isMastery() && !PConfigManager.hasMastery(player, this.gui.getName())) {
            MessageUtil.sendMessage("crafting.error.noMastery", player, new MessageData("craftingTable", Cfg.getTable(gui.getName())));
            return false;
        }
        if ((calculatedRecipe == null) || !calculatedRecipe.isCanCraft()) {
            MessageUtil.sendMessage("crafting.gui.canCraft.false", player);
            return false;
        }
//        this.reloadRecipes();
        if (!Objects.equals(this.recipes.get(slot), calculatedRecipe)) {
            return false;
        }
        if (LevelFunction.getLevel(player, Cfg.getTable(this.gui.name)) < recipe.getNeededLevels()) {
            MessageUtil.sendMessage("crafting.error.noLevel", player, new MessageData("recipe", recipe));
            return false;
        }
        if (ExperienceManager.getTotalExperience(this.player) < recipe.getNeededXp()) {
            MessageUtil.sendMessage("crafting.error.noXP", player, new MessageData("recipe", recipe));
            return false;
        }
        if (!Vault.canPay(this.player, recipe.getPrice())) {
            MessageUtil.sendMessage("crafting.error.noFunds", player, new MessageData("recipe", recipe));
            return false;
        }

        return true;
    }

    private boolean craft(int slot, boolean addToCursor) {
        /* if (craftingTask != null) {
            MessageUtil.sendMessage("crafting.alreadyCrafting", player);
            return false;
        }*/

        CalculatedRecipe calculatedRecipe = this.recipes.get(slot);
        Recipe           recipe           = calculatedRecipe.getRecipe();
        if (craftingRecipe != null && craftingRecipe.equals(recipe)) {
            cancel();
            return false;
        }

        cancel();
        if (!canCraft(calculatedRecipe, slot)) return false;

        RecipeItem recipeResult = recipe.getResult();
        ItemStack  resultItem   = recipeResult.getItemStack();

        //Add "Crafted by"
        if (player.hasPermission("crafting.craftedby." + recipe.getName())) {
            ItemMeta     meta = resultItem.getItemMeta();
            List<String> lore = meta.getLore();
            lore.add(ChatColor.WHITE + " - " + ChatColor.YELLOW + "Crafted by: " + ChatColor.WHITE + player.getName());
            meta.setLore(lore);
            resultItem.setItemMeta(meta);
        }

        if (addToCursor) {
            ItemStack cursor = this.player.getItemOnCursor();
            if (resultItem.isSimilar(cursor)) {
                if ((resultItem.getAmount() + cursor.getAmount()) > resultItem.getMaxStackSize()) {
                    return false;
                }
            } else if ((cursor != null) && (cursor.getType() != Material.AIR)) {
                return false;
            }
        }

        Collection<ItemStack> itemsToTake = recipe.getItemsToTake();
        Collection<ItemStack> taken       = new ArrayList<>(itemsToTake.size());
        PlayerInventory       inventory   = this.player.getInventory();

        for (Iterator<ItemStack> iterator = itemsToTake.iterator(); iterator.hasNext(); ) {
            ItemStack toTake = iterator.next();
            for (ItemStack entry : getPlayerItems(player)) {
                ItemStack item = entry.clone();
                entry = entry.clone();
                if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
                    item = item.clone();
//                    ItemMeta meta = item.getItemMeta();
//                    List<String> itemLore = meta.getLore();
//                    itemLore.removeIf(s -> org.apache.commons.lang.StringUtils.contains(s, "Crafted by"));
//                    meta.setLore(itemLore);
//                    item.setItemMeta(meta);
                    entry.setAmount(toTake.getAmount());

                    if (CalculatedRecipe.isSimilar(toTake, item)) {
                        toTake = entry;
                        break;
                    }
                }
            }

            HashMap<Integer, ItemStack> notRemoved = inventory.removeItem(toTake);
            if (notRemoved.isEmpty()) {
                taken.add(toTake);
                iterator.remove();
                continue;
            }
            for (ItemStack itemStack : taken) {
                HashMap<Integer, ItemStack> notAdded = inventory.addItem(itemStack);
                if (notAdded.isEmpty()) {
                    break;
                }
                for (ItemStack stack : notAdded.values()) {
                    this.player.getWorld().dropItemNaturally(this.player.getLocation(), stack);
                }
            }
            break;
        }

        refund = taken;
        if (!itemsToTake.isEmpty()) {
            MessageUtil.sendMessage("crafting.error.insufficientItems", player, new MessageData("recipe", recipe));
            cancel();
            return false;
        }

        double modifier = ProRPGCrafting.getInstance().getPlayerCooldown(player);
        int cooldown = modifier == 0d
                ? recipe.getCooldown()
                : (int) Math.round(recipe.getCooldown() - (recipe.getCooldown() * modifier));
        showBossBar(this.player, cooldown);

        if (cooldown != 0) {
            previousCursor = player.getOpenInventory().getCursor();
            player.getOpenInventory().setCursor(new ItemStack(Material.BARRIER));
        }

        craftingSuccess = false;
        craftingRecipe = recipe;
        craftingTask = ProRPGCrafting.getInstance().runTaskLater(cooldown, () -> {
            craftingSuccess = true;
            if (recipe.getCommands().size() == 0) {
                if (addToCursor) {
                    ItemStack cursor = this.player.getItemOnCursor();
                    if (cursor != null && cursor.isSimilar(recipe.getResult().getItemStack())) {
                        if (cursor.getAmount() < cursor.getMaxStackSize() && cursor.getAmount() + recipe.getResult().getAmount() <= cursor.getMaxStackSize()) {
                            cursor.setAmount(cursor.getAmount() + recipe.getResult().getAmount());
                            this.player.setItemOnCursor(cursor);
                        } else {
                            craftingSuccess = false;
                        }
                    } else if (cursor == null || cursor.getType() == Material.AIR) {
                        this.player.setItemOnCursor(resultItem);
                    } else {
                        craftingSuccess = false;
                    }
                } else {
                    boolean fits = calcWillFit(resultItem);
                    if (fits) {
                        HashMap<Integer, ItemStack> notAdded = inventory.addItem(resultItem);
                        if (!notAdded.isEmpty()) {
                            for (ItemStack stack : notAdded.values()) {
                                this.player.getWorld().dropItemNaturally(this.player.getLocation(), stack);
                            }
                        }
                    } else {
                        craftingSuccess = false;
                    }
                }
            }

            if (craftingSuccess) {
                cancel(false);
                Vault.pay(this.player, recipe.getPrice());
                //Commands
                DelayedCommand.invoke(ProRPGCrafting.getInstance(), player, recipe.getCommands());

                //Experience
                CraftingTable table = Cfg.getTable(this.gui.name);

                if (recipe.getXpGain() > 0) {
                    ProRPGCrafting.getExperienceManager().getPlayerData(player).add(table, recipe.getXpGain());
                }

                //Restart the crafting sequence if auto-crafting is enabled
                if (PConfigManager.getPlayerConfig(player).isAutoCraft()) {
                    reloadRecipesTask();
                    boolean success = craft(slot, addToCursor); //Call this method again recursively
                    if (!success)
                        MessageUtil.sendMessage("crafting.autoCancelled", player);
                }
            } else {
                cancel();
            }
        });

        return true;
    }

    private boolean calcWillFit(ItemStack item) {
        Inventory inv = Bukkit.createInventory(null, InventoryType.PLAYER);
        inv.setContents(inventory.getContents());
        return inv.addItem(item).isEmpty();
    }

    private void showBossBar(Player target, double cooldown) {
        if (cooldown == 0)
            return;

        bar = Bukkit.createBossBar(ChatColor.GREEN + "Crafting...", BarColor.BLUE, BarStyle.SOLID, BarFlag.PLAY_BOSS_MUSIC);
        bar.setProgress(0d);
        bar.addPlayer(target);
        barTask = new BukkitRunnable() {
            int count = 0;

            public void run() {
                if (count >= cooldown * 20) {
                    this.cancel();
                    bar.removeAll();
                }

                bar.setProgress(Math.min(1d, count / (cooldown * 20)));

                count++;
            }
        }.runTaskTimer(ProRPGCrafting.getInstance(), 1L, 1L);
    }

    public void cancel() {
        cancel(true);
    }

    private void cancel(boolean refund) {
        if (craftingTask == null) return;
        craftingRecipe = null;
        if (barTask != null) {
            barTask.cancel();
            barTask = null;
            bar.removeAll();
            bar = null;
        }

        if (!craftingSuccess && PConfigManager.getPlayerConfig(player).isAutoCraft()) {
            MessageUtil.sendMessage("crafting.autoCancelled", player);
        }

        if (player.getOpenInventory().getCursor() != null && player.getOpenInventory().getCursor().getType() == Material.BARRIER)
            if (previousCursor != null) {
                player.getOpenInventory().setCursor(previousCursor);
                previousCursor = null;
            } else
                player.getOpenInventory().setCursor(new ItemStack(Material.AIR));

        if (craftingTask != null)
            craftingTask.cancel();
        craftingTask = null;

        if (!refund || craftingSuccess)
            return;

        PlayerInventory       inventory = player.getInventory();
        Collection<ItemStack> notAdded  = inventory.addItem(this.refund.toArray(new ItemStack[0])).values();
        if (!notAdded.isEmpty()) {
            for (ItemStack item : notAdded) {
                player.getLocation().getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
        this.refund.clear();
    }
}
