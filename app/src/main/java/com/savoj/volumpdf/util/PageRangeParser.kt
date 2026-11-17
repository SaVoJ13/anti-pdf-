package com.savoj.volumpdf.util

object PageRangeParser {

    /**
     * Перевірка діапазону ДО системного діалогу збереження (QA2 Д1): `null` — рядок прийнятний, інакше причина.
     * Порожній рядок для необов'язкового поля ([required] = false) прийнятний («усі сторінки»).
     */
    fun validationError(input: String?, totalPages: Int, required: Boolean): PageRangeException? {
        if (input.isNullOrBlank() && !required) return null
        return try {
            parsePageIndices(input.orEmpty(), totalPages)
            null
        } catch (e: PageRangeException) {
            e
        }
    }

    /**
     * Парсить рядок номерів сторінок (1-based, наприклад: "1-3, 5, 8-10")
     * у список унікальних 0-based індексів сторінок у порядку зростання.
     *
     * @param input Рядок із номерами сторінок.
     * @param totalPages Кількість сторінок у документі (якщо відома > 0 для перевірки меж).
     * @return Відсортований список 0-based індексів.
     * @throws IllegalArgumentException якщо рядок має некоректний формат або індекси поза межами.
     */
    fun parsePageIndices(input: String, totalPages: Int = 0): List<Int> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            throw PageRangeException(PageRangeException.Reason.EMPTY, technical = "empty input")
        }

        val resultIndices = mutableSetOf<Int>()
        val parts = trimmed.split(',', ';')

        for (part in parts) {
            val token = part.trim()
            if (token.isEmpty()) continue

            if (token.contains('-')) {
                val rangeParts = token.split('-')
                if (rangeParts.size != 2) {
                    throw PageRangeException(PageRangeException.Reason.BAD_FORMAT, technical = "bad range")
                }

                val startPage = rangeParts[0].trim().toIntOrNull()
                    ?: throw PageRangeException(PageRangeException.Reason.BAD_FORMAT, technical = "bad range start")
                val endPage = rangeParts[1].trim().toIntOrNull()
                    ?: throw PageRangeException(PageRangeException.Reason.BAD_FORMAT, technical = "bad range end")

                if (startPage < 1 || endPage < 1) {
                    throw PageRangeException(PageRangeException.Reason.NOT_POSITIVE, technical = "page < 1")
                }

                val min = minOf(startPage, endPage)
                val max = maxOf(startPage, endPage)

                for (page in min..max) {
                    val index0 = page - 1
                    if (totalPages > 0 && index0 >= totalPages) {
                        throw PageRangeException(PageRangeException.Reason.OUT_OF_RANGE, page, totalPages, technical = "page $page out of $totalPages")
                    }
                    resultIndices.add(index0)
                }
            } else {
                val page = token.toIntOrNull()
                    ?: throw PageRangeException(PageRangeException.Reason.BAD_FORMAT, technical = "bad page number")

                if (page < 1) {
                    throw PageRangeException(PageRangeException.Reason.NOT_POSITIVE, page, technical = "page < 1")
                }

                val index0 = page - 1
                if (totalPages > 0 && index0 >= totalPages) {
                    throw PageRangeException(PageRangeException.Reason.OUT_OF_RANGE, page, totalPages, technical = "page $page out of $totalPages")
                }
                resultIndices.add(index0)
            }
        }

        if (resultIndices.isEmpty()) {
            throw PageRangeException(PageRangeException.Reason.NONE_SELECTED, technical = "no valid pages")
        }

        return resultIndices.sorted()
    }
}
