package dev.darcosse.common.cobblemonspawner.spawner

import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import dev.darcosse.common.cobblemonspawner.CobblemonSpawner

/**
 * Resolves [SpawnPlacement.AUTO] from Cobblemon's world spawn pool: the most frequent
 * spawn position type among the natural spawns of the species. Server side only (the
 * spawn pool is not synced to clients).
 *
 * The pool is scanned on every call instead of being cached: Cobblemon refills the same
 * list instance on datapack reload (`details.clear()` + `addAll`), so an identity-based
 * cache would go stale silently. A scan is a few thousand cheap checks and only happens
 * when an AUTO entry actually spawns (at most once per spawn delay per spawner).
 */
object NaturalPlacementResolver {

    /**
     * Cobblemon spawn position type names, as registered by its context calculators
     * (`FlooredContextCalculators.kt`, `SubmergedContextCalculators.kt`).
     * `fishing` spawns are fished out of water, hence SUBMERGED for a spawner.
     */
    private val COBBLEMON_TYPE_TO_PLACEMENT = mapOf(
        "grounded" to SpawnPlacement.GROUND,
        "submerged" to SpawnPlacement.SUBMERGED,
        "surface" to SpawnPlacement.SURFACE,
        "seafloor" to SpawnPlacement.SEAFLOOR,
        "lavafloor" to SpawnPlacement.LAVA,
        "fishing" to SpawnPlacement.SUBMERGED
    )

    /** Unknown type names already logged, so an unexpected name warns once, not per spawn. */
    private val loggedUnknownTypes = mutableSetOf<String>()

    /**
     * Returns the natural placement of [speciesId] (a `showdownId`), or [SpawnPlacement.GROUND]
     * when the species has no natural spawn (legendaries, datapack-only species...).
     */
    fun resolve(speciesId: String): SpawnPlacement {
        val target = normalize(speciesId)
        val counts = mutableMapOf<SpawnPlacement, Int>()

        for (detail in CobblemonSpawnPools.WORLD_SPAWN_POOL.details) {
            if (detail !is PokemonSpawnDetail) continue
            val species = detail.pokemon.species ?: continue
            if (normalize(species) != target) continue

            val typeName = runCatching { detail.spawnablePositionType.name }.getOrNull() ?: continue
            val placement = COBBLEMON_TYPE_TO_PLACEMENT[typeName.lowercase()]
            if (placement == null) {
                if (loggedUnknownTypes.add(typeName)) {
                    CobblemonSpawner.LOGGER.warn("Unknown Cobblemon spawn position type '{}', ignored by AUTO placement", typeName)
                }
                continue
            }
            counts.merge(placement, 1, Int::plus)
        }

        return counts.maxByOrNull { it.value }?.key ?: SpawnPlacement.GROUND
    }

    /**
     * Makes spawn-file species names comparable with showdown ids:
     * drops a namespace (`cobblemon:pikachu`) and any non-alphanumeric character (`mr. mime`).
     */
    private fun normalize(species: String): String =
        species.substringAfter(':').lowercase().filter { it.isLetterOrDigit() }
}
