package dev.darcosse.common.cobblemonspawner

import dev.darcosse.common.cobblemonspawner.block.PokemonSpawnerBlock
import dev.darcosse.common.cobblemonspawner.block.PokemonSpawnerBlockEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.GameMasterBlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction

/**
 * Minimal registration callback, implemented by each platform
 * (vanilla `Registry.register` on Fabric, `RegisterEvent` helper on NeoForge).
 */
fun interface Registrar<T> {
    fun register(id: ResourceLocation, value: T)
}

/**
 * Builds the spawner BlockEntityType. Provided by each platform because the vanilla
 * `BlockEntityType.BlockEntitySupplier` is package-private on Fabric (public on NeoForge).
 */
fun interface BlockEntityTypeFactory {
    fun create(block: Block): BlockEntityType<PokemonSpawnerBlockEntity>
}

/**
 * Holds every registered object of the mod.
 *
 * Objects are created lazily inside the register calls so that NeoForge
 * instantiates them during its RegisterEvent, when registries are unfrozen.
 *
 * @author Darcosse
 * @version 1.0
 * @since 2026
 */
object CobblemonSpawnerRegistry {

    @JvmField
    val POKEMON_SPAWNER_ID: ResourceLocation = CobblemonSpawner.id("pokemon_spawner")

    lateinit var spawnerBlock: Block
        private set

    lateinit var spawnerItem: Item
        private set

    lateinit var spawnerBlockEntity: BlockEntityType<PokemonSpawnerBlockEntity>
        private set

    fun registerBlocks(registrar: Registrar<Block>) {
        spawnerBlock = PokemonSpawnerBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                // Unbreakable in survival, like a command block.
                .strength(-1.0f, 3_600_000.0f)
                .noLootTable()
                .sound(SoundType.METAL)
                .pushReaction(PushReaction.BLOCK)
        )
        registrar.register(POKEMON_SPAWNER_ID, spawnerBlock)
    }

    fun registerItems(registrar: Registrar<Item>) {
        // GameMasterBlockItem: only operators in creative can place it, like command blocks.
        spawnerItem = GameMasterBlockItem(spawnerBlock, Item.Properties().rarity(Rarity.EPIC))
        registrar.register(POKEMON_SPAWNER_ID, spawnerItem)
    }

    fun registerBlockEntities(registrar: Registrar<BlockEntityType<*>>, factory: BlockEntityTypeFactory) {
        spawnerBlockEntity = factory.create(spawnerBlock)
        registrar.register(POKEMON_SPAWNER_ID, spawnerBlockEntity)
    }
}
