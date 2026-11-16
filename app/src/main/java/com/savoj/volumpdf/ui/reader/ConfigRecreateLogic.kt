package com.savoj.volumpdf.ui.reader

import android.content.pm.ActivityInfo

/**
 * Д1: ReaderActivity сама обробляє поворот/розмір (configChanges), а зміну масштабу шрифту, теми й мови
 * застосовує через recreate() лише тоді, коли немає відкритого вікна чи картки зі станом (введений текст).
 */
object ConfigRecreateLogic {
    const val POLL_MS = 300L

    private const val RECREATE_MASK = ActivityInfo.CONFIG_FONT_SCALE or
        ActivityInfo.CONFIG_UI_MODE or
        ActivityInfo.CONFIG_LOCALE or
        ActivityInfo.CONFIG_LAYOUT_DIRECTION or
        ActivityInfo.CONFIG_FONT_WEIGHT_ADJUSTMENT

    /** [diff] — результат Configuration.diff(): чи потрібно перечитати ресурси. */
    fun needsRecreate(diff: Int): Boolean = diff and RECREATE_MASK != 0

    /** Перестворювати зараз можна лише коли recreate відкладено й жодне вікно/картка не відкриті. */
    fun shouldRecreateNow(pending: Boolean, busy: Boolean): Boolean = pending && !busy
}
