package dev.darcosse.common.cobblemonspawner.client.render;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Java bridge to the Cobblemon species registry, used by the species picker.
 * <p>
 * Written in Java on purpose: these exact calls ({@code PokemonSpecies.getSpecies()},
 * {@code Species.getTranslatedName()}, {@code Species.showdownId()}) are already compiled
 * this way in Just Enough Cobblemon. From Kotlin, whether they are properties or functions
 * would have to be guessed.
 *
 * @author Darcosse
 * @version 1.0
 * @since 2026
 */
public final class SpeciesCatalog {

    private SpeciesCatalog() {
    }

    /**
     * Returns every registered species, sorted by translated name in the current client language.
     */
    public static List<Species> sortedByName() {
        List<Species> list = new ArrayList<>(PokemonSpecies.getSpecies());
        list.sort(Comparator.comparing((Species species) -> species.getTranslatedName().getString()));
        return list;
    }

    /**
     * Returns the translated, displayable name of a species.
     */
    public static Component displayName(Species species) {
        return species.getTranslatedName();
    }

    /**
     * Returns the id stored in spawn entries. {@code PokemonSpecies.getByName} accepts it back,
     * as JEC's drop category already relies on.
     */
    public static String id(Species species) {
        return species.showdownId();
    }
}
