package com.example.fipscan

import android.util.Log

object ExtractData {

    fun parseLabResults(csvLines: List<String>): Map<String, Any> {
        val extractedData = mutableMapOf<String, Any>()
        val results = mutableListOf<String>()
        var dateCollectedSet = false  // Flaga do zapisania pierwszej wartości "Data pobrania materiału"
        var lastTestName: String? = null  // Przechowuje nazwę badania dla "Wynik"

        // Szukaj danych pacjenta
        for (line in csvLines) {
            if (line.contains("Pacjent:") || line.contains("Gatunek:") ||
                line.contains("Płeć:") || line.contains("Wiek:") ||
                line.contains("Umaszczenie:") || line.contains("Mikrochip:")) {
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

                //if (isOutOfRange(value, range[0], range.getOrElse(1) { value })) {
                results.add("$resolvedName $value (norma ${range[0]} - ${range.getOrElse(1) { value }}) $unit")
                //}

                Log.d("DATA", "$resolvedName: ${extractedData[resolvedName]} (${extractedData["${resolvedName}RangeMin"]} - ${extractedData["${resolvedName}RangeMax"]}) ${extractedData["${resolvedName}Unit"]}")
            }
        }
        extractedData["results"] = results
        return extractedData
    }

    private fun extractPatientData(text: String, data: MutableMap<String, Any>) {
        val regex = Regex("(Pacjent:|Gatunek:|Rasa:|Płeć:|Wiek:|Umaszczenie:|Mikrochip:)")
        val matches = regex.findAll(text.replace("\n", " "))

        var currentKey = ""
        var currentValue = StringBuilder()

        matches.forEachIndexed { index, match ->
            if (currentKey.isNotEmpty()) {
                // Dodanie wartości do mapy
                data[currentKey] = currentValue.toString().trim()
                currentValue.clear()
            }
            currentKey = match.value.removeSuffix(":").trim() // Usuń dwukropek na końcu klucza

            // Pobranie wartości między bieżącym a następnym kluczem
            val startIndex = match.range.last + 1
            val endIndex = if (index + 1 < matches.count()) matches.elementAt(index + 1).range.first else text.length
            currentValue.append(text.substring(startIndex, endIndex).replace(";", "").trim())
        }

        // Zapisz ostatnią wartość po zakończeniu pętli
        if (currentKey.isNotEmpty()) {
            data[currentKey] = currentValue.toString().trim()
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

    private fun removeParentheses(str: String): String {
        return str.replace(Regex("""\([^)]*\)"""), "").trim()
    }

    private fun containsNumericValue(str: String): Boolean {
        return str.contains(Regex("""\d+[,.]\d+"""))
    }
}
