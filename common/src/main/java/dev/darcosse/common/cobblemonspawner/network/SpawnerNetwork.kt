package dev.darcosse.common.cobblemonspawner.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer

/**
 * Platform bridge for sending packets from common code.
 * Each platform assigns the two senders during its initialization.
 */
object SpawnerNetwork {

    /**
     * Version of the payload layouts, written as the first field of every payload.
     * - 1: no version field (initial release).
     * - 2: version field added; the embedded config uses SpawnerConfig format 2.
     * - 3: embedded config uses SpawnerConfig format 3 (per-entry placement).
     * - 4: embedded config uses SpawnerConfig format 4 (per-entry time of day).
     * - 5: embedded config uses SpawnerConfig format 5 (per-entry weather).
     * Bump it on every payload field change. Also used as the NeoForge registrar version.
     */
    const val PROTOCOL_VERSION = 5

    /**
     * Reads and checks the protocol version at the start of a payload.
     * Fails loudly: a mismatch means client and server run different mod versions.
     */
    fun readAndCheckVersion(buf: FriendlyByteBuf) {
        val version = buf.readVarInt()
        check(version == PROTOCOL_VERSION) {
            "Cobblemon Spawner protocol mismatch: received $version, expected $PROTOCOL_VERSION. Client and server must use the same mod version."
        }
    }

    /** Server -> client. Set by the platform on both physical sides. */
    lateinit var toPlayer: (ServerPlayer, CustomPacketPayload) -> Unit

    /** Client -> server. Set by the platform on the physical client only. */
    lateinit var toServer: (CustomPacketPayload) -> Unit

    /** Sends [payload] to one player through the platform sender. */
    fun sendToPlayer(player: ServerPlayer, payload: CustomPacketPayload) = toPlayer(player, payload)

    /** Sends [payload] to the server through the platform sender. Client only. */
    fun sendToServer(payload: CustomPacketPayload) = toServer(payload)
}
