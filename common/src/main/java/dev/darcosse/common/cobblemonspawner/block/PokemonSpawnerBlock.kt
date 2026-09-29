package dev.darcosse.common.cobblemonspawner.block

import com.mojang.serialization.MapCodec
import dev.darcosse.common.cobblemonspawner.CobblemonSpawnerRegistry
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * The Pokémon spawner block. Right-click (operator in creative) opens the configuration screen.
 * Spawning logic lives in [PokemonSpawnerBlockEntity].
 *
 * @author Darcosse
 * @version 1.0
 * @since 2026
 */
class PokemonSpawnerBlock(properties: BlockBehaviour.Properties) : BaseEntityBlock(properties) {

    // Built from an instance member so it works whatever the visibility of simpleCodec.
    private val codec: MapCodec<PokemonSpawnerBlock> by lazy { simpleCodec(::PokemonSpawnerBlock) }

    override fun codec(): MapCodec<out BaseEntityBlock> = codec

    // BaseEntityBlock defaults to INVISIBLE.
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        PokemonSpawnerBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (level.isClientSide || type !== CobblemonSpawnerRegistry.spawnerBlockEntity) return null
        return BlockEntityTicker { tickLevel, _, _, blockEntity ->
            (blockEntity as? PokemonSpawnerBlockEntity)?.serverTick(tickLevel as ServerLevel)
        }
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        // Same permission rule as command blocks: op level 2 + creative.
        if (!player.canUseGameMasterBlocks()) return InteractionResult.PASS

        if (level is ServerLevel && player is ServerPlayer) {
            val spawner = level.getBlockEntity(pos) as? PokemonSpawnerBlockEntity ?: return InteractionResult.PASS
            spawner.openScreen(player, level)
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        // Breaking the spawner removes the wild Pokémon it created (handy while building a map).
        if (!state.`is`(newState.block) && level is ServerLevel) {
            (level.getBlockEntity(pos) as? PokemonSpawnerBlockEntity)?.despawnSpawned(level)
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }
}
