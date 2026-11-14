package com.savoj.volumpdf.ui.reader

import com.savoj.volumpdf.core.pdf.model.DocumentEditability

/**
 * Стан доступу до редагування документа (етап 3e). Контракт `checkEditability` не змінено:
 * `null` означає «не вдалося відкрити для редагування».
 */
sealed interface EditAccess {
    /** Незашифрований документ: редагування й збереження в той самий файл, як у 3d. */
    data class Open(val canMarkup: Boolean, val canFillForms: Boolean) : EditAccess

    /** Зашифрований документ, що відкрився (порожній або введений пароль): права керують окремо, збереження лише новою копією. */
    data class Restricted(val canMarkup: Boolean, val canFillForms: Boolean) : EditAccess

    /** Переглядач документ відкрив, пароль не вводили, а результат null: файл вимагає пароль. */
    object PasswordRequired : EditAccess

    /** Пароль уже вводили, а результат знову null: «Невірний пароль або файл не підтримується для редагування». */
    object WrongPassword : EditAccess

    /** null, переглядач документ не відкрив і пароль не вводили: не вдалося відкрити (пошкоджений або ще завантажується). */
    object Unreadable : EditAccess
}

/** Чиста логіка стану доступу: без Android. */
object EditAccessModel {

    /**
     * @param editability результат `checkEditability` (null — не вдалося відкрити для редагування)
     * @param passwordEntered чи передавали пароль у цей виклик
     * @param viewerOpened чи переглядач уже відкрив документ
     */
    fun resolve(editability: DocumentEditability?, passwordEntered: Boolean, viewerOpened: Boolean): EditAccess = when {
        editability != null ->
            if (editability.isEncrypted) EditAccess.Restricted(editability.canMarkup, editability.canFillForms)
            else EditAccess.Open(editability.canMarkup, editability.canFillForms)
        passwordEntered -> EditAccess.WrongPassword
        viewerOpened -> EditAccess.PasswordRequired
        else -> EditAccess.Unreadable
    }

    fun canMarkup(access: EditAccess): Boolean = when (access) {
        is EditAccess.Open -> access.canMarkup
        is EditAccess.Restricted -> access.canMarkup
        else -> false
    }

    fun canFillForms(access: EditAccess): Boolean = when (access) {
        is EditAccess.Open -> access.canFillForms
        is EditAccess.Restricted -> access.canFillForms
        else -> false
    }

    /** Чи треба спершу запитати пароль. */
    fun needsPassword(access: EditAccess): Boolean =
        access === EditAccess.PasswordRequired || access === EditAccess.WrongPassword

    /** Режим шторки «Форми». */
    enum class FormsMode { EDITABLE, READ_ONLY, UNAVAILABLE }

    fun formsMode(access: EditAccess): FormsMode = when (access) {
        is EditAccess.Open, is EditAccess.Restricted -> if (canFillForms(access)) FormsMode.EDITABLE else FormsMode.READ_ONLY
        else -> FormsMode.UNAVAILABLE
    }

    enum class SaveTarget { SAME_URI, NEW_COPY, DENIED, UNAVAILABLE }

    /**
     * Куди зберігати: незашифрований — той самий URI (3d); зашифрований — лише нова копія (R15). Об'єкти потребують
     * canMarkup, зміни форм — canFillForms; інакше DENIED. Станам без доступу — UNAVAILABLE.
     */
    fun saveTarget(access: EditAccess, hasItems: Boolean, hasForms: Boolean): SaveTarget {
        val markup: Boolean
        val forms: Boolean
        val encrypted: Boolean
        when (access) {
            is EditAccess.Open -> { markup = access.canMarkup; forms = access.canFillForms; encrypted = false }
            is EditAccess.Restricted -> { markup = access.canMarkup; forms = access.canFillForms; encrypted = true }
            else -> return SaveTarget.UNAVAILABLE
        }
        if (hasItems && !markup) return SaveTarget.DENIED
        if (hasForms && !forms) return SaveTarget.DENIED
        return if (encrypted) SaveTarget.NEW_COPY else SaveTarget.SAME_URI
    }
}

