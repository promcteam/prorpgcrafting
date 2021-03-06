package com.gotofinal.darkrise.crafting;

import me.travja.darkrise.core.legacy.cmds.DelayedCommand;
import me.travja.darkrise.core.legacy.util.DeserializationWorker;
import me.travja.darkrise.core.legacy.util.SerializationBuilder;
import me.travja.darkrise.core.legacy.util.item.ItemBuilder;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;

import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.stream.Collectors;

public class InventoryPattern implements ConfigurationSerializable {
    private final String[] pattern; // _ for ingredients, = for result.
    private final HashMap<Character, ItemStack> items;
    private final HashMap<Character, Collection<DelayedCommand>> commands = new HashMap<>();
    private final List<Character> closeOnClickSlots = new ArrayList<>();

    public InventoryPattern(String[] pattern, HashMap<Character, ItemStack> items) {
        this.pattern = pattern;
        this.items = items;
    }

    @SuppressWarnings("unchecked")
    public InventoryPattern(Map<String, Object> map) {

        DeserializationWorker dw = DeserializationWorker.start(map);
        List<String> temp = dw.getStringList("pattern");
        this.pattern = temp.toArray(new String[temp.size()]);
        this.items = new HashMap<>();
        DeserializationWorker itemsTemp = DeserializationWorker.start(dw.getSection("items", new HashMap<>(2)));
        for (String entry : itemsTemp.getMap().keySet()) {
            if (entry.contains("."))
                continue;

            Map<String, Object> section = itemsTemp.getSection(entry);
            this.items.put(entry.charAt(0), new ItemBuilder(section).build());

            if (section.containsKey("closeonclick") && (boolean) section.get("closeonclick")) {
                closeOnClickSlots.add(entry.charAt(0));
            }
        }

        final DeserializationWorker commandsTemp = DeserializationWorker.start(dw.getSection("commands", new HashMap<>(2)));
        for (final String entry : commandsTemp.getMap().keySet()) {
            this.commands.put(entry.charAt(0), commandsTemp.deserializeCollection(new ArrayList<>(5), entry, DelayedCommand.class));
        }
    }

    public String[] getPattern() {
        return this.pattern;
    }

    public HashMap<Character, ItemStack> getItems() {
        return this.items;
    }

    public Collection<DelayedCommand> getCommands(char c) {
        return this.commands.get(c);
    }

    public List<Character> getCloseOnClickSlots() {
        return closeOnClickSlots;
    }

    public Character getSlot(int slot) {
        if (slot / 9 >= pattern.length)
            return ' ';
        return pattern[slot / 9].charAt(slot % 9);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE).appendSuper(super.toString()).append("pattern", this.pattern).append("items", this.items).toString();
    }

    @Override
    public Map<String, Object> serialize() {
        //noinspection Convert2MethodRef,RedundantCast eclipse...,
        return SerializationBuilder.start(2)
                .append("pattern", this.pattern)
                .appendMap("commands", this.commands)
                .append("items", this.items.entrySet().stream().map(e -> new SimpleEntry<>(e.getKey().toString(), ItemBuilder.newItem(e.getValue()).serialize())).collect(Collectors.toMap((stringMapSimpleEntry) -> ((SimpleEntry<String, Map<String, Object>>) stringMapSimpleEntry).getKey(), (stringMapSimpleEntry1) -> ((SimpleEntry<String, Map<String, Object>>) stringMapSimpleEntry1).getValue()))).build();
    }
}
