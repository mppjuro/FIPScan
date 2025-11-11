package com.example.fipscan

import android.util.Log
import java.util.regex.Pattern

object ExtractData {
    var lastExtracted: Map<String, Any>? = null
    private val newFormatRegexes = listOf(
        Pattern.compile("^(Stosunek:.*?)\\s+([\\d,.]+)(?:\\s*\\([^)]+\\))?;\\s*([\\d.,\\s-]+)\\s*$"),
        Pattern.compile("^Wynik\\s+([\\d,.]+)(?:\\s*\\([^)]+\\))?;\\s*([^\\d;]+?)\\s+([\\d.,-]+)(?:\\s*\\([^)]+\\))?\\s*$"),
        Pattern.compile("^(.+?)\\s+([\\d,.]+)(?:\\s*\\([^)]+\\))?;\\s*([^\\d;]+?)\\s+([\\d.,-]+)(?:\\s*\\([^)]+\\))?(?:;\\s*([HLX]))?\\s*$")
    )

    fun parseLabResults(csvLines: List<String>): Map<String, Any> {
        val extractedData = mutableMapOf<String, Any>()
        val results = mutableListOf<String>()
        var dateCollectedSet = false
        var lastTestName: String? = null

        val patientLines = mutableListOf<String>()
        csvLines.forEach { line ->
            if (!dateCollectedSet && (line.contains("Data pobrania materiału") || line.contains("Data badania"))) {
                extractDateOfCollection(line, extractedData)
                if (extractedData.containsKey("Data")) {
                    dateCollectedSet = true
                    Log.d("DATA_P", "Data pobrania materiału: " + extractedData["Data"])
                }
            }
            if (line.contains("Pacjent:") || line.contains("Gatunek:") || line.contains("Właściciel:") ||
                line.contains("Rasa:") || line.contains("Płeć:") || line.contains("Wiek:") ||
                line.contains("Lecznica:") || line.contains("Lekarz:") ||
                line.contains("Umaszczenie:") || line.contains("Mikrochip:") || line.contains("Rodzaj próbki")) {
                patientLines.add(line)
            }
        }
        extractPatientData(patientLines.joinToString(separator = " "), extractedData)

        var i = 0
        while (i < csvLines.size) {
            val line = csvLines[i].trim()
            Log.d("CLEAR_LINE", line)

            if (line.isBlank() || line.startsWith("Badanie Wynik") || line.startsWith("Badanie,")) {
                lastTestName = null
                i++
                continue
            }

            if (line.contains("Pacjent:") || line.contains("Gatunek:") || line.contains("Właściciel:") || line.contains("Data pobrania materiału:") || line.contains("Data badania:") || line.contains("Lecznica:") || line.contains("Lekarz:")) {
                i++
                continue
            }

            if (line.contains("FCoV", ignoreCase = true) &&
                line.contains("przeciwciała", ignoreCase = true) &&
                line.contains("ELISA", ignoreCase = true)) {
                if (i + 2 < csvLines.size) {
                    val resultLine = csvLines[i + 1].replace("Wynik", "", ignoreCase = true).trim()
                    val valueLine = csvLines[i + 2].replace("Wartość", "", ignoreCase = true).trim()
                    val parts = valueLine.split(";").map { it.trim() }

                    val elisaValue = parts.getOrNull(0)?.replace(",", ".") ?: "-"
                    val elisaUnit = parts.getOrNull(1) ?: ""

                    extractedData["FCoV (ELISA)"] = elisaValue
                    extractedData["FCoV (ELISA)Unit"] = elisaUnit
                    extractedData["FCoV (ELISA)Range"] = resultLine

                    Log.d("DATA", "FCoV (ELISA): $elisaValue ($resultLine) $elisaUnit")
                    results.add("FCoV (ELISA): $elisaValue ($resultLine) $elisaUnit")

                    i += 3
                    continue
                }
            }

            var newFormatMatched = false
            for ((patternIndex, pattern) in newFormatRegexes.withIndex()) {
                val matcher = pattern.matcher(line)
                if (matcher.find()) {
                    newFormatMatched = true
                    try {
                        when (patternIndex) {
                            0 -> {
                                val name = matcher.group(1)?.trim() ?: "Stosunek Nieznany"
                                val value = matcher.group(2)?.trim()?.replace(',', '.') ?: "-"
                                val rangeStr = matcher.group(3)?.trim() ?: "-"
                                val rangeParts = rangeStr.split("-").map { it.trim().replace(",", ".") }
                                // FIX: split zawsze zwraca co najmniej jeden element, więc getOrNull(0) nie zwróci null, jeśli rangeParts nie jest puste (a nie jest).
                                val minRange = rangeParts[0]
                                val maxRange = rangeParts.getOrNull(1) ?: "-"
                                val unit = ""

                                extractedData[name] = value
                                extractedData["${name}Unit"] = unit
                                extractedData["${name}RangeMin"] = minRange
                                extractedData["${name}RangeMax"] = maxRange
                                Log.d("DATA", "$name: $value ($minRange - $maxRange)")
                                results.add("$name: $value (norma $minRange - $maxRange)")
                            }
                            1 -> {
                                val name = lastTestName ?: "Wynik Nienazwany"
                                val value = matcher.group(1)?.trim()?.replace(',', '.') ?: "-"
                                var unit = matcher.group(2)?.trim() ?: ""
                                val rangeStr = matcher.group(3)?.trim() ?: "-"
                                unit = unit.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
                                val rangeParts = rangeStr.split("-").map { it.trim().replace(",", ".") }
                                val minRange = rangeParts[0]
                                val maxRange = rangeParts.getOrNull(1) ?: "-"

                                extractedData[name] = value
                                extractedData["${name}Unit"] = unit
                                extractedData["${name}RangeMin"] = minRange
                                extractedData["${name}RangeMax"] = maxRange
                                Log.d("DATA", "$name: $value ($minRange - $maxRange) $unit")
                                results.add("$name: $value (norma $minRange - $maxRange) $unit")
                            }
                            2 -> {
                                val name = matcher.group(1)?.trim() ?: "Parametr Nieznany"
                                val value = matcher.group(2)?.trim()?.replace(',', '.') ?: "-"
                                var unit = matcher.group(3)?.trim() ?: ""
                                val rangeStr = matcher.group(4)?.trim() ?: "-"
                                val flag = matcher.group(5)?.trim() ?: ""
                                unit = unit.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
                                val rangeParts = rangeStr.split("-").map { it.trim().replace(",", ".") }
                                val minRange = rangeParts[0]
                                val maxRange = rangeParts.getOrNull(1) ?: "-"

                                extractedData[name] = value
                                extractedData["${name}Unit"] = unit
                                extractedData["${name}RangeMin"] = minRange
                                extractedData["${name}RangeMax"] = maxRange
                                if (flag.isNotEmpty()) {
                                    extractedData["${name}Flag"] = flag
                                }
                                Log.d("DATA", "$name: $value ($minRange - $maxRange) $unit ${if(flag.isNotEmpty()) "[$flag]" else ""}")
                                results.add("$name: $value (norma $minRange - $maxRange) $unit ${if(flag.isNotEmpty()) "[$flag]" else ""}")
                            }
                        }
                        lastTestName = null
                    } catch (e: Exception) {
                        Log.e("ExtractData", "Błąd przetwarzania nowego formatu dla linii: '$line' z wzorcem $patternIndex", e)
                    }
                    break
                }
            }

            if (!newFormatMatched) {
                if (!containsNumericValue(line) && line.isNotEmpty() && !line.contains(";") && !line.startsWith("Uwagi") && !line.startsWith("Interpretacja") && !line.startsWith("Elektroforeza") && !line.startsWith("Morfologia")) {
                    lastTestName = line.replace(":", "").trim()
                    Log.d("ExtractData", "Potencjalna nazwa badania (stary format): $lastTestName")
                } else {
                    val parts = line.split(";").map { it.trim() }

                    if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                        val (namePart, valuePart) = parseParameterName(parts[0])

                        val resolvedName = if (namePart.equals("Wynik", ignoreCase = true) && lastTestName != null) {
                            lastTestName
                        } else {
                            namePart
                        }

                        if (resolvedName.isNotEmpty() && valuePart != "-") {
                            val remainingParts = parts.drop(1)
                            var unit = ""
                            var rangeStr = ""
                            val rangeRegex = Regex("""[<>]?\d+[,.]?\d*\s*-\s*[<>]?\d+[,.]?\d*""")

                            val rangePartIndex = remainingParts.indexOfFirst { rangeRegex.matches(it) }
                            if (rangePartIndex != -1) {
                                rangeStr = remainingParts[rangePartIndex]
                                if (rangePartIndex > 0) {
                                    unit = remainingParts[rangePartIndex - 1]
                                } else if (remainingParts.size > 1) {
                                    unit = remainingParts[1]
                                }
                            } else if (remainingParts.isNotEmpty()) {
                                unit = remainingParts[0]
                            }

                            val value = valuePart.replace(',', '.')
                            unit = unit.replace(Regex("""\([^)]*\)"""), "").trim()
                            val rangeParts = rangeStr.split("-").map { it.trim().replace(",", ".") }
                            // FIX: split zawsze zwraca listę, element 0 jest bezpieczny.
                            val minRange = rangeParts[0].replace(Regex("[<>]"), "")
                            val maxRange = rangeParts.getOrNull(1)?.replace(Regex("[<>]"), "") ?: "-"

                            if (!resolvedName.equals("Badanie", ignoreCase = true) && !resolvedName.contains("Norma") && !resolvedName.contains("Jedn.")) {
                                extractedData[resolvedName] = value
                                extractedData["${resolvedName}Unit"] = unit
                                extractedData["${resolvedName}RangeMin"] = minRange
                                extractedData["${resolvedName}RangeMax"] = maxRange
                                Log.d("DATA", "(Fallback) $resolvedName: $value ($minRange - $maxRange) $unit")
                                results.add("$resolvedName: $value (norma $minRange - $maxRange) $unit")
                                lastTestName = null
                            } else {
                                Log.d("ExtractData", "(Fallback) Ignorowanie linii nagłówka/niesparowanej: $line")
                            }

                        } else {
                            Log.d("ExtractData", "(Fallback) Nie udało się sparsować nazwy/wartości dla linii: $line")
                            if (containsNumericValue(line)) {
                                lastTestName = null
                            }
                        }
                    } else if (line.isNotEmpty()){
                        lastTestName = null
                        Log.d("ExtractData", "Ignorowanie niesparowanej linii: $line")
                    }
                }
            }
            i++
        }

