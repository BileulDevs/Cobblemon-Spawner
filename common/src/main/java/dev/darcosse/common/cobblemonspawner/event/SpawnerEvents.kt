package dev.darcosse.common.cobblemonspawner.event

import com.cobblemon.mod.common.api.events.CobblemonEvents
import dev.darcosse.common.cobblemonspawner.spawner.SpawnerTags
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Cobblemon event hooks used by spawned Pokémon.
 */
object SpawnerEvents {

    fun register() {
        // Uncatchable Pokémon: cancel the capture as soon as the ball hits.
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe { event ->
            val data = event.pokemon.pokemon.persistentData
            if (data.getBoolean(SpawnerTags.UNCATCHABLE)) {
                event.cancel()
                (event.pokeBall.owner as? ServerPlayer)?.sendSystemMessage(
                    Component.translatable("message.cobblemonspawner.uncatchable").withStyle(ChatFormatting.RED)
                )
            }
        }
    }
}
