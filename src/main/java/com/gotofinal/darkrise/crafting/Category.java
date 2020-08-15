package com.gotofinal.darkrise.crafting;

import com.gotofinal.darkrise.economy.DarkRiseEconomy;
import me.travja.darkrise.core.item.DarkRiseItem;
import me.travja.darkrise.core.legacy.util.DeserializationWorker;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Category implements ConfigurationSerializable {
    private final String name;
    private final DarkRiseItem iconItem;
    private final Collection<Recipe> recipes = new ArrayList<>();
    private InventoryPattern pattern;
    private final int order;
    private boolean hasPrevious = true;

    public Category(String name) {
        this.name = name;
        this.order = 0;
        this.iconItem = DarkRiseEconomy.getInstance().getItems().getItems().iterator().next();
    }

    public Category(Map<String, Object> map) {
        DeserializationWorker dw = DeserializationWorker.start(map);
        name = dw.getString("name");
        order = dw.getInt("order");
        iconItem = DarkRiseEconomy.getInstance().getItems().getItemById(dw.getString("icon"));

        if (iconItem == null) {
            DarkRiseCrafting.getInstance().getLogger().severe("Invalid category icon for: " + name);
        }

        pattern = dw.getSection("pattern") != null ? new InventoryPattern(dw.getSection("pattern")) : null;
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        if (pattern != null)
            map.put("pattern", pattern.serialize());
        return map;
    }

    public InventoryPattern getPattern() {
        return pattern;
    }

    public void setPattern(InventoryPattern pattern) {
        this.pattern = pattern;
    }

    public String getName() {
        return name;
    }

    public Collection<Recipe> getRecipes() {
        return recipes;
    }

    public DarkRiseItem getIconItem() {
        return iconItem;
    }

    public int getOrder() {
        return order;
    }

    public void hasPrevious(boolean b) {
        this.hasPrevious = b;
    }

    public boolean hasPrevious() {
        return hasPrevious;
    }
}
