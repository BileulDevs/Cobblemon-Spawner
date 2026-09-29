package dev.darcosse.common.cobblemonspawner.client.gui

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import dev.darcosse.common.cobblemonspawner.client.render.EntryPreview
import dev.darcosse.common.cobblemonspawner.client.render.PokemonModelRenderer
import dev.darcosse.common.cobblemonspawner.network.SaveSpawnerConfigPayload
import dev.darcosse.common.cobblemonspawner.network.SpawnerNetwork
import dev.darcosse.common.cobblemonspawner.spawner.RedstoneMode
import dev.darcosse.common.cobblemonspawner.spawner.ShinyMode
import dev.darcosse.common.cobblemonspawner.spawner.SpawnerConfig
import dev.darcosse.common.cobblemonspawner.spawner.TimeCondition
import dev.darcosse.common.cobblemonspawner.spawner.WeatherCondition
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import kotlin.math.max

/**
 * Main configuration screen of the Pokémon spawner.
 *
 * Top: the pool, as a scrollable grid of cards showing each Pokémon's 3D model, weight and
 * level range. Clicking a card edits it in [PokemonEntryScreen], the last card adds a new one.
 * Bottom: spawner-wide settings and the action buttons.
 *
 * Closing the screen (Escape or Done) saves automatically; only "Save & reset" and the
 * explicit discard (Cancel) need a click.
 *
 * Client only.
 *
 * @author Darcosse
 * @version 2.0
 * @since 2026
 */
