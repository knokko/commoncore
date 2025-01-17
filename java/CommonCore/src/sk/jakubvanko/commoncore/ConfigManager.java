package sk.jakubvanko.commoncore;

import com.cryptomorin.xseries.XMaterial;
import com.google.common.collect.Multimap;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

/**
 * Represents a config manager and loader
 */
public class ConfigManager {

    protected File configFile;
    protected Configuration config;
    protected IClickActionFactory clickActionFactory;
    protected Plugin plugin;

    /**
     * Creates a config manager
     *
     * @param configPath         Path to the configuration to link
     * @param clickActionFactory Factory for creating click actions from their names
     */
    @Deprecated
    public ConfigManager(String configPath, IClickActionFactory clickActionFactory) {
        configFile = new File(configPath);
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                Bukkit.getServer().getLogger().severe("Error: Unable to create config file " + configFile.getName());
                e.printStackTrace();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.clickActionFactory = clickActionFactory;
    }

    /**
     * Creates a config manager
     *
     * @param configPath         Path to the configuration to link
     * @param clickActionFactory Factory for creating click actions from their names
     */
    public ConfigManager(String configPath, IClickActionFactory clickActionFactory, Plugin plugin) {
        this(configPath, clickActionFactory);
        this.plugin = plugin;
    }

    /**
     * Loads all data from config
     *
     * @return Config data holder
     */
    public ConfigData loadData() {
        Map<String, ItemStack> itemStackMap = loadItemStacks();
        Map<String, Recipe> recipeMap = loadRecipes(itemStackMap);
        Map<String, InventoryData> inventoryDataMap = loadInventories(itemStackMap);
        Map<String, String> messageMap = loadMessages();
        return new ConfigData(config, itemStackMap, recipeMap, inventoryDataMap, messageMap);
    }

