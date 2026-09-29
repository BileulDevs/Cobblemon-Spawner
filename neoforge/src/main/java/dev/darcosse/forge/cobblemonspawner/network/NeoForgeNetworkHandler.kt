package dev.darcosse.forge.cobblemonspawner.network

import dev.darcosse.common.cobblemonspawner.client.ClientPacketHandler
import dev.darcosse.common.cobblemonspawner.network.OpenSpawnerScreenPayload
import dev.darcosse.common.cobblemonspawner.network.SaveSpawnerConfigPayload
import dev.darcosse.common.cobblemonspawner.network.ServerPacketHandler
import dev.darcosse.common.cobblemonspawner.network.SpawnerNetwork
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * Registers payloads and handlers for the NeoForge platform.
 *
 * Unlike Just Enough Cobblemon, the registrar is NOT optional: the spawner adds a block,
 * so the mod has to be present on both sides anyway.
 *
 * @author Darcosse
 * @version 1.0
 * @since 2026
 */
object NeoForgeNetworkHandler {

    fun register(event: RegisterPayloadHandlersEvent) {
        // The registrar version makes NeoForge reject mismatched clients at login, before any payload is sent.
        val registrar = event.registrar(SpawnerNetwork.PROTOCOL_VERSION.toString())

        registrar.playToClient(OpenSpawnerScreenPayload.TYPE, OpenSpawnerScreenPayload.STREAM_CODEC) { payload, context ->
            // The lambda body is only executed on the client, so ClientPacketHandler never loads on a server.
            context.enqueueWork { ClientPacketHandler.openScreen(payload) }
        }

        registrar.playToServer(SaveSpawnerConfigPayload.TYPE, SaveSpawnerConfigPayload.STREAM_CODEC) { payload, context ->
            context.enqueueWork { ServerPacketHandler.handleSave(payload, context.player() as ServerPlayer) }
        }
    }

    /**
     * PacketDistributor works on both sides, so both senders can be bound right away.
     */
    fun bindSenders() {
        SpawnerNetwork.toPlayer = { player, payload -> PacketDistributor.sendToPlayer(player, payload) }
        SpawnerNetwork.toServer = { payload -> PacketDistributor.sendToServer(payload) }
    }
}
