package dev.darcosse.fabric.cobblemonspawner.network

import dev.darcosse.common.cobblemonspawner.client.ClientPacketHandler
import dev.darcosse.common.cobblemonspawner.network.OpenSpawnerScreenPayload
import dev.darcosse.common.cobblemonspawner.network.SaveSpawnerConfigPayload
import dev.darcosse.common.cobblemonspawner.network.ServerPacketHandler
import dev.darcosse.common.cobblemonspawner.network.SpawnerNetwork
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

/**
 * Registers payloads and handlers for the Fabric platform.
 *
 * @author Darcosse
 * @version 1.0
 * @since 2026
 */
object FabricNetworkHandler {

    /**
     * Payload types + server receiver. Runs on both physical sides.
     */
    fun registerCommon() {
        PayloadTypeRegistry.playS2C().register(OpenSpawnerScreenPayload.TYPE, OpenSpawnerScreenPayload.STREAM_CODEC)
        PayloadTypeRegistry.playC2S().register(SaveSpawnerConfigPayload.TYPE, SaveSpawnerConfigPayload.STREAM_CODEC)

        ServerPlayNetworking.registerGlobalReceiver(SaveSpawnerConfigPayload.TYPE) { payload, context ->
            context.server().execute { ServerPacketHandler.handleSave(payload, context.player()) }
        }

        SpawnerNetwork.toPlayer = { player, payload -> ServerPlayNetworking.send(player, payload) }
    }

    /**
     * Client receiver. Physical client only.
     */
    fun registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenSpawnerScreenPayload.TYPE) { payload, context ->
            context.client().execute { ClientPacketHandler.openScreen(payload) }
        }

        SpawnerNetwork.toServer = { payload -> ClientPlayNetworking.send(payload) }
    }
}
