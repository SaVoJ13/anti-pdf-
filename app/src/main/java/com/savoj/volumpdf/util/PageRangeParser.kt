package com.savoj.volumpdf.util

object PageRangeParser {

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
            throw IllegalArgumentException("Введіть номери сторінок")
        }

        val resultIndices = mutableSetOf<Int>()
        val parts = trimmed.split(',', ';')

        for (part in parts) {
            val token = part.trim()
            if (token.isEmpty()) continue

            if (token.contains('-')) {
                val rangeParts = token.split('-')
                if (rangeParts.size != 2) {
                    throw IllegalArgumentException("Некоректний діапазон сторінок: '$token'")
                }

                val startPage = rangeParts[0].trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Некоректний номер початку діапазону: '${rangeParts[0]}'")
                val endPage = rangeParts[1].trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Некоректний номер кінця діапазону: '${rangeParts[1]}'")

                if (startPage < 1 || endPage < 1) {
                    throw IllegalArgumentException("Номери сторінок мають бути більше 0")
                }

                val min = minOf(startPage, endPage)
                val max = maxOf(startPage, endPage)

                for (page in min..max) {
                    val index0 = page - 1
                    if (totalPages > 0 && index0 >= totalPages) {
                        throw IllegalArgumentException("Сторінка $page виходить за межі документа (всього сторінок: $totalPages)")
                    }
                    resultIndices.add(index0)
                }
            } else {
                val page = token.toIntOrNull()
                    ?: throw IllegalArgumentException("Некоректний номер сторінки: '$token'")

                if (page < 1) {
                    throw IllegalArgumentException("Номер сторінки має бути більше 0: $page")
                }

                val index0 = page - 1
                if (totalPages > 0 && index0 >= totalPages) {
                    throw IllegalArgumentException("Сторінка $page виходить за межі документа (всього сторінок: $totalPages)")
                }
                resultIndices.add(index0)
            }
        }

        if (resultIndices.isEmpty()) {
            throw IllegalArgumentException("Не вказано жодної дійсної сторінки")
        }

        return resultIndices.sorted()
    }
}
