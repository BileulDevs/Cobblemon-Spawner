package dev.darcosse.common.cobblemonspawner.client.gui

import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.Species
import dev.darcosse.common.cobblemonspawner.client.render.EntryPreview
import dev.darcosse.common.cobblemonspawner.client.render.PokemonModelRenderer
import dev.darcosse.common.cobblemonspawner.client.render.SpeciesCatalog
import dev.darcosse.common.cobblemonspawner.spawner.ShinyMode
import dev.darcosse.common.cobblemonspawner.spawner.SpawnEntry
import dev.darcosse.common.cobblemonspawner.spawner.SpawnPlacement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import kotlin.math.max

/**
 * Editor for a single pool entry: searchable species list on the left, spinning 3D preview
 * and entry settings (weight, level range, shiny, spawn placement, extra properties) on the right.
 *
 * Returns to [parent] on confirm or cancel; the result is handed back through [onConfirm].
 * Client only.
 *
 * @author Darcosse
 * @version 1.0
 * @since 2026
 */
class PokemonEntryScreen(
    private val parent: Screen,
    initial: SpawnEntry?,
    private val onConfirm: (SpawnEntry) -> Unit
) : Screen(Component.translatable("screen.cobblemonspawner.entry.title")) {

    /**
     * A species of the picker, with its display name and lowercase search key precomputed
     * so filtering doesn't rebuild strings on every keystroke.
     */
    private class SpeciesOption(val species: Species, val id: String, val name: Component) {
        val searchKey: String = "${name.string} $id".lowercase()
    }

    /** Built once per screen: sorting the whole registry is too costly to redo per frame. */
    private val allOptions: List<SpeciesOption> by lazy {
        SpeciesCatalog.sortedByName().map { SpeciesOption(it, SpeciesCatalog.id(it), SpeciesCatalog.displayName(it)) }
    }

    // --- Edited values, kept outside the widgets so they survive init() on resize.
    private var selected: SpeciesOption? = null
    private var weightText = (initial?.weight ?: SpawnEntry.DEFAULT_WEIGHT).toString()
    private var minLevelText = (initial?.minLevel ?: SpawnEntry.DEFAULT_MIN_LEVEL).toString()
    private var maxLevelText = (initial?.maxLevel ?: SpawnEntry.DEFAULT_MAX_LEVEL).toString()
    private var shiny = initial?.shiny ?: ShinyMode.DEFAULT
    private var placement = initial?.placement ?: SpawnPlacement.AUTO
    private var extraText = initial?.extraProperties ?: ""
    private var searchText = ""

    private var filtered: List<SpeciesOption> = emptyList()
    private var lastAppliedSearch: String? = null
    private var scrollRow = 0

    private var previewAspects: Set<String> = emptySet()
    private var lastAspectKey: String? = null
    private val previewState = FloatingState()

    private var left = 0
    private var top = 0

    private var searchBox: EditBox? = null
    private var weightBox: EditBox? = null
    private var minLevelBox: EditBox? = null
    private var maxLevelBox: EditBox? = null
    private var shinyButton: CycleButton<ShinyMode>? = null
    private var placementButton: CycleButton<SpawnPlacement>? = null
    private var extraBox: EditBox? = null
    private var confirmButton: Button? = null

    init {
        selected = initial?.let { entry -> allOptions.firstOrNull { it.id == entry.species.lowercase() } }
    }

    /**
     * Builds the widgets from the edited values (also on resize).
     */
    override fun init() {
        captureValues()

        left = (width - PANEL_WIDTH) / 2
        top = max(MIN_TOP_MARGIN, (height - PANEL_HEIGHT) / 2)
        val rightX = left + RIGHT_COLUMN_OFFSET

        searchBox = addRenderableWidget(
            EditBox(font, left, top + SEARCH_TOP, LIST_WIDTH, FIELD_HEIGHT, tr("gui.cobblemonspawner.entry.search")).apply {
                setMaxLength(SEARCH_MAX_LENGTH)
                value = searchText
                setTooltip(Tooltip.create(tr("gui.cobblemonspawner.entry.search")))
            }
        )

        weightBox = addRenderableWidget(numberBox(rightX, weightText, "weight"))
        minLevelBox = addRenderableWidget(numberBox(rightX + SMALL_FIELD_WIDTH + FIELD_GAP, minLevelText, "min_level"))
        maxLevelBox = addRenderableWidget(numberBox(rightX + 2 * (SMALL_FIELD_WIDTH + FIELD_GAP), maxLevelText, "max_level"))

        shinyButton = addRenderableWidget(
            CycleButton.builder<ShinyMode> { Component.translatable(it.translationKey) }
                .withValues(ShinyMode.entries)
                .withInitialValue(shiny)
                .create(rightX, top + SHINY_TOP, RIGHT_COLUMN_WIDTH, TOGGLE_HEIGHT, tr("gui.cobblemonspawner.entry.shiny"))
        )

        placementButton = addRenderableWidget(
            CycleButton.builder<SpawnPlacement> { Component.translatable(it.translationKey) }
                .withValues(SpawnPlacement.entries)
                .withInitialValue(placement)
                // One tooltip per value: the placement rules are not obvious from the name alone.
                .withTooltip { Tooltip.create(Component.translatable("${it.translationKey}.tooltip")) }
                .create(rightX, top + PLACEMENT_TOP, RIGHT_COLUMN_WIDTH, TOGGLE_HEIGHT, tr("gui.cobblemonspawner.entry.placement"))
        )

        extraBox = addRenderableWidget(
            EditBox(font, rightX, top + EXTRA_TOP, RIGHT_COLUMN_WIDTH, FIELD_HEIGHT, tr("gui.cobblemonspawner.entry.extra")).apply {
                setMaxLength(EXTRA_MAX_LENGTH)
                value = extraText
                setTooltip(Tooltip.create(tr("gui.cobblemonspawner.entry.extra.tooltip")))
            }
        )

        confirmButton = addRenderableWidget(
            Button.builder(tr("gui.cobblemonspawner.entry.confirm")) { confirm() }
                .bounds(rightX, top + ACTIONS_TOP, ACTION_BUTTON_WIDTH, BUTTON_HEIGHT).build()
        )
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL) { onClose() }
                .bounds(rightX + ACTION_BUTTON_WIDTH + FIELD_GAP, top + ACTIONS_TOP, ACTION_BUTTON_WIDTH, BUTTON_HEIGHT).build()
        )

        applySearchIfChanged()
        scrollToSelected()
    }

    /**
     * Draws the widgets, then the species list, the preview and the labels.
     */
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Cheap string comparisons each frame instead of widget responders: no extra API needed.
        captureValues()
        applySearchIfChanged()
        refreshAspectsIfChanged()
        confirmButton?.active = selected != null

        super.render(graphics, mouseX, mouseY, partialTick)

        graphics.drawString(font, title, left, top + TITLE_Y, COLOR_TITLE)
        renderSpeciesList(graphics, mouseX, mouseY)
        renderPreview(graphics)

        val rightX = left + RIGHT_COLUMN_OFFSET
        val labelY = top + NUMBERS_LABEL_TOP
        graphics.drawString(font, tr("gui.cobblemonspawner.entry.weight"), rightX, labelY, COLOR_LABEL)
        graphics.drawString(font, tr("gui.cobblemonspawner.entry.min_level"), rightX + SMALL_FIELD_WIDTH + FIELD_GAP, labelY, COLOR_LABEL)
        graphics.drawString(font, tr("gui.cobblemonspawner.entry.max_level"), rightX + 2 * (SMALL_FIELD_WIDTH + FIELD_GAP), labelY, COLOR_LABEL)
        graphics.drawString(font, tr("gui.cobblemonspawner.entry.extra"), rightX, top + EXTRA_LABEL_TOP, COLOR_LABEL)
    }

    /**
     * Selects the species under the mouse in the list; other clicks go to the widgets.
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == LEFT_BUTTON && isInList(mouseX, mouseY)) {
            val index = scrollRow + ((mouseY - (top + LIST_TOP)) / ROW_HEIGHT).toInt()
            filtered.getOrNull(index)?.let { selected = it }
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /**
     * Scrolls the species list by [SCROLL_STEP] rows per wheel notch.
     */
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (isInList(mouseX, mouseY)) {
            scrollRow -= scrollY.toInt().coerceIn(-1, 1) * SCROLL_STEP
            clampScroll()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    /** Escape and Cancel go back to the spawner screen instead of closing everything. */
    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }

    /** The world keeps running behind the screen, like vanilla block editors. */
    override fun isPauseScreen(): Boolean = false

    // ---------------------------------------------------------------- species list

    /**
     * Draws the visible part of the filtered species list with its scroll indicator.
     */
    private fun renderSpeciesList(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val listTop = top + LIST_TOP
        val listBottom = listTop + LIST_HEIGHT
        graphics.fill(left, listTop, left + LIST_WIDTH, listBottom, COLOR_PANEL_BACKGROUND)

        if (filtered.isEmpty()) {
            graphics.drawString(font, tr("gui.cobblemonspawner.entry.no_match"), left + ROW_PADDING, listTop + ROW_PADDING, COLOR_SECONDARY)
            return
        }

        graphics.enableScissor(left, listTop, left + LIST_WIDTH, listBottom)
        for (row in 0 until visibleRows()) {
            val option = filtered.getOrNull(scrollRow + row) ?: break
            val y = listTop + row * ROW_HEIGHT
            val hovered = mouseX in left until left + LIST_WIDTH && mouseY in y until y + ROW_HEIGHT
            when {
                option === selected -> graphics.fill(left, y, left + LIST_WIDTH, y + ROW_HEIGHT, COLOR_ROW_SELECTED)
                hovered -> graphics.fill(left, y, left + LIST_WIDTH, y + ROW_HEIGHT, COLOR_ROW_HOVER)
            }
            graphics.drawString(font, fitText(option.name.string, LIST_WIDTH - 2 * ROW_PADDING), left + ROW_PADDING, y + ROW_TEXT_OFFSET, COLOR_TITLE)
        }
        graphics.disableScissor()

        val total = filtered.size
        val visible = visibleRows()
        if (total > visible) {
            val barHeight = max(SCROLLBAR_MIN_HEIGHT, LIST_HEIGHT * visible / total)
            val barY = listTop + (LIST_HEIGHT - barHeight) * scrollRow / (total - visible)
            val barX = left + LIST_WIDTH - SCROLLBAR_WIDTH
            graphics.fill(barX, barY, barX + SCROLLBAR_WIDTH, barY + barHeight, COLOR_SCROLLBAR)
        }
    }

    /**
     * Draws the preview box: slowly spinning model of the selected species with the current
     * aspects, and its name underneath.
     */
    private fun renderPreview(graphics: GuiGraphics) {
        val boxX = left + RIGHT_COLUMN_OFFSET
        val boxY = top + PREVIEW_TOP
        graphics.fill(boxX, boxY, boxX + RIGHT_COLUMN_WIDTH, boxY + PREVIEW_HEIGHT, COLOR_PANEL_BACKGROUND)

        val option = selected
        if (option == null) {
            val hint = tr("gui.cobblemonspawner.entry.pick_species")
            graphics.drawString(font, hint, boxX + (RIGHT_COLUMN_WIDTH - font.width(hint)) / 2, boxY + PREVIEW_HEIGHT / 2, COLOR_SECONDARY)
            return
        }

        val yaw = PokemonModelRenderer.DEFAULT_YAW_DEGREES +
            (System.currentTimeMillis() % PREVIEW_ROTATION_PERIOD_MS) * FULL_TURN_DEGREES / PREVIEW_ROTATION_PERIOD_MS

        graphics.enableScissor(boxX, boxY, boxX + RIGHT_COLUMN_WIDTH, boxY + PREVIEW_HEIGHT)
        PokemonModelRenderer.render(
            graphics, option.species, previewAspects,
            centerX = boxX + RIGHT_COLUMN_WIDTH / 2f,
            anchorY = boxY + PREVIEW_MODEL_ANCHOR_Y,
            scale = PREVIEW_MODEL_SCALE,
            state = previewState,
            yawDegrees = yaw
        )
        graphics.disableScissor()

        val name = option.name
        graphics.drawString(font, name, boxX + (RIGHT_COLUMN_WIDTH - font.width(name)) / 2, top + NAME_TOP, if (shiny == ShinyMode.ALWAYS) COLOR_SHINY else COLOR_TITLE)
    }

    /** Re-filters the list when the search text changed since the last call. */
    private fun applySearchIfChanged() {
        if (searchText == lastAppliedSearch) return
        lastAppliedSearch = searchText
        val query = searchText.trim().lowercase()
        filtered = if (query.isEmpty()) allOptions else allOptions.filter { query in it.searchKey }
        scrollRow = 0
    }

    /** Re-parses the preview aspects only when shiny mode or extra properties changed. */
    private fun refreshAspectsIfChanged() {
        val key = "$shiny|$extraText"
        if (key == lastAspectKey) return
        lastAspectKey = key
        previewAspects = EntryPreview.aspectsFor(extraText, shiny)
    }

    /** Scrolls so the initially selected species is visible, a few rows from the top. */
    private fun scrollToSelected() {
        val index = filtered.indexOf(selected ?: return)
        if (index >= 0) {
            scrollRow = index - SELECTED_CONTEXT_ROWS
            clampScroll()
        }
    }

    /** Keeps [scrollRow] inside the scrollable range. */
    private fun clampScroll() {
        scrollRow = scrollRow.coerceIn(0, max(0, filtered.size - visibleRows()))
    }

    /** Rows of the species list visible at once. */
    private fun visibleRows(): Int = LIST_HEIGHT / ROW_HEIGHT

    /** True when the mouse is over the species list. */
    private fun isInList(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= left && mouseX < left + LIST_WIDTH && mouseY >= top + LIST_TOP && mouseY < top + LIST_TOP + LIST_HEIGHT

    // ---------------------------------------------------------------- actions

    /**
     * Builds the entry from the edited values and hands it back to the spawner screen.
     * Values are only loosely checked here; the server sanitizes and validates them again.
     */
    private fun confirm() {
        val option = selected ?: return
        captureValues()
        onConfirm(
            SpawnEntry(
                species = option.id,
                weight = weightText.toIntOrNull()?.coerceAtLeast(1) ?: SpawnEntry.DEFAULT_WEIGHT,
                minLevel = minLevelText.toIntOrNull()?.coerceAtLeast(1) ?: SpawnEntry.DEFAULT_MIN_LEVEL,
                maxLevel = maxLevelText.toIntOrNull()?.coerceAtLeast(1) ?: SpawnEntry.DEFAULT_MAX_LEVEL,
                shiny = shiny,
                extraProperties = extraText.trim(),
                placement = placement
            )
        )
        onClose()
    }

    /** Copies the widget values into the edited fields. No-op before the first init(). */
    private fun captureValues() {
        searchBox?.let { searchText = it.value }
        weightBox?.let { weightText = it.value }
        minLevelBox?.let { minLevelText = it.value }
        maxLevelBox?.let { maxLevelText = it.value }
        shinyButton?.let { shiny = it.value }
        placementButton?.let { placement = it.value }
        extraBox?.let { extraText = it.value }
    }

    // ---------------------------------------------------------------- helpers

    /** Digits-only field of the weight / level row. */
    private fun numberBox(x: Int, initial: String, key: String): EditBox =
        EditBox(font, x, top + NUMBERS_TOP, SMALL_FIELD_WIDTH, FIELD_HEIGHT, tr("gui.cobblemonspawner.entry.$key")).apply {
            setMaxLength(NUMBER_MAX_LENGTH)
            setFilter { text -> text.all { it.isDigit() } }
            value = initial
            setTooltip(Tooltip.create(tr("gui.cobblemonspawner.entry.$key.tooltip")))
        }

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
        // --- Layout. Same 330 x 222 panel as the spawner screen, fits a 240 px tall GUI.
        private const val PANEL_WIDTH = 330
        private const val PANEL_HEIGHT = 222
        private const val MIN_TOP_MARGIN = 4
        private const val TITLE_Y = 2

        private const val LIST_WIDTH = 130
        private const val SEARCH_TOP = 14
        private const val LIST_TOP = 34
        private const val LIST_HEIGHT = 184
        private const val ROW_HEIGHT = 11
        private const val ROW_PADDING = 3
        private const val ROW_TEXT_OFFSET = 2
        private const val SCROLL_STEP = 3
        private const val SEARCH_MAX_LENGTH = 32

        /** Rows kept above the selected species when the editor opens, for context. */
        private const val SELECTED_CONTEXT_ROWS = 3

        private const val RIGHT_COLUMN_OFFSET = 140
        private const val RIGHT_COLUMN_WIDTH = 190
        private const val PREVIEW_TOP = 14
        /** Shrunk from 84 to make room for the placement row; the panel must stay 222 px tall. */
        private const val PREVIEW_HEIGHT = 64

        /**
         * Model scale and anchor in the preview box. Visual tuning only: raise the scale for
         * bigger models, move the anchor if models are cut at the top or bottom.
         */
        private const val PREVIEW_MODEL_SCALE = 24f
        private const val PREVIEW_MODEL_ANCHOR_Y = 8f

        /** One full turn every 12 s. Lower = faster spin. */
        private const val PREVIEW_ROTATION_PERIOD_MS = 12_000L
        private const val FULL_TURN_DEGREES = 360f

        private const val NAME_TOP = 81
        private const val NUMBERS_LABEL_TOP = 93
        private const val NUMBERS_TOP = 102
        private const val SMALL_FIELD_WIDTH = 58
        private const val FIELD_GAP = 8
        private const val NUMBER_MAX_LENGTH = 5
        private const val SHINY_TOP = 122
        private const val PLACEMENT_TOP = 144
        private const val EXTRA_LABEL_TOP = 166
        private const val EXTRA_TOP = 176
        private const val EXTRA_MAX_LENGTH = 256
        private const val ACTIONS_TOP = 198
        private const val ACTION_BUTTON_WIDTH = 91
        private const val FIELD_HEIGHT = 16
        private const val TOGGLE_HEIGHT = 18
        private const val BUTTON_HEIGHT = 20
        private const val SCROLLBAR_WIDTH = 2
        private const val SCROLLBAR_MIN_HEIGHT = 8

        private const val LEFT_BUTTON = 0

        // --- Colors (ARGB).
        private const val COLOR_TITLE = 0xFFFFFFFF.toInt()
        private const val COLOR_SECONDARY = 0xFFA0A0A0.toInt()
        private const val COLOR_LABEL = 0xFFE0E0E0.toInt()
        private const val COLOR_SHINY = 0xFFFFD700.toInt()
        private const val COLOR_PANEL_BACKGROUND = 0x80000000.toInt()
        private const val COLOR_ROW_SELECTED = 0x80FFFFFF.toInt()
        private const val COLOR_ROW_HOVER = 0x30FFFFFF
        private const val COLOR_SCROLLBAR = 0xFFC0C0C0.toInt()

        private const val ELLIPSIS = "…"
    }
}
