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
 * Bottom: spawner-wide settings and the save buttons.
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

    /** First visible card row of the pool grid. */
    private var scrollRow = 0

    /** Resolved species/aspects per entry, rebuilt only when the pool changes. */
    private var previews: List<EntryPreview> = emptyList()

    /** One animation state per card, so animations of different species don't interfere. */
    private val cardStates = mutableMapOf<Int, FloatingState>()

    private val numberBoxes = mutableListOf<EditBox>()
    private var timeButton: CycleButton<TimeCondition>? = null
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

        timeButton = addRenderableWidget(
            CycleButton.builder<TimeCondition> { Component.translatable(it.translationKey) }
                .withValues(TimeCondition.entries)
                .withInitialValue(config.time)
                .create(column(0), row1, CELL_WIDTH, TOGGLE_HEIGHT, tr("gui.cobblemonspawner.time"))
        )
        redstoneButton = addRenderableWidget(
            CycleButton.builder<RedstoneMode> { Component.translatable(it.translationKey) }
                .withValues(RedstoneMode.entries)
                .withInitialValue(config.redstone)
                .create(column(1), row1, CELL_WIDTH, TOGGLE_HEIGHT, tr("gui.cobblemonspawner.redstone"))
        )
        onceButton = addRenderableWidget(toggle("once_only", config.onceOnly, column(2), row1))
        uncatchableButton = addRenderableWidget(toggle("uncatchable", config.uncatchable, column(0), row2))
        persistentButton = addRenderableWidget(toggle("persistent", config.persistent, column(1), row2))
        noAiButton = addRenderableWidget(toggle("no_ai", config.noAi, column(2), row2))

        val actionsY = top + ACTIONS_TOP
        addRenderableWidget(
            Button.builder(tr("gui.cobblemonspawner.save")) { save(reset = false) }
                .bounds(column(0), actionsY, CELL_WIDTH, BUTTON_HEIGHT).build()
        )
        addRenderableWidget(
            Button.builder(tr("gui.cobblemonspawner.save_reset")) { save(reset = true) }
                .bounds(column(1), actionsY, CELL_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(tr("gui.cobblemonspawner.save_reset.tooltip")))
                .build()
        )
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL) { onClose() }
                .bounds(column(2), actionsY, CELL_WIDTH, BUTTON_HEIGHT).build()
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
        renderScrollBar(graphics)
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
        graphics.drawString(font, Component.translatable("gui.cobblemonspawner.entry.card.levels", entry.minLevel, entry.maxLevel), textX, y + CARD_PADDING + 2 * CARD_LINE_STEP, COLOR_SECONDARY)

        if (entry.shiny == ShinyMode.ALWAYS) {
            graphics.drawString(font, SHINY_MARK, x + CELL_WIDTH - font.width(SHINY_MARK) - CARD_PADDING, y + CARD_HEIGHT - font.lineHeight - CARD_PADDING, COLOR_SHINY)
        }
        if (hovered) {
            graphics.drawString(font, DELETE_MARK, x + CELL_WIDTH - DELETE_CROSS_SIZE, y + CARD_PADDING / 2, COLOR_ERROR)
        }
    }

    /**
     * Thin scroll indicator on the right edge of the grid, only when the pool overflows.
     */
    private fun renderScrollBar(graphics: GuiGraphics) {
        val totalRows = totalRows()
        if (totalRows <= VISIBLE_ROWS) return
        val gridHeight = gridHeight()
        val barHeight = max(SCROLLBAR_MIN_HEIGHT, gridHeight * VISIBLE_ROWS / totalRows)
        val barY = top + GRID_TOP + (gridHeight - barHeight) * scrollRow / (totalRows - VISIBLE_ROWS)
        val barX = left + PANEL_WIDTH + SCROLLBAR_GAP
        graphics.fill(barX, barY, barX + SCROLLBAR_WIDTH, barY + barHeight, COLOR_SCROLLBAR)
    }

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
     * Sends the edited config to the server, then closes the screen.
     */
    private fun save(reset: Boolean) {
        captureValues()
        SpawnerNetwork.sendToServer(SaveSpawnerConfigPayload(pos, config.toTag(), reset))
        onClose()
    }

    /** Copies the widget values into [config]. No-op before the first init(). */
    private fun captureValues() {
        numberBoxes.forEachIndexed { index, box ->
            box.value.toIntOrNull()?.let { NUMBER_FIELDS[index].set(config, it) }
        }
        timeButton?.let { config.time = it.value }
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
        private const val SCROLLBAR_WIDTH = 2
        private const val SCROLLBAR_GAP = 2
        private const val SCROLLBAR_MIN_HEIGHT = 8

        private const val LEFT_BUTTON = 0

        // --- Colors (ARGB).
        private const val COLOR_TITLE = 0xFFFFFFFF.toInt()
        private const val COLOR_SECONDARY = 0xFFA0A0A0.toInt()
        private const val COLOR_LABEL = 0xFFE0E0E0.toInt()
        private const val COLOR_ERROR = 0xFFFF5555.toInt()
        private const val COLOR_SHINY = 0xFFFFD700.toInt()
        private const val COLOR_GRID_BACKGROUND = 0x80000000.toInt()
        private const val COLOR_CARD = 0x40FFFFFF
        private const val COLOR_CARD_HOVER = 0x70FFFFFF
        private const val COLOR_SCROLLBAR = 0xFFC0C0C0.toInt()

        private const val SHINY_MARK = "★"
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
