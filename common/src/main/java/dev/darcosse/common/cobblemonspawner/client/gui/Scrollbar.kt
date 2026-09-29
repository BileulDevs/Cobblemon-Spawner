package dev.darcosse.common.cobblemonspawner.client.gui

import net.minecraft.client.gui.GuiGraphics
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draggable vertical scrollbar for the custom row-based lists of the mod's screens.
 *
 * Both lists are drawn by hand (no vanilla selection list), so this class owns the thumb
 * geometry, the rendering and the mouse drag. The screen keeps the scroll position and
 * describes the track on every call; nothing here is cached, so a list that changes size
 * between two frames stays consistent.
 *
 * @author Darcosse
 * @version 1.0
 * @since 2026
 */
class Scrollbar {

    /**
     * Position and content of the track for the current frame.
     *
     * @property x left edge of the visible bar
     * @property top top of the track
     * @property height height of the track
     * @property totalRows rows in the whole list
     * @property visibleRows rows visible at once
     */
    data class Track(val x: Int, val top: Int, val height: Int, val totalRows: Int, val visibleRows: Int) {

        /** Highest valid scroll position. */
        val maxScroll: Int get() = max(0, totalRows - visibleRows)

        /** True when the list overflows; otherwise the bar is neither drawn nor clickable. */
        val isScrollable: Boolean get() = maxScroll > 0

        /** Thumb height, proportional to the visible share of the list. */
        val thumbHeight: Int get() = max(MIN_THUMB_HEIGHT, height * visibleRows / max(1, totalRows))

        /** Top of the thumb for [scrollRow]. */
        fun thumbTop(scrollRow: Int): Int =
            if (maxScroll == 0) top else top + (height - thumbHeight) * scrollRow / maxScroll

        /**
         * True when the mouse is on the bar. The hit zone is wider than the drawn bar
         * ([HIT_MARGIN] on each side): a 4 px target is too hard to grab.
         */
        fun contains(mouseX: Double, mouseY: Double): Boolean =
            mouseX >= x - HIT_MARGIN && mouseX < x + WIDTH + HIT_MARGIN && mouseY >= top && mouseY < top + height

        /** Scroll position whose thumb top is closest to [thumbTopY]. */
        fun rowForThumbTop(thumbTopY: Double): Int {
            val travel = height - thumbHeight
            if (travel <= 0) return 0
            return ((thumbTopY - top) * maxScroll / travel).roundToInt().coerceIn(0, maxScroll)
        }
    }

    /**
     * Distance between the mouse and the top of the thumb while dragging, or null when idle.
     * Keeping it makes the thumb follow the mouse from where it was grabbed, without jumping.
     */
    private var grabOffset: Double? = null

    /** True between a press on the bar and the matching release. */
    val isDragging: Boolean get() = grabOffset != null

    /**
     * Draws the thumb. Does nothing when the list does not overflow.
     */
    fun render(graphics: GuiGraphics, track: Track, scrollRow: Int) {
        if (!track.isScrollable) return
        val thumbTop = track.thumbTop(scrollRow)
        val color = if (isDragging) COLOR_THUMB_DRAGGING else COLOR_THUMB
        graphics.fill(track.x, thumbTop, track.x + WIDTH, thumbTop + track.thumbHeight, color)
    }

    /**
     * Starts a drag when the press is on the bar.
     *
     * Pressing the thumb grabs it where it was clicked; pressing the track elsewhere jumps
     * so that the thumb is centered on the mouse, then drags from there.
     *
     * @return the new scroll position, or null when the press is not on the bar
     */
    fun press(mouseX: Double, mouseY: Double, track: Track, scrollRow: Int): Int? {
        if (!track.isScrollable || !track.contains(mouseX, mouseY)) return null
        val thumbTop = track.thumbTop(scrollRow)
        val onThumb = mouseY >= thumbTop && mouseY < thumbTop + track.thumbHeight
        val offset = if (onThumb) mouseY - thumbTop else track.thumbHeight / 2.0
        grabOffset = offset
        return track.rowForThumbTop(mouseY - offset)
    }

    /**
     * Follows the mouse during a drag.
     *
     * @return the new scroll position, or null when no drag is in progress
     */
    fun drag(mouseY: Double, track: Track): Int? {
        val offset = grabOffset ?: return null
        return track.rowForThumbTop(mouseY - offset)
    }

    /**
     * Ends the drag.
     *
     * @return true when a drag was in progress, so the screen can consume the release
     */
    fun release(): Boolean {
        val wasDragging = isDragging
        grabOffset = null
        return wasDragging
    }

    companion object {
        /** Drawn width of the bar. Widened from 2 px so it reads as something grabbable. */
        const val WIDTH = 4

        /** Extra clickable pixels on each side of the bar. */
        private const val HIT_MARGIN = 2

        /** Smallest thumb, so a very long list still leaves something to grab. */
        private const val MIN_THUMB_HEIGHT = 8

        private const val COLOR_THUMB = 0xFFC0C0C0.toInt()
        private const val COLOR_THUMB_DRAGGING = 0xFFFFFFFF.toInt()
    }
}
