package dev.darcosse.common.cobblemonspawner.util

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import net.minecraft.resources.ResourceLocation

/**
 * Checks that a properties string resolves to an existing species,
 * so typos in the GUI are reported instead of silently spawning a default Pokémon.
 */
object SpeciesValidator {

    fun isValid(properties: String): Boolean = try {
        val species = PokemonProperties.parse(properties).species
        when {
            species == null -> false
            species.equals("random", ignoreCase = true) -> true
            ':' in species -> PokemonSpecies.getByIdentifier(ResourceLocation.parse(species.lowercase())) != null
            else -> PokemonSpecies.getByName(species.lowercase()) != null
        }
    } catch (e: Exception) {
        false
    }
}
