package com.gotofinal.darkrise.crafting;

import mc.promcteam.engine.NexEngine;
import mc.promcteam.engine.items.ItemType;
import mc.promcteam.engine.items.exception.ProItemException;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.bukkit.inventory.ItemStack;

public class RecipeEconomyItem implements RecipeItem {
    private final String itemName;
    private final int amount;

    public RecipeEconomyItem(String itemName, int amount) {
        this.itemName = itemName;
        this.amount = amount;
    }

    public String getItemName() {
        return this.itemName;
    }

    @Override
    public int getAmount() {
        return this.amount;
    }

    public ItemType asItemType() {
        try {
            return NexEngine.get().getItemManager().getItemType(this.itemName);
        } catch (ProItemException e) {
            return null;
        }
    }

    @Override
    public ItemStack getItemStack() {
        ItemType customItem = this.asItemType();
        if (customItem == null) {
            ProRPGCrafting.getInstance().error("Can't find CustomItem named: " + this.itemName);
            ProRPGCrafting.getInstance().error("Can't find CustomItem named: " + this.itemName);
            throw new NullPointerException("Can't find CustomItem named: " + this.itemName);
        }
        ItemStack itemStack = customItem.create();
        itemStack.setAmount(this.amount);
        return itemStack;
    }

    @Override
    public String toConfig() {
        return this.itemName + ":" + this.amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecipeEconomyItem)) {
            return false;
        }

        RecipeEconomyItem that = (RecipeEconomyItem) o;

        return (this.amount == that.amount) && this.itemName.equals(that.itemName);

    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).appendSuper(super.toString()).append("itemName", this.itemName).append("amount", this.amount).toString();
    }

    @Override
    public int hashCode() {
        int result = this.itemName.hashCode();
        result = (31 * result) + this.amount;
        return result;
    }
}
