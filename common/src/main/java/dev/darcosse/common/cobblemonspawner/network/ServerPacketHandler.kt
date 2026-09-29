package dev.darcosse.common.cobblemonspawner.network

import dev.darcosse.common.cobblemonspawner.block.PokemonSpawnerBlockEntity
import dev.darcosse.common.cobblemonspawner.spawner.SpawnerConfig
import dev.darcosse.common.cobblemonspawner.util.SpeciesValidator
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/**
 * Server-side handling of the packets sent by the configuration screen.
 * Never trust the client: permission, distance and values are all re-checked here.
 */
object ServerPacketHandler {

    private const val MAX_EDIT_DISTANCE_SQR = 64.0 * 64.0

    fun handleSave(payload: SaveSpawnerConfigPayload, player: ServerPlayer) {
        if (!player.canUseGameMasterBlocks()) return

        val level = player.serverLevel()
        val pos = payload.pos
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_EDIT_DISTANCE_SQR) return

        val spawner = level.getBlockEntity(pos) as? PokemonSpawnerBlockEntity ?: return

        val config = SpawnerConfig.fromTag(payload.config)
        config.sanitize()

        // Drop and report the entries that don't resolve to a species
        // (typo in the extra properties, species removed by a datapack, ...).
        val (valid, invalid) = config.entries.partition { SpeciesValidator.isValid(it.toPropertiesString()) }
        config.entries = valid.toMutableList()
        invalid.forEach {
            player.sendSystemMessage(
                Component.translatable("message.cobblemonspawner.invalid_entry", it.toPropertiesString())
                    .withStyle(ChatFormatting.YELLOW)
            )
        }

        spawner.applyConfig(config, payload.reset, level)

        val key = if (payload.reset) "message.cobblemonspawner.saved_reset" else "message.cobblemonspawner.saved"
        player.sendSystemMessage(Component.translatable(key, valid.size).withStyle(ChatFormatting.GREEN))
    }
}