        extractedData["results"] = results
        lastExtracted = extractedData
        Log.d("ExtractData", "Zakończono parsowanie. Wynik: ${extractedData.keys.joinToString()}")
        return extractedData
    }

    private fun extractPatientData(text: String, data: MutableMap<String, Any>) {
        val keys = listOf("Właściciel:", "Pacjent:", "Gatunek:", "Rasa:", "Płeć:", "Wiek:", "Umaszczenie:", "Mikrochip:", "Lecznica:", "Lekarz:", "Rodzaj próbki:")

        val remainingText = text.replace("\n", " ").replace(";", " ")

        keys.forEach { key ->
            val keyIndex = remainingText.indexOf(key, ignoreCase = true)
            if (keyIndex != -1) {
                val valueStartIndex = keyIndex + key.length
                var valueEndIndex = remainingText.length
                keys.forEach { nextKey ->
                    if (nextKey != key) {
                        val nextKeyIndex = remainingText.indexOf(nextKey, startIndex = valueStartIndex, ignoreCase = true)
                        if (nextKeyIndex != -1 && nextKeyIndex < valueEndIndex) {
                            valueEndIndex = nextKeyIndex
                        }
                    }
                }

                val value = remainingText.substring(valueStartIndex, valueEndIndex).trim()
                val cleanKey = key.removeSuffix(":").trim()

                if (value.isNotEmpty() && !data.containsKey(cleanKey)) {
                    data[cleanKey] = value
                    Log.d("DATA_P", "Znaleziono: $cleanKey = $value")
                }
            }
        }
        if (!data.containsKey("Gatunek") && data.containsKey("Pacjent") && data["Pacjent"] is String) {
            val patientLine = data["Pacjent"] as String
            val gatunekMatch = Regex("Gatunek:\\s*(\\S+)").find(patientLine)
            if (gatunekMatch != null) data["Gatunek"] = gatunekMatch.groupValues[1]
        }

    }

    private fun extractDateOfCollection(line: String, data: MutableMap<String, Any>) {
        val regex = Regex("""Data pobrania materiału:\s*(\d{2}\.\d{2}\.\d{4})""")
        val match = regex.find(line)
        if (match != null) {
            data["Data"] = match.groupValues[1]
        } else {
            val dateFallbackRegex = Regex("""(\d{2}\.\d{2}\.\d{4})""")
            val fallbackMatch = dateFallbackRegex.find(line)
            if (fallbackMatch != null && !line.contains("Data badania", ignoreCase = true)) {
                if (!data.containsKey("Data")) {
                    data["Data"] = fallbackMatch.groupValues[1]
                    Log.d("DATA_P", "Znaleziono datę (fallback): " + data["Data"])
                }
            }
        }
    }

    private fun parseParameterName(str: String): Pair<String, String> {
        val regex = Regex("""([^\d<>]*[a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ:/\s-]+)\s*([<>]?\d+[,.]\d+)""")
        val match = regex.find(str.trim())

        if (match != null && match.groupValues.size == 3) {
            val name = match.groupValues[1].trim()
            val value = match.groupValues[2].trim()
            if (value.matches(Regex("[<>]?[\\d.,]+"))) {
                return Pair(name, value)
            }
        }
        return Pair(str.trim(), "-")
    }

    private fun containsNumericValue(str: String): Boolean {
        return str.contains(Regex("""\d[,.]?\d"""))
    }
}