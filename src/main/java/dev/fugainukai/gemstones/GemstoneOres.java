package dev.fugainukai.gemstones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.equipment.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.GenerationStep;

public final class GemstoneOres implements ModInitializer {
    public static final String MOD_ID = "gemstone_ores";
    private static final List<Item> INGREDIENTS = new ArrayList<>();
    private static final List<Item> EQUIPMENT = new ArrayList<>();
    private static final List<Item> BLOCK_ITEMS = new ArrayList<>();

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override
    public void onInitialize() {
        for (String gem : List.of("ruby", "sapphire", "emerald", "amethyst")) {
            INGREDIENTS.add(item(gem, Item::new, new Item.Properties()));
            block(gem + "_ore", Blocks.DIAMOND_ORE, true);
            block("deepslate_" + gem + "_ore", Blocks.DEEPSLATE_DIAMOND_ORE, true);
            block(gem + "_block", Blocks.IRON_BLOCK, false);

            TagKey<Item> repair = TagKey.create(Registries.ITEM, id("repairs_" + gem));
            ToolMaterial material = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL,
                    250, 6.0F, 2.0F, 14, repair);
            EQUIPMENT.add(item(gem + "_sword", Item::new, new Item.Properties().sword(material, 3.0F, -2.4F)));
            EQUIPMENT.add(item(gem + "_pickaxe", Item::new, new Item.Properties().pickaxe(material, 1.0F, -2.8F)));
            EQUIPMENT.add(item(gem + "_axe", p -> new AxeItem(material, 6.0F, -3.1F, p), new Item.Properties()));
            EQUIPMENT.add(item(gem + "_shovel", p -> new ShovelItem(material, 1.5F, -3.0F, p), new Item.Properties()));
            EQUIPMENT.add(item(gem + "_hoe", p -> new HoeItem(material, -2.0F, -1.0F, p), new Item.Properties()));

            ArmorMaterial armor = new ArmorMaterial(15,
                    Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 6, ArmorType.LEGGINGS, 5, ArmorType.BOOTS, 2),
                    9, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, 0.0F, repair,
                    ResourceKey.create(EquipmentAssets.ROOT_ID, id(gem)));
            for (ArmorType type : List.of(ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS)) {
                EQUIPMENT.add(item(gem + "_" + type.getSerializedName(), Item::new,
                        new Item.Properties().humanoidArmor(armor, type)));
            }
            for (String pass : List.of("", "_medium", "_large", "_buried")) {
                BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                        GenerationStep.Decoration.UNDERGROUND_ORES,
                        ResourceKey.create(Registries.PLACED_FEATURE, id("ore_" + gem + pass)));
            }
        }
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(e -> INGREDIENTS.forEach(e::accept));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(e -> EQUIPMENT.stream()
                .filter(i -> !BuiltInRegistries.ITEM.getKey(i).getPath().matches(".*_(pickaxe|axe|shovel|hoe)$")).forEach(e::accept));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> EQUIPMENT.stream()
                .filter(i -> BuiltInRegistries.ITEM.getKey(i).getPath().matches(".*_(pickaxe|axe|shovel|hoe)$")).forEach(e::accept));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(e -> BLOCK_ITEMS.forEach(e::accept));
    }

    private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    private static void block(String name, Block template, boolean ore) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(template).setId(key);
        Block block = ore ? new DropExperienceBlock(UniformInt.of(3, 7), properties) : new Block(properties);
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        BLOCK_ITEMS.add(item(name, p -> new BlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix()));
    }
}
