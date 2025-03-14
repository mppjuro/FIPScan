package com.example.fipscan

import android.util.Log

object ExtractData {

    fun parseLabResults(csvLines: List<String>): Map<String, Any> {
        val extractedData = mutableMapOf<String, Any>()
        val results = mutableListOf<String>()
        var wbcFound = false
        var dateCollectedSet = false  // Flaga do zapisania pierwszej wartości "Data pobrania materiału"
        var lastTestName: String? = null  // Przechowuje nazwę badania dla "Wynik"

        // Szukaj danych pacjenta
        for (line in csvLines) {
            if (line.contains("Pacjent:")) {
                extractPatientData(line, extractedData)
            }
        }

        // Przetwarzaj wyniki badań
        for (line in csvLines) {
            val cleanedLine = removeParentheses(line)  // Usunięcie nawiasów i ich zawartości

            if (cleanedLine.startsWith("Data pobrania materiału") && !dateCollectedSet) {
                extractDateOfCollection(cleanedLine, extractedData)
                dateCollectedSet = true
                Log.d("DATA", "Data pobrania materiału: " + extractedData["Data pobrania materiału"])
                continue
            }

            if (cleanedLine.startsWith("Data ")) {
                continue  // Pominięcie innych wpisów rozpoczynających się od "Data"
            }

            val parts = cleanedLine.split(";").map { it.trim() }

            // Jeśli linia zawiera tylko nazwę badania (np. "Kwas foliowy"), zapamiętaj ją
            if (parts.size == 1 && parts[0].isNotEmpty() && !containsNumericValue(parts[0])) {
                lastTestName = parts[0]
                continue
            }

            if (parts.size >= 3 && parts[0].matches(Regex(".*[<>]?[0-9]+[,.][0-9]+.*"))) {
                var (name, value) = parseParameterName(parts[0])

                // Jeśli nazwa to "Wynik", użyj poprzedniej zapamiętanej nazwy badania
                val resolvedName = if (name == "Wynik" && lastTestName != null) lastTestName else name

                val unit = parts[1]
                val range = parts[2].split("-")

                extractedData[resolvedName] = value
                extractedData["${resolvedName}Unit"] = unit
                extractedData["${resolvedName}RangeMin"] = range[0].replace(",", ".")
                extractedData["${resolvedName}RangeMax"] = range.getOrElse(1) { "-" }.replace(",", ".")

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

    private fun extractDateOfCollection(line: String, data: MutableMap<String, Any>) {
        val regex = Regex("""Data pobrania materiału:\s*(\d{2}\.\d{2}\.\d{4})""")
        val match = regex.find(line)
        if (match != null) {
            data["Data pobrania materiału"] = match.groupValues[1]
        }
    }

    private fun parseParameterName(str: String): Pair<String, String> {
        val regex = Regex("""([^\d<>]+?)\s*([<>]?\d+[,.]\d+)""")
        val match = regex.find(str) ?: return Pair(str, "-")

        val name = match.groupValues[1].trim()
        val value = match.groupValues[2].replace(",", ".")

        return Pair(name, value)
    }

    private fun isOutOfRange(value: String, min: String, max: String): Boolean {
        return try {
            if (value.startsWith("<") || value.startsWith(">")) return false // Dla wartości "<2.00" pomijamy sprawdzanie
            val v = value.toDouble()
            val minVal = min.replace(",", ".").toDoubleOrNull() ?: return false
            val maxVal = max.replace(",", ".").toDoubleOrNull() ?: return false
            v < minVal || v > maxVal
        } catch (e: Exception) {
            false
        }
    }

    private fun removeParentheses(str: String): String {
        return str.replace(Regex("""\([^)]*\)"""), "").trim()
    }

    private fun containsNumericValue(str: String): Boolean {
        return str.contains(Regex("""\d+[,.]\d+"""))
    }
}