/** Пропонована назва копії зашифрованого документа: «<ім'я>_edited.pdf». */
object CopyNaming {
    fun suggestedName(title: String?): String {
        val base = (title ?: "").trim().let { if (it.endsWith(".pdf", ignoreCase = true)) it.dropLast(4) else it }.trim()
        return (if (base.isEmpty()) "document" else base) + "_edited.pdf"
    }
}

/**
 * Д1 (QA 3e): якщо збереження копії не вдалося, у вибраній папці лишається порожній файл «<ім'я>_edited.pdf».
 * Рішення чисте (тестується без Android): видаляти лише URI, який повернув наш CreateDocument у цьому потоці,
 * і лише якщо розмір відомо й він дорівнює 0. Невідомий розмір (null) — НЕ видаляти.
 */
object EmptyCopyCleanup {
    fun shouldDelete(isCopy: Boolean, sizeBytes: Long?, createdByThisFlow: Boolean): Boolean =
        isCopy && createdByThisFlow && sizeBytes == 0L
}

/** Результат спроби прибрати порожню копію. */
enum class DeleteResult { NOT_ATTEMPTED, DELETED, FAILED }

/** Повідомлення про підсумок збереження (тексти беруться з ресурсів у активності). */
enum class SaveNotice { NO_CHANGES_COPY_NOT_CREATED, NO_CHANGES, EMPTY_FILE_LEFT, SAVE_NOT_COMPLETED, COPY_SAVED, COPY_SAVED_PASSWORD_HINT }

/** Чистий вибір повідомлень: без Android, тестується в JVM. */
object SaveNotices {
    /**
     * Нічого зберігати. Для копії: якщо порожній файл прибрано — «Змін немає, копію не створено» (не «успіх»);
     * якщо файл лишився з іншої причини — просто «Змін немає». Запис у сам документ мовчки завершується, як раніше.
     */
    fun forNothingToSave(copy: Boolean, deleted: DeleteResult): List<SaveNotice> = when {
        !copy -> emptyList()
        deleted == DeleteResult.DELETED -> listOf(SaveNotice.NO_CHANGES_COPY_NOT_CREATED)
        deleted == DeleteResult.FAILED -> listOf(SaveNotice.NO_CHANGES, SaveNotice.EMPTY_FILE_LEFT)
        else -> listOf(SaveNotice.NO_CHANGES)
    }

    /**
     * Успіх збереження копії (Д2). Якщо копію записано з паролем, переглядач відкриє її без пароля в пам'яті (розшифровану
     * копію в кеш не кладемо, пароль у бібліотеку не передаємо) і попросить пароль ще раз: попереджаємо про це.
     */
    fun forCopySaved(passwordUsed: Boolean): List<SaveNotice> =
        if (passwordUsed) listOf(SaveNotice.COPY_SAVED, SaveNotice.COPY_SAVED_PASSWORD_HINT) else listOf(SaveNotice.COPY_SAVED)

    /** Збереження копії не завершено (скасовано пароль або втрачено стан): «Збереження не завершено» (+ файл лишився). */
    fun forUnfinished(deleted: DeleteResult): List<SaveNotice> =
        listOf(SaveNotice.SAVE_NOT_COMPLETED) + forFailed(deleted)

    /** Додаток до повідомлення про збій: не мовчимо, якщо прибрати порожній файл не вдалося. */
    fun forFailed(deleted: DeleteResult): List<SaveNotice> =
        if (deleted == DeleteResult.FAILED) listOf(SaveNotice.EMPTY_FILE_LEFT) else emptyList()
}

/** Що робити з результатом системного вибору місця для копії. */
enum class CopyPickAction { CANCELLED, SAVE, DISCARD_EMPTY }

object CopyPick {
    /**
     * @param uriChosen користувач обрав місце (CreateDocument повернув URI)
     * @param awaitingFlag ознака «чекаю місце для копії», збережена в SavedStateHandle перед запуском вибору
     * Ознаки нема (процес був вбитий і стан не відновлено, або це не наш запит) — нічого не зберігаємо, порожній файл прибираємо.
     */
    fun resolve(uriChosen: Boolean, awaitingFlag: Boolean): CopyPickAction = when {
        !uriChosen -> CopyPickAction.CANCELLED
        awaitingFlag -> CopyPickAction.SAVE
        else -> CopyPickAction.DISCARD_EMPTY
    }
}
