package dev.darcosse.common.cobblemonspawner.client

import dev.darcosse.common.cobblemonspawner.client.gui.PokemonSpawnerScreen
import dev.darcosse.common.cobblemonspawner.network.OpenSpawnerScreenPayload
import dev.darcosse.common.cobblemonspawner.spawner.SpawnerConfig
import net.minecraft.client.Minecraft

/**
 * Client-only packet handling. Must only be referenced from client code paths.
 */
object ClientPacketHandler {

    fun openScreen(payload: OpenSpawnerScreenPayload) {
        Minecraft.getInstance().setScreen(
            PokemonSpawnerScreen(
                payload.pos,
                SpawnerConfig.fromTag(payload.config),
                payload.activeCount,
                payload.hasSpawned
            )
        )
    }
}
