package com.savoj.volumpdf.ui.reader

/**
 * М1/М2/М3/М5: що видно в читачі. Чиста логіка без Android, щоб її можна було перевірити JVM-тестами.
 *
 * Поза режимом «Редагувати» лишається пігулка сторінки (з лупою пошуку) і одна плаваюча кнопка «Редагувати».
 * Пошук, тема читання, «Сторінки й файл», форми — у меню ⋮ та шторці; нічого не зникає зовсім.
 */
object ReaderChromeLogic {

    /**
     * Плаваюча кнопка «Редагувати» показується лише в чистому читанні: не в режимі «Редагувати» (там темна
     * панель), не коли документ не відкрився і не під час текстового пошуку (пігулка теж ховається).
     */
    fun showEditFab(placeMode: Boolean, openFailed: Boolean, searchActive: Boolean): Boolean =
        !placeMode && !openFailed && !searchActive

    /** Стан пошуку змінився відносно того, з яким останній раз рахували видимість: треба перерахувати. */
    fun needsChromeRefresh(lastApplied: Boolean, current: Boolean): Boolean = lastApplied != current

    /**
     * R1 (D-1): після «Зберегти зміни» запускати вибір файлу для копії лише коли збереження було на місці.
     * Якщо зміни збережено в нову копію (NEW_COPY / «лише копіювати»), поточним документом уже стала ця копія
     * (змінився uri), і другий вибір файлу був би зайвим.
     */
    fun shouldLaunchCopyAfterSave(uriBeforeSave: String?, uriAfterSave: String?): Boolean =
        uriBeforeSave != null && uriBeforeSave == uriAfterSave

    /** Видимість пунктів верхньої панелі й меню ⋮. */
    data class MenuState(
        /** «Зберегти» у режимі «Редагувати» видно завжди (R1). */
        val placeSave: Boolean,
        /** «Зберегти» активне лише за незбережених змін; без змін — сіре. */
        val placeSaveEnabled: Boolean,
        val placeDone: Boolean,
        /** «+» у верхній панелі лише в режимі «Редагувати»; у читанні ці дії в «Сторінки й файл». */
        val add: Boolean,
        /** «Тема читання»: одне місце, меню ⋮, завжди. */
        val readingTheme: Boolean,
        /** Пошук у меню ⋮ лише коли пігулки немає (режим «Редагувати»); у читанні це лупа в пігулці. */
        val searchInOverflow: Boolean,
        val forms: Boolean,
        /** «Зберегти копію» (R1): для будь-якого відкритого документа, також у режимі «Редагувати». */
        val saveCopy: Boolean,
        /** «Друкувати» (R1): для відкритого документа, також у режимі «Редагувати». */
        val print: Boolean,
        /** Решта пунктів: обране, поділитися, «Сторінки й файл», організатор, відомості. */
        val documentActions: Boolean,
    )

    fun menuState(
        placeMode: Boolean,
        hasUnsaved: Boolean,
        formFieldCount: Int,
        readOnlyDocument: Boolean,
        openFailed: Boolean,
    ): MenuState {
        if (openFailed) {
            return MenuState(false, false, false, false, false, false, false, false, false, false)
        }
        return MenuState(
            placeSave = placeMode,
            placeSaveEnabled = placeMode && hasUnsaved,
            placeDone = placeMode,
            add = placeMode,
            readingTheme = true,
            searchInOverflow = placeMode,
            forms = formFieldCount > 0,
            saveCopy = true,
            print = true,
            documentActions = true,
        )
    }
}
