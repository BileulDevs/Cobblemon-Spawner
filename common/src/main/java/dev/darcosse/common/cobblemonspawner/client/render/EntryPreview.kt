package dev.darcosse.common.cobblemonspawner.client.render

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import dev.darcosse.common.cobblemonspawner.spawner.ShinyMode
import dev.darcosse.common.cobblemonspawner.spawner.SpawnEntry
import net.minecraft.network.chat.Component

/**
 * Everything a screen needs to display a spawn entry: resolved species, display name and
 * model aspects. Built once per entry change, never per frame, because parsing the extra
 * properties allocates.
 *
 * @property species null when the stored id no longer resolves (typo, datapack removed)
 */
class EntryPreview private constructor(
    val species: Species?,
    val displayName: Component,
    val aspects: Set<String>
) {
    companion object {
        /** Cobblemon aspect that switches a model to its shiny texture. */
        private const val SHINY_ASPECT = "shiny"

        /**
         * Resolves the species and computes the aspects (extra properties + forced shiny) of [entry].
         */
        fun of(entry: SpawnEntry): EntryPreview {
            val species = PokemonSpecies.getByName(entry.species.lowercase())
            val name = species?.let { SpeciesCatalog.displayName(it) } ?: Component.literal(entry.species)
            return EntryPreview(species, name, aspectsFor(entry.extraProperties, entry.shiny))
        }

        /**
         * Aspects written in [extraProperties] (e.g. `alolan`), plus `shiny` when forced.
         * An unparsable string simply yields no aspect: the preview must never crash the screen.
         */
        fun aspectsFor(extraProperties: String, shiny: ShinyMode): Set<String> {
            val aspects = try {
                if (extraProperties.isBlank()) mutableSetOf() else PokemonProperties.parse(extraProperties).aspects.toMutableSet()
            } catch (e: Exception) {
                mutableSetOf()
            }
            if (shiny == ShinyMode.ALWAYS) aspects += SHINY_ASPECT
            return aspects
        }
    }
}
