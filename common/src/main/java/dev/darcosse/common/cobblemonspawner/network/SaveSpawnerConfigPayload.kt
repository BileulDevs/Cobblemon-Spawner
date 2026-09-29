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
 * Client -> server: saves the config edited in the GUI.
 *
 * @property reset also clears the spawner state and removes its current Pokémon
 */
class SaveSpawnerConfigPayload(
    val pos: BlockPos,
    val config: CompoundTag,
    val reset: Boolean
) : CustomPacketPayload {

    /** Identifies this payload for the platform networking layers. */
    override fun type(): CustomPacketPayload.Type<SaveSpawnerConfigPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<SaveSpawnerConfigPayload>(CobblemonSpawner.id("save_spawner_config"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, SaveSpawnerConfigPayload> = StreamCodec.of(
            StreamEncoder<FriendlyByteBuf, SaveSpawnerConfigPayload> { buf, payload ->
                // Must stay the first field: the reader checks it before anything else.
                buf.writeVarInt(SpawnerNetwork.PROTOCOL_VERSION)
                buf.writeBlockPos(payload.pos)
                buf.writeNbt(payload.config)
                buf.writeBoolean(payload.reset)
            },
            StreamDecoder<FriendlyByteBuf, SaveSpawnerConfigPayload> { buf ->
                SpawnerNetwork.readAndCheckVersion(buf)
                SaveSpawnerConfigPayload(buf.readBlockPos(), buf.readNbt() ?: CompoundTag(), buf.readBoolean())
            }
        )
    }
}
