package dev.darcosse.common.cobblemonspawner.network

import dev.darcosse.common.cobblemonspawner.CobblemonSpawner
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Server -> client: opens the configuration screen of the spawner at [pos].
 *
 * @property config the current spawner config (see SpawnerConfig.toTag)
 * @property activeCount number of Pokémon from this spawner currently alive
 * @property hasSpawned whether the spawner already spawned at least once
 */
class OpenSpawnerScreenPayload(
    val pos: BlockPos,
    val config: CompoundTag,
    val activeCount: Int,
    val hasSpawned: Boolean
) : CustomPacketPayload {

    /** Identifies this payload for the platform networking layers. */
    override fun type(): CustomPacketPayload.Type<OpenSpawnerScreenPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<OpenSpawnerScreenPayload>(CobblemonSpawner.id("open_spawner_screen"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, OpenSpawnerScreenPayload> = StreamCodec.of(
            StreamEncoder<FriendlyByteBuf, OpenSpawnerScreenPayload> { buf, payload ->
                // Must stay the first field: the reader checks it before anything else.
                buf.writeVarInt(SpawnerNetwork.PROTOCOL_VERSION)
                buf.writeBlockPos(payload.pos)
                buf.writeNbt(payload.config)
                buf.writeVarInt(payload.activeCount)
                buf.writeBoolean(payload.hasSpawned)
            },
            StreamDecoder<FriendlyByteBuf, OpenSpawnerScreenPayload> { buf ->
                SpawnerNetwork.readAndCheckVersion(buf)
                OpenSpawnerScreenPayload(buf.readBlockPos(), buf.readNbt() ?: CompoundTag(), buf.readVarInt(), buf.readBoolean())
            }
        )
    }
}