    /**
     * Registers the given itemstack into the item section of the config
     *
     * @param itemStack Itemstack to register
     * @return String representing the item identifier
     */
    public String registerItem(ItemStack itemStack) {
        String itemIdentifier = itemStack.getType().name().toLowerCase() + "_" + itemStack.hashCode();
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<String> itemData = new ArrayList<>();
        itemData.add("  " + itemIdentifier + ":");
        if (itemMeta.hasDisplayName()) {
            itemData.add("    name: '" + itemMeta.getDisplayName() + "'");
        }
        String materialName = itemStack.getType().name();
        if (!XMaterial.isNewVersion()) {
            materialName += ":" + itemStack.getData().getData();
        }
        itemData.add("    material: " + materialName);
        itemData.add("    amount: " + itemStack.getAmount());
        // This assures compatibility with older versions
        if (XMaterial.isNewVersion()) {
            Damageable damageable = (Damageable) itemMeta;
            itemData.add("    damage: " + damageable.getDamage());
            if (itemMeta.isUnbreakable()) {
                itemData.add("    unbreakable: " + "true");
            }
            Multimap<Attribute, AttributeModifier> attributeModifierMultimap = itemMeta.getAttributeModifiers();
            if (attributeModifierMultimap != null) {
                Map<Attribute, Collection<AttributeModifier>> attributeModifierMap = attributeModifierMultimap.asMap();
                if (attributeModifierMap != null) {
                    itemData.add("    item_attributes:");
                    for (Map.Entry<Attribute, Collection<AttributeModifier>> entry : attributeModifierMap.entrySet()) {
                        itemData.add("      " + entry.getKey().name() + ":");
                        // Sort the collection by attribute modifier slot
                        Map<EquipmentSlot, List<AttributeModifier>> slotModifierMap = new HashMap<>();
                        for (AttributeModifier attributeModifier : entry.getValue()) {
                            EquipmentSlot equipmentSlot = attributeModifier.getSlot();
                            slotModifierMap.computeIfAbsent(equipmentSlot, k -> new ArrayList<>());
                            List<AttributeModifier> attributeModifierList = slotModifierMap.get(equipmentSlot);
                            attributeModifierList.add(attributeModifier);
                        }
                        // Now write them properly
                        for (Map.Entry<EquipmentSlot, List<AttributeModifier>> equipmentEntry : slotModifierMap.entrySet()) {
                            itemData.add("        " + equipmentEntry.getKey().name() + ":");
                            for (AttributeModifier attributeModifier : equipmentEntry.getValue()) {
                                itemData.add("          " + attributeModifier.getOperation().name() + ": " + attributeModifier.getAmount());
                            }
                        }
                    }
                }
            }
        }
        List<String> loreList = itemMeta.getLore();
        if (loreList != null) {
            String loreTitle = loreList.size() != 0 ? "    lore:" : "    lore: []";
            itemData.add(loreTitle);
            for (String line : loreList) {
                String formattedLine = "    - '" + line + "'";
                itemData.add(formattedLine);
            }
        }
        Map<Enchantment, Integer> enchantments = itemStack.getEnchantments();
        if (enchantments != null) {
            itemData.add("    enchantments:");
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                itemData.add("      " + entry.getKey().getName() + ": " + entry.getValue());
            }
        }
        Set<ItemFlag> itemFlags = itemMeta.getItemFlags();
        if (itemFlags != null) {
            String itemFlagTitle = itemFlags.size() != 0 ? "    item_flags:" : "    item_flags: []";
            itemData.add(itemFlagTitle);
            for (ItemFlag flag : itemFlags) {
                String formattedFlagLine = "    - '" + flag.name() + "'";
                itemData.add(formattedFlagLine);
            }
        }
        try {
            List<String> allLines = Files.readAllLines(configFile.toPath());
            for (int i = 0; i < allLines.size(); i++) {
                if (allLines.get(i).equals("items:")) {
                    allLines.addAll(i + 1, itemData);
                    break;
                }
            }
            Files.write(configFile.toPath(), allLines); // You can add a charset and other options too
        } catch (Exception e) {
            Bukkit.getServer().getLogger().severe("Error: Unable to edit config file " + configFile.getName());
        }
        // Reloading the configuration
        this.config = YamlConfiguration.loadConfiguration(configFile);
        return itemIdentifier;
    }

    /**
     * Loads the map of item identifiers and their corresponding item stacks from the config
     *
     * @return Map of item identifiers and their corresponding item stacks
     */
    protected Map<String, ItemStack> loadItemStacks() {
        Map<String, ItemStack> itemMap = new HashMap<>();
        ConfigurationSection itemSection = config.getConfigurationSection("items");
        if (itemSection == null) return itemMap;
        for (String itemIdentifier : itemSection.getKeys(false)) {
            ConfigurationSection dataSection = itemSection.getConfigurationSection(itemIdentifier);

            // Modifications for nice compatibility with Custom Items and Textures
            String customItemName = dataSection.getString("CustomItemName");
            if (customItemName != null) {
                try {
                    ItemStack customItemStack = null;

                    // CustomItems 9.14 and later has a nice API
                    try {
                        int amount = dataSection.getInt("amount", 1);
                        customItemStack = (ItemStack) Class.forName(
                                "nl.knokko.customitems.plugin.CustomItemsApi"
                        ).getMethod(
                                "createItemStack", String.class, int.class
                        ).invoke(null, customItemName, amount);
                    } catch (ClassNotFoundException tooOldForApi) {
                        // If we reach this line, we should continue with attempting the 'old ugly' way of creating an item stack
                    }

                    // This is the 'old ugly' way of creating an item stack...
                    if (customItemStack == null) {
                        Plugin customItemsPlugin = Bukkit.getPluginManager().getPlugin("CustomItems");

                        // Intentionally cause NullPointerException if it is not available
                        Object customItemSet = customItemsPlugin.getClass().getMethod("getSet").invoke(customItemsPlugin);
                        Object customItem = customItemSet.getClass().getMethod("getItem", String.class).invoke(customItemSet, customItemName);
                        if (customItem != null) {
                            customItemStack = (ItemStack) customItem.getClass().getMethod("create", int.class).invoke(customItem, 1);
                        }
                    }

                    if (customItemStack == null) {
                        Bukkit.getLogger().warning("A custom item with name '" + customItemName + "' was requested, but it doesn't exist");
                    } else {
                        itemMap.put(itemIdentifier, customItemStack);
                        continue;
                    }
                } catch (Exception ex) {
                    Bukkit.getLogger().log(Level.WARNING, "Attempted to load a custom item from the config, but it looks like Custom Items and Textures is not installed", ex);
                }
            }
            // End of modifications

            String name = dataSection.getString("name", null);
            String materialName = dataSection.getString("material", "AIR");
            Optional<XMaterial> optionalXMaterial = XMaterial.matchXMaterial(materialName);
            XMaterial xMaterial = optionalXMaterial.get();
            int amount = dataSection.getInt("amount", 1);
            int damage = dataSection.getInt("damage", 0);
            boolean unbreakable = dataSection.getBoolean("unbreakable", false);
            List<String> lore = dataSection.getStringList("lore");
            List<String> itemFlagStrings = dataSection.getStringList("item_flags");
            List<ItemFlag> itemFlags = new ArrayList<>();
            if (itemFlagStrings != null) {
                for (String itemFlagString : itemFlagStrings) {
                    ItemFlag itemFlag = ItemFlag.valueOf(itemFlagString);
                    itemFlags.add(itemFlag);
                }
            }
            ConfigurationSection enchantmentSection = dataSection.getConfigurationSection("enchantments");
            Map<Enchantment, Integer> enchantments = new HashMap<>();
            if (enchantmentSection != null) {
                for (String enchantmentName : enchantmentSection.getKeys(false)) {
                    Enchantment enchantment = Enchantment.getByName(enchantmentName);
                    Integer level = enchantmentSection.getInt(enchantmentName, 1);
                    enchantments.put(enchantment, level);
                }
            }

            ItemBuilder itemBuilder = new ItemBuilder(xMaterial)
                    .setAmount(amount)
                    .setDamage(damage)
                    .setUnbreakable(unbreakable)
                    .setLore(lore)
                    .setItemFlags(itemFlags)
                    .setEnchantments(enchantments);
            if (name != null) {
                itemBuilder.setName(name);
            }
            // Adding attribute modifiers
            if (XMaterial.isNewVersion()) {
                ConfigurationSection attributesSection = dataSection.getConfigurationSection("item_attributes");
                Map<Attribute, List<AttributeModifier>> attributeModifiers = new HashMap<>();
                if (attributesSection != null) {
                    for (String itemAttributeName : attributesSection.getKeys(false)) {
                        Attribute attribute = Attribute.valueOf(itemAttributeName);
                        List<AttributeModifier> attributeModifierList = new ArrayList<>();
                        ConfigurationSection itemAttributeSection = attributesSection.getConfigurationSection(itemAttributeName);
                        if (itemAttributeSection == null) continue;
                        for (String itemAttributeSlot : itemAttributeSection.getKeys(false)) {
                            EquipmentSlot attributeSlot = EquipmentSlot.valueOf(itemAttributeSlot);
                            ConfigurationSection itemAttributeSlotSection = itemAttributeSection.getConfigurationSection(itemAttributeSlot);
                            if (itemAttributeSlotSection == null) continue;
                            for (String itemAttributeSlotOperation : itemAttributeSlotSection.getKeys(false)) {
                                AttributeModifier.Operation attributeOperation = AttributeModifier.Operation.valueOf(itemAttributeSlotOperation);
                                double attributeOperationAmount = itemAttributeSlotSection.getDouble(itemAttributeSlotOperation, 1);
                                AttributeModifier attributeModifier = new AttributeModifier(UUID.randomUUID(), itemAttributeName,
                                        attributeOperationAmount, attributeOperation, attributeSlot);
                                attributeModifierList.add(attributeModifier);
                            }
                        }
                        attributeModifiers.put(attribute, attributeModifierList);
                    }
                }
                itemBuilder.setAttributeModifiers(attributeModifiers);
            }
            ItemStack createdItem = itemBuilder.build();
            itemMap.put(itemIdentifier, createdItem);
        }
        return itemMap;
    }

    /**
     * Loads the map of recipe identifiers and their corresponding recipes from the config
     *
     * @param itemStackMap Map of item identifiers and their corresponding item stacks
     * @return Map of recipe identifiers and their corresponding recipes
     */
    protected Map<String, Recipe> loadRecipes(Map<String, ItemStack> itemStackMap) {
        Map<String, Recipe> recipeMap = new HashMap<>();
        ConfigurationSection recipeSection = config.getConfigurationSection("recipes");
        if (recipeSection == null) return recipeMap;
        Map<Character, XMaterial> ingredientsMap = new HashMap<>();
        // Loading ingredients
        ConfigurationSection ingredientSection = recipeSection.getConfigurationSection("ingredients");
        if (ingredientSection == null) return recipeMap;
        for (String ingredientSymbol : ingredientSection.getKeys(false)) {
            Character ingredientCharacter = ingredientSymbol.charAt(0);
            Optional<XMaterial> optionalIngredient = XMaterial.matchXMaterial(ingredientSection.getString(ingredientSymbol));
            XMaterial ingredient = optionalIngredient.get();
            ingredientsMap.put(ingredientCharacter, ingredient);
        }
        // Creating shaped recipes
        ConfigurationSection shapedRecipeSection = recipeSection.getConfigurationSection("shaped_recipes");
        if (shapedRecipeSection != null) {
            for (String itemIdentifier : shapedRecipeSection.getKeys(false)) {
                ConfigurationSection recipeRowSection = shapedRecipeSection.getConfigurationSection(itemIdentifier);
                ItemStack result = itemStackMap.get(itemIdentifier);
                ShapedRecipe shapedRecipe;
                try {
                    NamespacedKey namespacedKey = new NamespacedKey(this.plugin, itemIdentifier);
                    shapedRecipe = new ShapedRecipe(namespacedKey, result);
                } catch (Exception e) {
                    shapedRecipe = new ShapedRecipe(result);
                }
                String recipeRow1 = recipeRowSection.getString("row_1", "   ");
                String recipeRow2 = recipeRowSection.getString("row_2", "   ");
                String recipeRow3 = recipeRowSection.getString("row_3", "   ");
                shapedRecipe.shape(recipeRow1, recipeRow2, recipeRow3);
                for (Character ingredientCharacter : recipeRow1.toCharArray()) {
                    if (ingredientCharacter == ' ') continue;
                    shapedRecipe.setIngredient(ingredientCharacter, ingredientsMap.get(ingredientCharacter).parseMaterial());
                }
                for (Character ingredientCharacter : recipeRow2.toCharArray()) {
                    if (ingredientCharacter == ' ') continue;
                    shapedRecipe.setIngredient(ingredientCharacter, ingredientsMap.get(ingredientCharacter).parseMaterial());
                }
                for (Character ingredientCharacter : recipeRow3.toCharArray()) {
                    if (ingredientCharacter == ' ') continue;
                    shapedRecipe.setIngredient(ingredientCharacter, ingredientsMap.get(ingredientCharacter).parseMaterial());
                }
                recipeMap.put(itemIdentifier, shapedRecipe);
            }
        }
        // Creating shapeless recipes
        ConfigurationSection shapelessRecipeSection = recipeSection.getConfigurationSection("shapeless_recipes");
        if (shapelessRecipeSection != null) {
            for (String itemIdentifier : shapelessRecipeSection.getKeys(false)) {
                ItemStack result = itemStackMap.get(itemIdentifier);
                String ingredientString = shapelessRecipeSection.getString(itemIdentifier);
                ShapelessRecipe shapelessRecipe;
                try {
                    NamespacedKey namespacedKey = new NamespacedKey(this.plugin, itemIdentifier);
                    shapelessRecipe = new ShapelessRecipe(namespacedKey, result);
                } catch (Exception e) {
                    shapelessRecipe = new ShapelessRecipe(result);
                }
                for (Character ingredientCharacter : ingredientString.toCharArray()) {
                    shapelessRecipe.addIngredient(ingredientsMap.get(ingredientCharacter).parseMaterial());
                }
                recipeMap.put(itemIdentifier, shapelessRecipe);
            }
        }
        return recipeMap;
    }

    /**
     * Loads the map of inventory data identifiers and their corresponding inventory data holders from the config
     *
     * @param itemStackMap Map of item identifiers and their corresponding item stacks
     * @return Map of inventory data identifiers and their corresponding inventory data holders
     */
    protected Map<String, InventoryData> loadInventories(Map<String, ItemStack> itemStackMap) {
        Map<String, InventoryData> inventoryMap = new HashMap<>();
        ConfigurationSection inventorySection = config.getConfigurationSection("inventories");
        // Creating chest inventories
        ConfigurationSection chestInventorySection = inventorySection.getConfigurationSection("chest_inventories");
        if (chestInventorySection != null) {
            for (String inventoryIdentifier : chestInventorySection.getKeys(false)) {
                ConfigurationSection dataSection = chestInventorySection.getConfigurationSection(inventoryIdentifier);
                int size = dataSection.getInt("size", 1);
                String title = dataSection.getString("title", "not_specified");
                Inventory chestInventory = Bukkit.createInventory(null, size, title);
                ConfigurationSection contentSection = dataSection.getConfigurationSection("content");
                InventoryData inventoryData = setupChestInventory(chestInventory, contentSection, itemStackMap, title);
                inventoryMap.put(inventoryIdentifier, inventoryData);
            }
        }
        return inventoryMap;
    }

    /**
     * Creates chest inventory from a given configuration section
     *
     * @param dataSection Configuration section containing title and size
     * @return New, empty chest inventory
     */
    @Deprecated
    protected Inventory createChestInventory(ConfigurationSection dataSection) {
        int size = dataSection.getInt("size", 1);
        String title = dataSection.getString("title", "not_specified");
        return Bukkit.createInventory(null, size, title);
    }

    /**
     * Setups the chest inventory as inventory data holder with actions and items loaded from config
     *
     * @param chestInventory Inventory to be filled
     * @param contentSection Configuration section containing the contents of the inventory
     * @param itemStackMap   Map of item identifiers and their corresponding item stacks
     * @return Complete inventory data holder
     */
    protected InventoryData setupChestInventory(Inventory chestInventory, ConfigurationSection contentSection, Map<String, ItemStack> itemStackMap, String title) {
        // Each inventory slot has a number of actions that will be called on click
        Map<Integer, List<ClickAction>> slotToActionMap = new HashMap<>();
        for (String inventorySlot : contentSection.getKeys(false)) {
            // Filling the inventory
            Integer inventorySlotNumber = Integer.parseInt(inventorySlot);
            ConfigurationSection slotSection = contentSection.getConfigurationSection(inventorySlot);
            String itemIdentifier = slotSection.getString("item");
            ItemStack itemIcon = itemStackMap.get(itemIdentifier);
            chestInventory.setItem(inventorySlotNumber, itemIcon);
            List<ClickAction> clickActions = new ArrayList<>();
            ConfigurationSection actionSection = slotSection.getConfigurationSection("actions");
            // Creating Click Actions
            if (actionSection != null) {
                for (String actionName : actionSection.getKeys(false)) {
                    ConfigurationSection actionArgumentSection = actionSection.getConfigurationSection(actionName);
                    Map<String, Object> argumentMap = new HashMap<>();
                    // Mapping action argument names to arguments
                    if (actionArgumentSection != null) {
                        for (String actionArgumentName : actionArgumentSection.getKeys(false)) {
                            Object actionArgument = actionArgumentSection.get(actionArgumentName);
                            argumentMap.put(actionArgumentName, actionArgument);
                        }
                    }
                    ClickAction clickAction = clickActionFactory.getClickAction(actionName, argumentMap);
                    clickActions.add(clickAction);
                }
                // Mapping inventory slots to actions
                slotToActionMap.put(inventorySlotNumber, clickActions);
            }
        }
        return new InventoryData(chestInventory, slotToActionMap, title);
    }

    /**
     * Gets the map of message identifier linked to their interpretations from the config
     *
     * @return Map of message identifier linked to their interpretations
     */
    protected Map<String, String> loadMessages() {
        Map<String, String> messageMap = new HashMap<>();
        ConfigurationSection messageSection = config.getConfigurationSection("messages");
        if (messageSection == null) return messageMap;
        for (String messageIdentifier : messageSection.getKeys(false)) {
            String message = messageSection.getString(messageIdentifier);
            messageMap.put(messageIdentifier, message);
        }
        return messageMap;
    }

    /**
     * Replaces abbreviations in a lore with their substitutes
     *
     * @param itemstack     Target itemstack
     * @param abbreviations Map of abbreviations and their substitutes
     */
    protected void formatLoreAbbreviations(ItemStack itemstack, Map<String, String> abbreviations) {
        ItemMeta itemMeta = itemstack.getItemMeta();
        List<String> oldLore = itemMeta.getLore();
        for (int i = 0; i < oldLore.size(); i++) {
            for (String abbreviation : abbreviations.keySet()) {
                oldLore.set(i, oldLore.get(i).replace(abbreviation, abbreviations.get(abbreviation)));
            }
        }
        itemMeta.setLore(oldLore);
        itemstack.setItemMeta(itemMeta);
    }
}
