package gg.pvpgames.demo.kit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An immutable kit definition: the armor, hotbar items, and metadata that get applied to a player
 * when a match starts. Built once from kits.yml by {@link KitManager} and reused for every match.
 *
 * <p>Items are stored as {@link ItemStack} templates; we clone them before giving them to players
 * so one match can't mutate the shared definition.
 */
public final class Kit {

    private final String name;
    private final Material icon;
    private final String description;
    private final boolean naturalRegen;
    private final boolean usePlayerInventory;

    @Nullable
    private final ItemStack helmet;
    @Nullable
    private final ItemStack chestplate;
    @Nullable
    private final ItemStack leggings;
    @Nullable
    private final ItemStack boots;
    @Nullable
    private final ItemStack offhand;

    private final List<ItemStack> items;

    public Kit(String name, Material icon, String description, boolean naturalRegen,
               boolean usePlayerInventory, @Nullable ItemStack helmet,
               @Nullable ItemStack chestplate, @Nullable ItemStack leggings,
               @Nullable ItemStack boots, @Nullable ItemStack offhand, List<ItemStack> items) {
        this.name = name;
        this.icon = icon;
        this.description = description;
        this.naturalRegen = naturalRegen;
        this.usePlayerInventory = usePlayerInventory;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.offhand = offhand;
        this.items = items;
    }

    public String name() {
        return name;
    }

    public Material icon() {
        return icon;
    }

    public String description() {
        return description;
    }

    public boolean naturalRegen() {
        return naturalRegen;
    }

    /** When true, the player keeps their own inventory (the "Custom" sandbox kit). */
    public boolean usePlayerInventory() {
        return usePlayerInventory;
    }

    @Nullable
    public ItemStack helmet() {
        return clone(helmet);
    }

    @Nullable
    public ItemStack chestplate() {
        return clone(chestplate);
    }

    @Nullable
    public ItemStack leggings() {
        return clone(leggings);
    }

    @Nullable
    public ItemStack boots() {
        return clone(boots);
    }

    @Nullable
    public ItemStack offhand() {
        return clone(offhand);
    }

    /** Defensive copies of the hotbar items so the template is never mutated. */
    public List<ItemStack> items() {
        return items.stream().map(ItemStack::clone).toList();
    }

    @Nullable
    private static ItemStack clone(@Nullable ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
}
