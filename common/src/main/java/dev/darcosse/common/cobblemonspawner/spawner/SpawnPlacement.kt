package dev.darcosse.common.cobblemonspawner.spawner

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags

/**
 * Where a pool entry is allowed to appear around the spawner.
 *
 * The rules mirror Cobblemon's natural spawn position types (`grounded`, `submerged`,
 * `surface`, `seafloor`, `lavafloor`) but are evaluated with plain vanilla block/fluid checks,
 * so a map maker gets predictable results. [AIR] has no Cobblemon equivalent.
 *
 * Each rule only checks the blocks around the feet position; the caller still checks
 * that the entity bounding box does not collide with blocks.
 */
enum class SpawnPlacement {
    /** Resolved at spawn time from the species' natural spawns, see [NaturalPlacementResolver]. */
    AUTO,

    /** Solid block below, feet out of any liquid. The behavior of versions before presets. */
    GROUND,

    /** Water below, at the feet and above: fully underwater, at any depth. */
    SUBMERGED,

    /** Water below, air at the feet: floating on the water surface. */
    SURFACE,

    /** Solid block below, water at the feet: resting on the sea floor. */
    SEAFLOOR,

    /** Solid block below, lava at the feet: resting on the bottom of a lava pool. */
    LAVA,

    /** Air below and at the feet: hovering, never on the ground. */
    AIR;

    /** Translation key of the label shown on the placement cycle button. */
    val translationKey: String get() = "gui.cobblemonspawner.entry.placement.${name.lowercase()}"

    /**
     * True when the Pokémon may overlap liquids. Land and air presets refuse any liquid in the
     * bounding box, otherwise a GROUND Pokémon could stand in a one-block puddle.
     */
    val allowsLiquid: Boolean get() = this == SUBMERGED || this == SEAFLOOR || this == LAVA

    /**
     * True when candidate heights should be tried in random order instead of top-down.
     * Top-down is right for floor-based presets (it finds the walkable top surface), but
     * would always stack underwater / airborne Pokémon at the highest possible block.
     */
    val spreadsVertically: Boolean get() = this == SUBMERGED || this == AIR

    /**
     * Checks the blocks around [feet] against this preset. Must not be called on [AUTO].
     */
    fun fits(level: ServerLevel, feet: BlockPos): Boolean {
        val below = feet.below()
        return when (this) {
            AUTO -> error("AUTO must be resolved before placement")
            GROUND -> isSolidFloor(level, below)
            SUBMERGED -> isWater(level, below) && isWater(level, feet) && isWater(level, feet.above())
            SURFACE -> isWater(level, below) && level.getBlockState(feet).isAir
            SEAFLOOR -> isSolidFloor(level, below) && isWater(level, feet)
            LAVA -> isSolidFloor(level, below) && level.getFluidState(feet).`is`(FluidTags.LAVA)
            AIR -> level.getBlockState(below).isAir && level.getBlockState(feet).isAir
        }
    }

    private fun isSolidFloor(level: ServerLevel, pos: BlockPos): Boolean =
        level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)

    private fun isWater(level: ServerLevel, pos: BlockPos): Boolean =
        level.getFluidState(pos).`is`(FluidTags.WATER)
}