class PokemonSpawnerScreen(
    private val pos: BlockPos,
    private val config: SpawnerConfig,
    private val activeCount: Int,
    private val hasSpawned: Boolean
) : Screen(Component.translatable("screen.cobblemonspawner.title")) {

    /**
     * Binding between a numeric text field and a config property.
     */
    private class NumberField(
        val key: String,
        val get: (SpawnerConfig) -> Int,
        val set: (SpawnerConfig, Int) -> Unit
    )

    private var left = 0
    private var top = 0

    /**
     * Config as received from the server. Closing only sends a save when something differs:
     * every save resets the spawn cooldown server-side, so merely opening and closing the
     * screen must not trigger an immediate spawn.
     */
    private val initialTag = config.toTag()

    /** First visible card row of the pool grid. */
    private var scrollRow = 0

    /** Resolved species/aspects per entry, rebuilt only when the pool changes. */
    private var previews: List<EntryPreview> = emptyList()

    /** Draggable scrollbar of the pool grid. */
    private val scrollbar = Scrollbar()

    /** One animation state per card, so animations of different species don't interfere. */
    private val cardStates = mutableMapOf<Int, FloatingState>()

    private val numberBoxes = mutableListOf<EditBox>()
    private var redstoneButton: CycleButton<RedstoneMode>? = null
    private var onceButton: CycleButton<Boolean>? = null
    private var uncatchableButton: CycleButton<Boolean>? = null
    private var persistentButton: CycleButton<Boolean>? = null
    private var noAiButton: CycleButton<Boolean>? = null

    /**
     * Builds the widgets. Also runs on resize and when coming back from the entry editor,
     * hence the capture of the current values first.
     */
    override fun init() {
        captureValues()
        numberBoxes.clear()
        refreshPreviews()

        left = (width - PANEL_WIDTH) / 2
        top = max(MIN_TOP_MARGIN, (height - PANEL_HEIGHT) / 2)

        NUMBER_FIELDS.forEachIndexed { index, field ->
            val (x, y) = numberFieldPos(index)
            val box = EditBox(font, x + NUMBER_BOX_OFFSET, y, NUMBER_BOX_WIDTH, FIELD_HEIGHT, tr("gui.cobblemonspawner.field.${field.key}")).apply {
                setMaxLength(NUMBER_MAX_LENGTH)
                setFilter { text -> text.all { it.isDigit() } }
                value = field.get(config).toString()
                setTooltip(Tooltip.create(tr("gui.cobblemonspawner.field.${field.key}.tooltip")))
            }
            numberBoxes += addRenderableWidget(box)
        }

        val row1 = top + TOGGLES_TOP
        val row2 = row1 + TOGGLE_ROW_STEP

        // The time of day moved to each entry (format 4): five spawner-wide toggles remain.
        redstoneButton = addRenderableWidget(
            CycleButton.builder<RedstoneMode> { Component.translatable(it.translationKey) }
                .withValues(RedstoneMode.entries)
                .withInitialValue(config.redstone)
                .create(column(0), row1, CELL_WIDTH, TOGGLE_HEIGHT, tr("gui.cobblemonspawner.redstone"))
        )
        onceButton = addRenderableWidget(toggle("once_only", config.onceOnly, column(1), row1))
        uncatchableButton = addRenderableWidget(toggle("uncatchable", config.uncatchable, column(2), row1))
        persistentButton = addRenderableWidget(toggle("persistent", config.persistent, column(0), row2))
        noAiButton = addRenderableWidget(toggle("no_ai", config.noAi, column(1), row2))

        val actionsY = top + ACTIONS_TOP
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE) { onClose() }
                .bounds(column(0), actionsY, CELL_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(tr("gui.cobblemonspawner.done.tooltip")))
                .build()
        )
        addRenderableWidget(
            Button.builder(tr("gui.cobblemonspawner.save_reset")) { saveAndReset() }
                .bounds(column(1), actionsY, CELL_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(tr("gui.cobblemonspawner.save_reset.tooltip")))
                .build()
        )
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL) { closeWithoutSaving() }
                .bounds(column(2), actionsY, CELL_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(tr("gui.cobblemonspawner.cancel.tooltip")))
                .build()
        )

        clampScroll()
    }

    /**
     * Draws the widgets (via super), then the texts and the pool grid on top.
     */
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)

        graphics.drawString(font, title, left, top + TITLE_Y, COLOR_TITLE)
        val status = Component.translatable(
            "gui.cobblemonspawner.status",
            activeCount,
            if (hasSpawned) CommonComponents.GUI_YES else CommonComponents.GUI_NO
        )
        graphics.drawString(font, status, left + PANEL_WIDTH - font.width(status), top + TITLE_Y, COLOR_SECONDARY)

        renderGrid(graphics, mouseX, mouseY)

        NUMBER_FIELDS.forEachIndexed { index, field ->
            val (x, y) = numberFieldPos(index)
            graphics.drawString(font, tr("gui.cobblemonspawner.field.${field.key}"), x, y + LABEL_Y_OFFSET, COLOR_LABEL)
        }
    }

    /**
     * Handles clicks on the pool grid: delete cross, edit card, or the add card.
     * Anything else goes to the regular widgets.
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == LEFT_BUTTON) {
            scrollbar.press(mouseX, mouseY, gridTrack(), scrollRow)?.let {
                scrollRow = it
                return true
            }
            val slot = slotAt(mouseX, mouseY)
            if (slot != null) {
                val entries = config.entries
                when {
                    slot == entries.size -> openEditor(null)
                    slot < entries.size && isOnDeleteCross(slot, mouseX, mouseY) -> {
                        entries.removeAt(slot)
                        refreshPreviews()
                        clampScroll()
                    }
                    slot < entries.size -> openEditor(slot)
                }
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /**
     * Moves the grid while its scrollbar is dragged; other drags go to the widgets.
     */
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        scrollbar.drag(mouseY, gridTrack())?.let {
            scrollRow = it
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    /**
     * Ends a scrollbar drag; other releases go to the widgets.
     */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (scrollbar.release()) return true
        return super.mouseReleased(mouseX, mouseY, button)
    }

    /**
     * Scrolls the pool grid one row per wheel notch when the mouse is over it.
     */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (isInGrid(mouseX, mouseY)) {
            scrollRow -= scrollY.toInt().coerceIn(-1, 1)
            clampScroll()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    /** The world keeps running behind the screen, like vanilla block editors. */
    override fun isPauseScreen(): Boolean = false

    /**
     * Escape and Done: saves the edits (if any), then closes. Escape used to discard
     * everything, which lost work on a stray key press.
     *
     * Opening the entry editor does not go through here (it replaces the screen, which only
     * calls `removed()`), so switching to the editor never saves half-way.
     */
    override fun onClose() {
        captureValues()
        val tag = config.toTag()
        if (tag != initialTag) {
            SpawnerNetwork.sendToServer(SaveSpawnerConfigPayload(pos, tag, false))
        }
        closeWithoutSaving()
    }

    // ---------------------------------------------------------------- pool grid

    /**
     * Draws the visible cards plus the "add" card, clipped to the grid area.
     */
    private fun renderGrid(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val gridBottom = top + GRID_TOP + gridHeight()
        graphics.fill(left - 1, top + GRID_TOP - 1, left + PANEL_WIDTH + 1, gridBottom + 1, COLOR_GRID_BACKGROUND)
        graphics.enableScissor(left, top + GRID_TOP, left + PANEL_WIDTH, gridBottom)

        val slotCount = config.entries.size + 1
        for (slot in 0 until slotCount) {
            val (x, y) = slotPos(slot) ?: continue
            val hovered = mouseX in x until x + CELL_WIDTH && mouseY in y until y + CARD_HEIGHT && isInGrid(mouseX.toDouble(), mouseY.toDouble())
            graphics.fill(x, y, x + CELL_WIDTH, y + CARD_HEIGHT, if (hovered) COLOR_CARD_HOVER else COLOR_CARD)

            if (slot == config.entries.size) {
                val label = tr("gui.cobblemonspawner.entries.add")
                graphics.drawString(font, label, x + (CELL_WIDTH - font.width(label)) / 2, y + (CARD_HEIGHT - font.lineHeight) / 2, COLOR_TITLE)
            } else {
                renderCard(graphics, slot, x, y, hovered)
            }
        }

        graphics.disableScissor()
        scrollbar.render(graphics, gridTrack(), scrollRow)
    }

    /**
     * Draws one entry card: 3D model on the left, name / weight / levels on the right.
     */
    private fun renderCard(graphics: GuiGraphics, slot: Int, x: Int, y: Int, hovered: Boolean) {
        val entry = config.entries[slot]
        val preview = previews[slot]

        val species = preview.species
        if (species != null) {
            // Nested scissor: big models (Wailord...) would otherwise spill over the card text.
            // GuiGraphics intersects it with the grid scissor already active.
            graphics.enableScissor(x, y, x + CARD_MODEL_WIDTH, y + CARD_HEIGHT)
            PokemonModelRenderer.render(
                graphics, species, preview.aspects,
                centerX = x + CARD_MODEL_WIDTH / 2f,
                anchorY = y + CARD_MODEL_ANCHOR_Y,
                scale = CARD_MODEL_SCALE,
                state = cardStates.getOrPut(slot) { FloatingState() }
            )
            graphics.disableScissor()
        } else {
            graphics.drawString(font, "?", x + CARD_MODEL_WIDTH / 2 - font.width("?") / 2, y + CARD_HEIGHT / 2 - font.lineHeight / 2, COLOR_ERROR)
        }

        val textX = x + CARD_MODEL_WIDTH + CARD_PADDING
        val textWidth = CELL_WIDTH - CARD_MODEL_WIDTH - CARD_PADDING - DELETE_CROSS_SIZE
        graphics.drawString(font, fitText(preview.displayName.string, textWidth), textX, y + CARD_PADDING, if (species != null) COLOR_TITLE else COLOR_ERROR)
        graphics.drawString(font, Component.translatable("gui.cobblemonspawner.entry.card.weight", entry.weight), textX, y + CARD_PADDING + CARD_LINE_STEP, COLOR_SECONDARY)

        // Status marks, right-aligned on the level line: time of day, weather, shiny.
        val marks = listOfNotNull(
            when (entry.time) {
                TimeCondition.ANY -> null
                TimeCondition.DAY -> DAY_MARK to COLOR_DAY
                TimeCondition.NIGHT -> NIGHT_MARK to COLOR_NIGHT
            },
            when (entry.weather) {
                WeatherCondition.ANY -> null
                WeatherCondition.CLEAR -> CLEAR_MARK to COLOR_CLEAR
                WeatherCondition.RAIN -> RAIN_MARK to COLOR_RAIN
                WeatherCondition.THUNDER -> THUNDER_MARK to COLOR_THUNDER
            },
            if (entry.shiny == ShinyMode.ALWAYS) SHINY_MARK to COLOR_SHINY else null
        )
        var markRight = x + CELL_WIDTH - CARD_PADDING
        val markY = y + CARD_PADDING + 2 * CARD_LINE_STEP
        for ((glyph, color) in marks.asReversed()) {
            markRight -= font.width(glyph)
            graphics.drawString(font, glyph, markRight, markY, color)
            markRight -= MARK_GAP
        }

        // Drawn after the marks so it can be truncated to the space they leave on that line.
        val levels = Component.translatable("gui.cobblemonspawner.entry.card.levels", entry.minLevel, entry.maxLevel).string
        graphics.drawString(font, fitText(levels, markRight - textX), textX, y + CARD_PADDING + 2 * CARD_LINE_STEP, COLOR_SECONDARY)
        if (hovered) {
            graphics.drawString(font, DELETE_MARK, x + CELL_WIDTH - DELETE_CROSS_SIZE, y + CARD_PADDING / 2, COLOR_ERROR)
        }
    }

    /**
     * Scrollbar track on the right of the grid, outside the cards.
     */
    private fun gridTrack(): Scrollbar.Track = Scrollbar.Track(
        x = left + PANEL_WIDTH + SCROLLBAR_GAP,
        top = top + GRID_TOP,
        height = gridHeight(),
        totalRows = totalRows(),
        visibleRows = VISIBLE_ROWS
    )

    /** Screen position of [slot], or null when it is scrolled out of view. */
    private fun slotPos(slot: Int): Pair<Int, Int>? {
        val row = slot / GRID_COLUMNS - scrollRow
        if (row !in 0 until VISIBLE_ROWS) return null
        return column(slot % GRID_COLUMNS) to top + GRID_TOP + row * (CARD_HEIGHT + CARD_GAP)
    }

    /** Slot under the mouse (the add card included), or null. */
    private fun slotAt(mouseX: Double, mouseY: Double): Int? {
        if (!isInGrid(mouseX, mouseY)) return null
        for (slot in 0..config.entries.size) {
            val (x, y) = slotPos(slot) ?: continue
            if (mouseX >= x && mouseX < x + CELL_WIDTH && mouseY >= y && mouseY < y + CARD_HEIGHT) return slot
        }
        return null
    }

    /** True when the mouse is on the delete cross of the card at [slot]. */
    private fun isOnDeleteCross(slot: Int, mouseX: Double, mouseY: Double): Boolean {
        val (x, y) = slotPos(slot) ?: return false
        return mouseX >= x + CELL_WIDTH - DELETE_CROSS_SIZE && mouseY < y + DELETE_CROSS_SIZE
    }

    /** True when the mouse is inside the visible grid area. */
    private fun isInGrid(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= left && mouseX < left + PANEL_WIDTH && mouseY >= top + GRID_TOP && mouseY < top + GRID_TOP + gridHeight()

    /** Rows needed for every entry plus the add card. */
    private fun totalRows(): Int = (config.entries.size + 1 + GRID_COLUMNS - 1) / GRID_COLUMNS

    /** Pixel height of the visible part of the grid. */
    private fun gridHeight(): Int = VISIBLE_ROWS * CARD_HEIGHT + (VISIBLE_ROWS - 1) * CARD_GAP

    /** Keeps [scrollRow] inside the scrollable range after the pool changed. */
    private fun clampScroll() {
        scrollRow = scrollRow.coerceIn(0, max(0, totalRows() - VISIBLE_ROWS))
    }

    /** Rebuilds the cached previews and drops the animation states of removed cards. */
    private fun refreshPreviews() {
        previews = config.entries.map { EntryPreview.of(it) }
        cardStates.clear()
    }

    // ---------------------------------------------------------------- actions

    /**
     * Opens the entry editor for the entry at [index], or for a new entry when null.
     */
    private fun openEditor(index: Int?) {
        captureValues()
        val initial = index?.let { config.entries[it] }
        Minecraft.getInstance().setScreen(PokemonEntryScreen(this, initial) { result ->
            if (index == null) config.entries.add(result) else config.entries[index] = result
            // Previews are rebuilt by init() when this screen is shown again.
        })
    }

    /**
     * Sends the edited config with the reset flag (removes the current Pokémon, clears the
     * once-only state), then closes. The only action that needs an explicit click.
     */
    private fun saveAndReset() {
        captureValues()
        SpawnerNetwork.sendToServer(SaveSpawnerConfigPayload(pos, config.toTag(), true))
        // Not onClose(): it would send a second, non-reset save.
        closeWithoutSaving()
    }

    /**
     * Closes the screen and drops every edit. Same as vanilla's default onClose(), which is
     * overridden here to save.
     */
    private fun closeWithoutSaving() {
        Minecraft.getInstance().setScreen(null)
    }

    /** Copies the widget values into [config]. No-op before the first init(). */
    private fun captureValues() {
        numberBoxes.forEachIndexed { index, box ->
            box.value.toIntOrNull()?.let { NUMBER_FIELDS[index].set(config, it) }
        }
        redstoneButton?.let { config.redstone = it.value }
        onceButton?.let { config.onceOnly = it.value }
        uncatchableButton?.let { config.uncatchable = it.value }
        persistentButton?.let { config.persistent = it.value }
        noAiButton?.let { config.noAi = it.value }
    }

    // ---------------------------------------------------------------- helpers

    /** Builds an ON/OFF toggle whose label and tooltip come from `gui.cobblemonspawner.<key>`. */
    private fun toggle(key: String, initial: Boolean, x: Int, y: Int): CycleButton<Boolean> =
        CycleButton.onOffBuilder(initial)
            .withTooltip { Tooltip.create(tr("gui.cobblemonspawner.$key.tooltip")) }
            .create(x, y, CELL_WIDTH, TOGGLE_HEIGHT, tr("gui.cobblemonspawner.$key"))

    /** X of the column [index] of the 3-column layout. */
    private fun column(index: Int): Int = left + index * (CELL_WIDTH + CELL_GAP)

    /** Top-left corner of the numeric field [index] (label position). */
    private fun numberFieldPos(index: Int): Pair<Int, Int> =
        column(index % GRID_COLUMNS) to top + NUMBERS_TOP + (index / GRID_COLUMNS) * NUMBER_ROW_STEP

    /** Truncates [text] with an ellipsis so it fits in [maxWidth] pixels. */
    private fun fitText(text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        var cut = text
        while (cut.isNotEmpty() && font.width("$cut$ELLIPSIS") > maxWidth) cut = cut.dropLast(1)
        return "$cut$ELLIPSIS"
    }

    /** Shorthand for a translatable component. */
    private fun tr(key: String): Component = Component.translatable(key)

    companion object {
        // --- Layout. The whole panel must fit a 240 px tall GUI (GUI scale 4 on 1080p).
        private const val PANEL_WIDTH = 330
        private const val PANEL_HEIGHT = 222
        private const val MIN_TOP_MARGIN = 4
        private const val GRID_COLUMNS = 3
        private const val CELL_WIDTH = 106
        private const val CELL_GAP = 6
        private const val TITLE_Y = 2
        private const val GRID_TOP = 14

        /** Card rows visible at once. One more row pushes the settings below 240 px. */
        private const val VISIBLE_ROWS = 2
        private const val CARD_HEIGHT = 44
        private const val CARD_GAP = 2
        private const val CARD_PADDING = 4
        private const val CARD_LINE_STEP = 12

        /** Width reserved for the model on the left of a card. */
        private const val CARD_MODEL_WIDTH = 36

        /**
         * Model scale and anchor inside a card. Visual tuning only: raise the scale for bigger
         * models, move the anchor if models are cut at the top or bottom.
         */
        private const val CARD_MODEL_SCALE = 14f
        private const val CARD_MODEL_ANCHOR_Y = 4f

        private const val DELETE_CROSS_SIZE = 10
        private const val NUMBERS_TOP = 110
        private const val NUMBER_ROW_STEP = 20
        private const val NUMBER_BOX_OFFSET = 72
        private const val NUMBER_BOX_WIDTH = 34
        private const val NUMBER_MAX_LENGTH = 5
        private const val FIELD_HEIGHT = 16
        private const val LABEL_Y_OFFSET = 4
        private const val TOGGLES_TOP = 152
        private const val TOGGLE_ROW_STEP = 22
        private const val TOGGLE_HEIGHT = 18
        private const val ACTIONS_TOP = 200
        private const val BUTTON_HEIGHT = 20
        private const val SCROLLBAR_GAP = 2

        private const val LEFT_BUTTON = 0

        // --- Colors (ARGB).
        private const val COLOR_TITLE = 0xFFFFFFFF.toInt()
        private const val COLOR_SECONDARY = 0xFFA0A0A0.toInt()
        private const val COLOR_LABEL = 0xFFE0E0E0.toInt()
        private const val COLOR_ERROR = 0xFFFF5555.toInt()
        private const val COLOR_SHINY = 0xFFFFD700.toInt()
        private const val COLOR_DAY = 0xFFFFAA00.toInt()
        private const val COLOR_NIGHT = 0xFF8888FF.toInt()
        private const val COLOR_CLEAR = 0xFFFFE680.toInt()
        private const val COLOR_RAIN = 0xFF55AAFF.toInt()
        private const val COLOR_THUNDER = 0xFFFFFF55.toInt()
        private const val COLOR_GRID_BACKGROUND = 0x80000000.toInt()
        private const val COLOR_CARD = 0x40FFFFFF
        private const val COLOR_CARD_HOVER = 0x70FFFFFF

        /**
         * Card status glyphs, rendered through Minecraft's Unifont fallback. The star is the
         * night mark, so shiny uses a four-pointed sparkle to stay distinct from it.
         */
        private const val SHINY_MARK = "✦"
        private const val DAY_MARK = "☀"
        private const val NIGHT_MARK = "★"
        private const val CLEAR_MARK = "☼"
        private const val RAIN_MARK = "☂"
        private const val THUNDER_MARK = "⚡"
        private const val MARK_GAP = 2
        private const val DELETE_MARK = "✕"
        private const val ELLIPSIS = "…"

        private val NUMBER_FIELDS = listOf(
            NumberField("radius", { it.radius }, { c, v -> c.radius = v }),
            NumberField("max_active", { it.maxActive }, { c, v -> c.maxActive = v }),
            NumberField("per_cycle", { it.spawnsPerCycle }, { c, v -> c.spawnsPerCycle = v }),
            NumberField("activation_range", { it.activationRange }, { c, v -> c.activationRange = v }),
            NumberField("min_delay", { it.minDelay }, { c, v -> c.minDelay = v }),
            NumberField("max_delay", { it.maxDelay }, { c, v -> c.maxDelay = v }),
        )
    }
}
