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
        var i = 0
        while (i < csvLines.size) {
            //Log.d("LINE", "${csvLines[i]}")
            val line = csvLines[i]
            val cleanedLine = removeParentheses(line)

            // Obsługa FCoV (ELISA)
            if (line.contains("FCoV", ignoreCase = true) &&
                line.contains("przeciwciała", ignoreCase = true) &&
                line.contains("ELISA", ignoreCase = true)) {
                if (i + 2 < csvLines.size) {
                    val resultLine = csvLines[i + 1].replace("Wynik", "", ignoreCase = true).trim()
                    val valueLine = csvLines[i + 2].replace("Wartość", "", ignoreCase = true).trim()
                    val parts = valueLine.split(";").map { it.trim() }

                    extractedData["FCoV (ELISA)RangeMax"] = resultLine
                    extractedData["FCoV (ELISA)"] = parts.getOrNull(0) ?: "-"
                    extractedData["FCoV (ELISA)Unit"] = parts.getOrNull(1) ?: ""

                    Log.d("DATA", "FCoV (ELISA): ${extractedData["FCoV (ELISA)"]} (${extractedData["FCoV (ELISA)RangeMax"]}) ${extractedData["FCoV (ELISA)Unit"]}")

                    i += 3
                    continue
                }
            }

            if (cleanedLine.startsWith("Data pobrania materiału") && !dateCollectedSet) {
                extractDateOfCollection(cleanedLine, extractedData)
                dateCollectedSet = true
                Log.d("DATA", "Data pobrania materiału: " + extractedData["Data pobrania materiału"])
                i++
                continue
            }

            if (cleanedLine.startsWith("Data ")) {
                i++
                continue
            }

            val parts = cleanedLine.split(";").map { it.trim() }

            if (parts.size == 1 && parts[0].isNotEmpty() && !containsNumericValue(parts[0])) {
                lastTestName = parts[0]
                i++
                continue
            }

            if (parts.size >= 2 && parts[0].matches(Regex(".*[<>]?[0-9]+[,.][0-9]+.*"))) {
                val (name, value) = parseParameterName(parts[0])
                val resolvedName = if (name == "Wynik" && lastTestName != null) lastTestName else name

                val secondField = parts.getOrNull(1)?.trim() ?: ""
                val thirdField = parts.getOrNull(2)?.trim() ?: ""

                val rangeRegex = Regex("""\d+,*\d*\s*-\s*\d+,*\d*""")

                val (unit, rangeStr) = if (secondField.matches(rangeRegex)) {
                    "" to secondField
                } else {
                    secondField to thirdField
                }

                val rangeParts = rangeStr.split("-").map { it.trim().replace(",", ".") }
                val minRange = rangeParts.getOrNull(0) ?: "-"
                val maxRange = rangeParts.getOrNull(1) ?: "-"

                extractedData[resolvedName] = value
                extractedData["${resolvedName}Unit"] = unit
                extractedData["${resolvedName}RangeMin"] = minRange
                extractedData["${resolvedName}RangeMax"] = maxRange

                results.add("$resolvedName $value (norma $minRange - $maxRange) $unit")

                Log.d("DATA", "$resolvedName: ${extractedData[resolvedName]} ($minRange - $maxRange) $unit")
            }

            i++
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
        val regex = Regex("""([^\d<>]*[a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ:/\s-]+)\s*([<>]?\d+[,.]\d+)""")
        val match = regex.find(str) ?: return Pair(str.trim(), "-")
        return Pair(match.groupValues[1].trim(), match.groupValues[2])
    }

    private fun removeParentheses(str: String): String {
        return str.replace(Regex("""\([^)]*\)"""), "").trim()
    }

    private fun containsNumericValue(str: String): Boolean {
        return str.contains(Regex("""\d+[,.]\d+"""))
    }
}
