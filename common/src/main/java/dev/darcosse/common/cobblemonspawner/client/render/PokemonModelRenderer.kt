package dev.darcosse.common.cobblemonspawner.client.render

import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Draws animated 3D Pokémon models inside GUI screens.
 *
 * Same call chain as JEC's `PokemonRenderer` (Cobblemon's `drawProfilePokemon`), extended with
 * aspects (shiny, regional forms) and a configurable yaw so the entry editor can spin the model.
 *
 * @author Darcosse
 * @version 1.0
 * @since 2026
 */
object PokemonModelRenderer {

    /** Downward tilt of the model, in degrees. Same value as JEC; 0 shows a flat side view. */
    private const val PITCH_DEGREES = 10f

    /** Default yaw, in degrees. Three-quarter view, same value as JEC. */
    const val DEFAULT_YAW_DEGREES = 35f

    /**
     * Depth at which the model is drawn. Must stay above the screen background and below
     * tooltips (drawn around z=400); JEC uses the same value.
     */
    private const val MODEL_Z = 200f

    /**
     * Divides the game time into Cobblemon's animation clock. Copied from JEC, where it gives
     * a slow idle animation; a smaller divisor makes models animate faster.
     */
    private const val ANIMATION_DIVISOR = 10_000f
    private const val ANIMATION_PERIOD_TICKS = 1000L

    /**
     * Vertical offset per unit of `Species.baseScale`, copied from JEC. Compensates for large
     * species whose model origin sits higher.
     */
    private const val BASE_SCALE_Y_FACTOR = 0.5f

    private val rotation = Quaternionf()
    private val euler = Vector3f()

    /**
     * Renders [species] with [aspects] anchored at ([centerX], [anchorY]).
     *
     * The anchor follows JEC's convention: JEC passes the top of its recipe area and the
     * model is drawn from there.
     *
     * @param state animation state; keep one instance per displayed model so animations don't mix
     * @param scale model scale; JEC uses 25 for its recipe area
     */
    fun render(
        graphics: GuiGraphics,
        species: Species,
        aspects: Set<String>,
        centerX: Float,
        anchorY: Float,
        scale: Float,
        state: FloatingState,
        yawDegrees: Float = DEFAULT_YAW_DEGREES
    ) {
        val poseStack = graphics.pose()
        poseStack.pushPose()
        poseStack.translate(centerX, anchorY + species.baseScale * BASE_SCALE_Y_FACTOR, MODEL_Z)

        val gameTime = Minecraft.getInstance().level?.gameTime ?: 0L
        val animationTicks = (gameTime % ANIMATION_PERIOD_TICKS).toFloat() / ANIMATION_DIVISOR

        drawProfilePokemon(
            renderablePokemon = RenderablePokemon(species, aspects),
            matrixStack = poseStack,
            rotation = rotation.identity().fromEulerXYZDegrees(euler.set(PITCH_DEGREES, yawDegrees, 0f)),
            state = state,
            partialTicks = animationTicks,
            scale = scale
        )

        poseStack.popPose()
    }
}
