package dev.fugainukai.gemstones;

import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;

/** Runs in a real singleplayer world: validates registration, data packs and iron-equivalent stats. */
public class GemstoneClientTest implements FabricClientGameTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var registries = server.registryAccess();
                var plains = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS).value();
                for (String gem : List.of("ruby", "sapphire", "emerald", "amethyst")) {
                    Item material = BuiltInRegistries.ITEM.getValue(GemstoneOres.id(gem));
                    require(material != Items.AIR, "Missing gem: " + gem);
                    for (String recipe : List.of(gem + "_sword", gem + "_pickaxe", gem + "_axe", gem + "_shovel",
                            gem + "_hoe", gem + "_helmet", gem + "_chestplate", gem + "_leggings", gem + "_boots",
                            gem + "_block", gem + "_from_block", "iron_from_" + gem)) {
                        require(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, GemstoneOres.id(recipe))).isPresent(),
                                "Recipe did not load: " + recipe);
                    }
                    for (String suffix : List.of("sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots")) {
                        Item custom = BuiltInRegistries.ITEM.getValue(GemstoneOres.id(gem + "_" + suffix));
                        Item iron = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("iron_" + suffix));
                        require(custom != Items.AIR, "Missing equipment: " + gem + "_" + suffix);
                        require(Objects.equals(custom.components().get(DataComponents.MAX_DAMAGE), iron.components().get(DataComponents.MAX_DAMAGE)),
                                "Durability differs from iron: " + gem + "_" + suffix);
                        require(Objects.equals(custom.components().get(DataComponents.ATTRIBUTE_MODIFIERS), iron.components().get(DataComponents.ATTRIBUTE_MODIFIERS)),
                                "Combat stats differ from iron: " + gem + "_" + suffix);
                        require(Objects.equals(custom.components().get(DataComponents.ENCHANTABLE), iron.components().get(DataComponents.ENCHANTABLE)),
                                "Enchantability differs from iron: " + gem + "_" + suffix);
                    }
                    var pickaxe = BuiltInRegistries.ITEM.getValue(GemstoneOres.id(gem + "_pickaxe"));
                    require(pickaxe.builtInRegistryHolder().is(ItemTags.PICKAXES), "Missing pickaxe tag: " + gem);
                    var helmet = BuiltInRegistries.ITEM.getValue(GemstoneOres.id(gem + "_helmet"));
                    require(helmet.builtInRegistryHolder().is(ItemTags.HEAD_ARMOR), "Missing armor tag: " + gem);
                    for (String prefix : List.of("", "deepslate_")) {
                        var block = BuiltInRegistries.BLOCK.getValue(GemstoneOres.id(prefix + gem + "_ore"));
                        require(block.defaultBlockState().is(BlockTags.NEEDS_IRON_TOOL), "Ore must require iron: " + gem);
                    }
                    for (String pass : List.of("", "_medium", "_large", "_buried")) {
                        var key = ResourceKey.create(Registries.PLACED_FEATURE, GemstoneOres.id("ore_" + gem + pass));
                        var feature = registries.lookupOrThrow(Registries.PLACED_FEATURE).getOrThrow(key).value();
                        require(plains.getGenerationSettings().hasFeature(feature), "Ore generation missing: " + key);
                    }
                }
            });
            context.takeScreenshot("gemstone-ores-world-loaded");
        }
    }
}
