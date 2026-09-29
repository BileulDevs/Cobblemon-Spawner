package dev.darcosse.fabric.cobblemonspawner;

import dev.darcosse.common.cobblemonspawner.CobblemonSpawner;
import dev.darcosse.common.cobblemonspawner.CobblemonSpawnerRegistry;
import dev.darcosse.common.cobblemonspawner.block.PokemonSpawnerBlockEntity;
import dev.darcosse.fabric.cobblemonspawner.network.FabricNetworkHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;

/**
 * Main mod entry point for the Fabric platform.
 *
 * @author Darcosse
 * @version 1.0
 * @since 2026
 */
public class CobblemonSpawnerFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CobblemonSpawnerRegistry registry = CobblemonSpawnerRegistry.INSTANCE;
        registry.registerBlocks((id, block) -> Registry.register(BuiltInRegistries.BLOCK, id, block));
        registry.registerItems((id, item) -> Registry.register(BuiltInRegistries.ITEM, id, item));
        registry.registerBlockEntities(
                (id, type) -> Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type),
                block -> FabricBlockEntityTypeBuilder.create(PokemonSpawnerBlockEntity::new, block).build()
        );

        // Same tab as command blocks ("Operator Utilities").
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.OP_BLOCKS)
                .register(entries -> entries.accept(registry.getSpawnerItem()));

        FabricNetworkHandler.INSTANCE.registerCommon();
        CobblemonSpawner.init();
    }
}
