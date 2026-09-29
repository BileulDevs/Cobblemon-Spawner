package dev.darcosse.common.cobblemonspawner.spawner

/**
 * Keys written into `Pokemon.persistentData` of every Pokémon created by a spawner.
 * Stored on the Pokémon (not the entity) so they survive save/load.
 */
object SpawnerTags {
    /** Packed BlockPos (`BlockPos.asLong()`) of the spawner that created this Pokémon. */
    const val SPAWNER_POS = "cobblemonspawner:spawner_pos"

    /** Present and true when Poké Balls must bounce off this Pokémon. */
    const val UNCATCHABLE = "cobblemonspawner:uncatchable"
}
