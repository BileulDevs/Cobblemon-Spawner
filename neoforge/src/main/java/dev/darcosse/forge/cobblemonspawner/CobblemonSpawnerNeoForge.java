package dev.darcosse.forge.cobblemonspawner;

import dev.darcosse.common.cobblemonspawner.CobblemonSpawner;
import dev.darcosse.common.cobblemonspawner.CobblemonSpawnerRegistry;
import dev.darcosse.common.cobblemonspawner.block.PokemonSpawnerBlockEntity;
import dev.darcosse.forge.cobblemonspawner.network.NeoForgeNetworkHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Main mod entry point for the NeoForge platform.
 *
 * @author Darcosse
 * @version 1.0
 * @since 2026
 */
@Mod(CobblemonSpawner.MOD_ID)
public class CobblemonSpawnerNeoForge {

    public CobblemonSpawnerNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(this::onRegister);
        modEventBus.addListener(this::onBuildCreativeTabs);
        modEventBus.addListener(NeoForgeNetworkHandler.INSTANCE::register);

        NeoForgeNetworkHandler.INSTANCE.bindSenders();
        CobblemonSpawner.init();
    }

    /**
     * Objects are created inside the RegisterEvent, while registries are unfrozen.
     */
    private void onRegister(RegisterEvent event) {
        CobblemonSpawnerRegistry registry = CobblemonSpawnerRegistry.INSTANCE;
        event.register(Registries.BLOCK, helper -> registry.registerBlocks(helper::register));
        event.register(Registries.ITEM, helper -> registry.registerItems(helper::register));
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> registry.registerBlockEntities(
                helper::register,
                // BlockEntitySupplier is made public by NeoForge's own access transformer.
                block -> BlockEntityType.Builder.of(PokemonSpawnerBlockEntity::new, block).build(null)
        ));
    }

    /**
     * Same tab as command blocks ("Operator Utilities").
     */
    private void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(CobblemonSpawnerRegistry.INSTANCE.getSpawnerItem());
        }
    }
}
