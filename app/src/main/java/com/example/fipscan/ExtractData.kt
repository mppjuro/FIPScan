package com.example.fipscan

import android.util.Log

object ExtractData {

    fun parseLabResults(csvLines: List<String>): Map<String, Any> {
        val extractedData = mutableMapOf<String, Any>()
        val results = mutableListOf<String>()
        var wbcFound = false
        var lastTestName: String? = null  // Przechowuje nazwę badania dla "Wynik"

        // Szukaj danych pacjenta
        for (line in csvLines) {
            if (line.contains("Pacjent:")) {
                extractPatientData(line, extractedData)
            }
        }

        // Przetwarzaj wyniki badań
        for (line in csvLines) {
            val parts = line.split(";").map { it.trim() }

            if (parts.size == 1 && parts[0].isNotEmpty() && !containsNumericValue(parts[0])) {
                // Jeśli linia zawiera tylko nazwę badania (np. "Kwas foliowy"), zapamiętaj ją
                lastTestName = parts[0]
                continue
            }

            if (parts.size >= 3 && containsNumericValue(parts[0])) {
                var (name, value) = extractParameterAndValue(parts[0])

                // Jeśli nazwa to "Wynik", użyj poprzedniej zapamiętanej nazwy badania
                val resolvedName = if (name == "Wynik" && lastTestName != null) lastTestName else name

                val unit = cleanValue(parts[1])
                val range = cleanValue(parts[2]).split("-")

                extractedData[resolvedName] = value
                extractedData["${resolvedName}Unit"] = unit
                extractedData["${resolvedName}RangeMin"] = range[0]
                extractedData["${resolvedName}RangeMax"] = range.getOrElse(1) { "-" }

                if (isOutOfRange(value, range[0], range.getOrElse(1) { value })) {
                    results.add("$resolvedName $value (norma ${range[0]} - ${range.getOrElse(1) { value }}) $unit")
                }

                Log.d("DATA", "$resolvedName: ${extractedData[resolvedName]} (${extractedData["${resolvedName}RangeMin"]} - ${extractedData["${resolvedName}RangeMax"]}) ${extractedData["${resolvedName}Unit"]}")

                if (resolvedName == "WBC") wbcFound = true
            }
        }

        if (!wbcFound) {
            extractedData["WBC"] = "-"
            extractedData["WBCUnit"] = "G/l"
            extractedData["WBCRangeMin"] = "-"
            extractedData["WBCRangeMax"] = "-"
        }

        extractedData["results"] = results
        return extractedData
    }

    private fun extractPatientData(line: String, data: MutableMap<String, Any>) {
        val segments = line.split(";")
            .joinToString(";") { it.trim() }
            .split(Regex("(?<=;)|(?=Pacjent:|Gatunek:|Rasa:|Płeć:|Wiek:|Umaszczenie:|Mikrochip:)"))
            .filter { it.isNotBlank() }

        var currentKey = ""
        segments.forEach { segment ->
            when {
                segment.startsWith("Pacjent:") -> currentKey = "Pacjent"
                segment.startsWith("Gatunek:") -> currentKey = "Gatunek"
                segment.startsWith("Rasa:") -> currentKey = "Rasa"
                segment.startsWith("Płeć:") -> currentKey = "Płeć"
                segment.startsWith("Wiek:") -> currentKey = "Wiek"
                segment.startsWith("Umaszczenie:") -> currentKey = "Umaszczenie"
                segment.startsWith("Mikrochip:") -> currentKey = "Mikrochip"
                else -> if (currentKey.isNotEmpty()) {
                    val value = segment.replace(Regex("[:;]"), "").trim()
                    data[currentKey] = value
                    currentKey = ""
                }
            }
        }
    }

    private fun extractParameterAndValue(str: String): Pair<String, String> {
        val regex = Regex("""^([\p{L} %/.-]+?)\s+(\d+[,.]\d+)""")  // Umożliwia nazwę z %, /, -, .
        val match = regex.find(str) ?: return Pair(str, "-")

        val name = match.groupValues[1].trim()
        val value = match.groupValues[2].replace(",", ".")  // Zastępuje przecinki na kropki

        return Pair(name, value)
    }

    private fun cleanValue(value: String): String {
        return value.replace(Regex("""\s*\(.*?\)\s*"""), "")  // Usuwa nawiasy i ich zawartość
            .replace(",", ".")
            .trim()
    }

    private fun containsNumericValue(str: String): Boolean {
        return str.contains(Regex("""\d+[,.]\d+"""))
    }

    private fun isOutOfRange(value: String, min: String, max: String): Boolean {
        return try {
            val v = value.toDouble()
            val minVal = min.replace(",", ".").toDoubleOrNull() ?: return false
            val maxVal = max.replace(",", ".").toDoubleOrNull() ?: return false
            v < minVal || v > maxVal
        } catch (e: Exception) {
            false
        }
    }
}
