package com.example.fipscan

object ExtractData {

    fun parseLabResults(text: String): Map<String, Any> {
        val extractedData = mutableMapOf<String, Any>()

        extractedData["Pacjent"] =
            extractRegex(Regex("Pacjent:\\s+(\\S+)"), text) ?: "-"
        extractedData["Gatunek"] =
            extractRegex(Regex("Gatunek:\\s+(\\S+)"), text) ?: "-"
        extractedData["Rasa"] =
            extractRegex(Regex("Rasa:\\s+(.+?)\\s+Płeć:"), text) ?: "-"
        extractedData["Płeć"] =
            extractRegex(Regex("Płeć:\\s+(\\S+)\\s+Wiek:"), text) ?: "-"
        extractedData["Wiek1"] =
            extractRegex(Regex("Wiek:\\s+(.*?)(?=\\s*Umaszczenie)"), text) ?: "-"
        extractedData["Wiek2"] =
            extractRegex(Regex("(?m)Wiek:\\s+(.*?)\\s*$"), text) ?: "-"
        extractedData["Wiek"] = when {
            extractedData["Wiek1"] != "-" -> extractedData["Wiek1"]
            extractedData["Wiek2"] != "-" -> extractedData["Wiek2"]
            else -> "-"
        } as Any
        extractedData["Umaszczenie"] =
            extractRegex(Regex("Umaszczenie:\\s+(\\S.+)"), text) ?: "-"
        extractedData["Mikrochip"] =
            extractRegex(Regex("Mikrochip:\\s+(\\d{15})"), text) ?: "-"

        // WBC
        extractedData["Wbc"] =
            extractRegex(Regex("WBC\\s+G/l\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["WbcUnit"] =
            extractRegex(Regex("WBC\\s+(G/l)"), text) ?: "-"
        extractedData["WbcRangeMin"] =
            extractRegex(Regex("WBC\\s+G/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["WbcRangeMax"] =
            extractRegex(Regex("WBC\\s+G/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // NEU
        extractedData["Neu"] =
            extractRegex(Regex("NEU\\s+G/l\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["NeuUnit"] =
            extractRegex(Regex("NEU\\s+(G/l)"), text) ?: "-"
        extractedData["NeuRangeMin"] =
            extractRegex(Regex("NEU\\s+G/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["NeuRangeMax"] =
            extractRegex(Regex("NEU\\s+G/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // NEU %
        extractedData["Neu%"] =
            extractRegex(Regex("NEU %\\s+%\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["Neu%RangeMin"] =
            extractRegex(Regex("NEU %\\s+%\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["NeuRangeMax%"] =
            extractRegex(Regex("NEU %\\s+%\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // LYM
        extractedData["Lym"] =
            extractRegex(Regex("LYM\\s+G/l\\s+([\\d,.]+)"), text) ?: "-"
        extractedData["LymUnit"] = "G/l"
        extractedData["LymRangeMin"] =
            extractRegex(Regex("LYM\\s+G/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["LymRangeMax"] =
            extractRegex(Regex("LYM\\s+G/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // RBC
        extractedData["Rbc"] =
            extractRegex(Regex("RBC\\s+T/l\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["RbcUnit"] =
            extractRegex(Regex("RBC\\s+(T/l)"), text) ?: "-"
        extractedData["RbcRangeMin"] =
            extractRegex(Regex("RBC\\s+T/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["RbcRangeMax"] =
            extractRegex(Regex("RBC\\s+T/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // HGB
        extractedData["Hgb"] =
            extractRegex(Regex("HGB\\s+g/l\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["HgbUnit"] =
            extractRegex(Regex("HGB\\s+(g/l)"), text) ?: "-"
        extractedData["HgbRangeMin"] =
            extractRegex(Regex("HGB\\s+g/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["HgbRangeMax"] =
            extractRegex(Regex("HGB\\s+g/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Albuminy
        extractedData["Albuminy"] =
            extractRegex(Regex("Albuminy\\s+[g/l|g/dl]*\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["AlbuminyUnit"] =
            extractRegex(Regex("Albuminy\\s+(g/l|g/dl)"), text) ?: "-"
        extractedData["AlbuminyRangeMin"] =
            extractRegex(Regex("Albuminy\\s+[g/l|g/dl]*\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["AlbuminyRangeMax"] =
            extractRegex(Regex("Albuminy\\s+[g/l|g/dl]*\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Kreatynina
        extractedData["Kreatynina"] =
            extractRegex(Regex("Kreatynina\\s+µmol/l\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["KreatyninaUnit"] =
            extractRegex(Regex("Kreatynina\\s+(µmol/l)"), text) ?: "-"
        extractedData["KreatyninaRangeMin"] =
            extractRegex(Regex("Kreatynina\\s+µmol/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["KreatyninaRangeMax"] =
            extractRegex(Regex("Kreatynina\\s+µmol/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // T4 całkowita
        extractedData["T4calkowita"] =
            extractRegex(Regex("T4 całkowita\\s+µg/dl\\s+[\\d,.]+-[\\d,.]+\\s+Wynik\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["T4Unit"] =
            extractRegex(Regex("T4 całkowita\\s+(µg/dl)"), text) ?: "-"
        extractedData["T4RangeMin"] =
            extractRegex(Regex("T4 całkowita\\s+µg/dl\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["T4RangeMax"] =
            extractRegex(Regex("T4 całkowita\\s+µg/dl\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Mocznik
        extractedData["Mocznik"] =
            extractRegex(Regex("Mocznik\\s+mmol/l\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["MocznikUnit"] =
            extractRegex(Regex("Mocznik\\s+(mmol/l)"), text) ?: "-"
        extractedData["MocznikRangeMin"] =
            extractRegex(Regex("Mocznik\\s+mmol/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["MocznikRangeMax"] =
            extractRegex(Regex("Mocznik\\s+mmol/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // ALT
        extractedData["ALT"] =
            extractRegex(Regex("ALT\\s+U/l.*?\\)\\s+[\\d,.]+-[\\d,.]+.*?\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["ALTUnit"] =
            extractRegex(Regex("ALT\\s+(U/l)"), text) ?: "-"
        extractedData["ALTRangeMin"] =
            extractRegex(Regex("ALT\\s+U/l.*?\\s([\\d,.]+)-"), text) ?: "-"
        extractedData["ALTRangeMax"] =
            extractRegex(Regex("ALT\\s+U/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // AP
        extractedData["AP"] =
            extractRegex(Regex("AP\\s+U/l.*?\\s[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["APUnit"] =
            extractRegex(Regex("AP\\s+(U/l)"), text) ?: "-"
        extractedData["APRangeMin"] =
            extractRegex(Regex("AP\\s+U/l.*?\\s([\\d,.]+)-"), text) ?: "-"
        extractedData["APRangeMax"] =
            extractRegex(Regex("AP\\s+U/l.*?-[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // AST
        extractedData["AST"] =
            extractRegex(Regex("AST\\s+U/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["ASTUnit"] =
            extractRegex(Regex("AST\\s+(U/l)"), text) ?: "-"
        extractedData["ASTRangeMin"] =
            extractRegex(Regex("AST\\s+U/l.*?([\\d,.]+)-"), text) ?: "-"
        extractedData["ASTRangeMax"] =
            extractRegex(Regex("AST\\s+U/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Białko całkowite
        extractedData["BialkoCalkowite"] =
            extractRegex(Regex("Białko całkowite\\s+g/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["BialkoCalkowiteUnit"] =
            extractRegex(Regex("Białko całkowite\\s+(g/l)"), text) ?: "-"
        extractedData["BialkoCalkowiteRangeMin"] =
            extractRegex(Regex("Białko całkowite\\s+g/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["BialkoCalkowiteRangeMax"] =
            extractRegex(Regex("Białko całkowite\\s+g/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Bilirubina całkowita
        extractedData["BilirubinaCalkowita"] =
            extractRegex(Regex("Bilirubina całkowita\\s+µmol/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["BilirubinaUnit"] =
            extractRegex(Regex("Bilirubina całkowita\\s+(µmol/l)"), text) ?: "-"
        extractedData["BilirubinaRangeMin"] =
            extractRegex(Regex("Bilirubina całkowita\\s+µmol/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["BilirubinaRangeMax"] =
            extractRegex(Regex("Bilirubina całkowita\\s+µmol/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Cholesterol
        extractedData["Cholesterol"] =
            extractRegex(Regex("Cholesterol\\s+mmol/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["CholesterolUnit"] =
            extractRegex(Regex("Cholesterol\\s+(mmol/l)"), text) ?: "-"
        extractedData["CholesterolRangeMin"] =
            extractRegex(Regex("Cholesterol\\s+mmol/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["CholesterolRangeMax"] =
            extractRegex(Regex("Cholesterol\\s+mmol/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // CK
        extractedData["CK"] =
            extractRegex(Regex("CK\\s+U/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["CKUnit"] =
            extractRegex(Regex("CK\\s+(U/l)"), text) ?: "-"
        extractedData["CKRangeMin"] =
            extractRegex(Regex("CK\\s+U/l.*?([\\d,.]+)-"), text) ?: "-"
        extractedData["CKRangeMax"] =
            extractRegex(Regex("CK\\s+U/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Glukoza
        extractedData["Glukoza"] =
            extractRegex(Regex("Glukoza\\s+mmol/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["GlukozaUnit"] =
            extractRegex(Regex("Glukoza\\s+(mmol/l)"), text) ?: "-"
        extractedData["GlukozaRangeMin"] =
            extractRegex(Regex("Glukoza\\s+mmol/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["GlukozaRangeMax"] =
            extractRegex(Regex("Glukoza\\s+mmol/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // γ-GT
        extractedData["GammaGT"] =
            extractRegex(Regex("γ-GT\\s+U/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["GammaGTUnit"] =
            extractRegex(Regex("γ-GT\\s+(U/l)"), text) ?: "-"
        extractedData["GammaGTRangeMin"] =
            extractRegex(Regex("γ-GT\\s+U/l.*?([\\d,.]+)-"), text) ?: "-"
        extractedData["GammaGTRangeMax"] =
            extractRegex(Regex("γ-GT\\s+U/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // LDH
        extractedData["LDH"] =
            extractRegex(Regex("LDH\\s+U/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["LDHUnit"] =
            extractRegex(Regex("LDH\\s+(U/l)"), text) ?: "-"
        extractedData["LDHRangeMin"] =
            extractRegex(Regex("LDH\\s+U/l.*?([\\d,.]+)-"), text) ?: "-"
        extractedData["LDHRangeMax"] =
            extractRegex(Regex("LDH\\s+U/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Potas (K+)
        extractedData["Potas"] =
            extractRegex(Regex("Potas\\s+mmol/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["PotasUnit"] =
            extractRegex(Regex("Potas\\s+(mmol/l)"), text) ?: "-"
        extractedData["PotasRangeMin"] =
            extractRegex(Regex("Potas\\s+mmol/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["PotasRangeMax"] =
            extractRegex(Regex("Potas\\s+mmol/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Sód
        extractedData["Sod"] =
            extractRegex(Regex("Sód\\s+mmol/l\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["SodUnit"] =
            extractRegex(Regex("Sód\\s+(mmol/l)"), text) ?: "-"
        extractedData["SodRangeMin"] =
            extractRegex(Regex("Sód\\s+mmol/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["SodRangeMax"] =
            extractRegex(Regex("Sód\\s+mmol/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Wapń
        extractedData["Wapn"] =
            extractRegex(Regex("Wapń\\s+mmol/l\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["WapnUnit"] =
            extractRegex(Regex("Wapń\\s+(mmol/l)"), text) ?: "-"
        extractedData["WapnRangeMin"] =
            extractRegex(Regex("Wapń\\s+mmol/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["WapnRangeMax"] =
            extractRegex(Regex("Wapń\\s+mmol/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Fosfor nieorganiczny
        extractedData["Fosfor"] =
            extractRegex(Regex("Fosfor nieorganiczny\\s+mmol/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["FosforUnit"] =
            extractRegex(Regex("Fosfor nieorganiczny\\s+(mmol/l)"), text) ?: "-"
        extractedData["FosforRangeMin"] =
            extractRegex(Regex("Fosfor nieorganiczny\\s+mmol/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["FosforRangeMax"] =
            extractRegex(Regex("Fosfor nieorganiczny\\s+mmol/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Magnez
        extractedData["Magnez"] =
            extractRegex(Regex("Magnez\\s+mmol/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["MagnezUnit"] =
            extractRegex(Regex("Magnez\\s+(mmol/l)"), text) ?: "-"
        extractedData["MagnezRangeMin"] =
            extractRegex(Regex("Magnez\\s+mmol/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["MagnezRangeMax"] =
            extractRegex(Regex("Magnez\\s+mmol/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Globuliny
        extractedData["Globuliny"] =
            extractRegex(Regex("Globuliny\\s+g/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["GlobulinyUnit"] =
            extractRegex(Regex("Globuliny\\s+(g/l|g/dl)"), text) ?: "-"
        extractedData["GlobulinyRangeMin"] =
            extractRegex(Regex("Globuliny\\s+g/l.*?([\\d,.]+)-"), text) ?: "-"
        extractedData["GlobulinyRangeMax"] =
            extractRegex(Regex("Globuliny\\s+g/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Trójglicerydy
        extractedData["Trojglicerydy"] =
            extractRegex(Regex("Trójglicerydy\\s+mmol/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["TrojglicerydyUnit"] =
            extractRegex(Regex("Trójglicerydy\\s+(mmol/l)"), text) ?: "-"
        extractedData["TrojglicerydyRangeMin"] =
            extractRegex(Regex("Trójglicerydy\\s+mmol/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["TrojglicerydyRangeMax"] =
            extractRegex(Regex("Trójglicerydy\\s+mmol/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Chlorki
        extractedData["Chlorki"] =
            extractRegex(Regex("Chlorki\\s+mmol/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["ChlorkiUnit"] =
            extractRegex(Regex("Chlorki\\s+(mmol/l)"), text) ?: "-"
        extractedData["ChlorkiRangeMin"] =
            extractRegex(Regex("Chlorki\\s+mmol/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["ChlorkiRangeMax"] =
            extractRegex(Regex("Chlorki\\s+mmol/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Lipaza (DGGR)
        extractedData["LipazaDGGR"] =
            extractRegex(Regex("Lipaza \\(DGGR\\)\\s+U/l\\s+[\\d,.]+-[\\d,.]+\\s+Wynik\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["LipazaDGGRUnit"] =
            extractRegex(Regex("Lipaza \\(DGGR\\)\\s+(U/l)"), text) ?: "-"
        extractedData["LipazaDGGRRangeMin"] =
            extractRegex(Regex("Lipaza \\(DGGR\\)\\s+U/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["LipazaDGGRRangeMax"] =
            extractRegex(Regex("Lipaza \\(DGGR\\)\\s+U/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Fruktozamina
        extractedData["Fruktozamina"] =
            extractRegex(Regex("Fruktozamina\\s+µmol/l\\s+[\\d,.]+-[\\d,.]+\\s+Wynik\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["FruktozaminaUnit"] =
            extractRegex(Regex("Fruktozamina\\s+(µmol/l)"), text) ?: "-"
        extractedData["FruktozaminaRangeMin"] =
            extractRegex(Regex("Fruktozamina\\s+µmol/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["FruktozaminaRangeMax"] =
            extractRegex(Regex("Fruktozamina\\s+µmol/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // TLI
        extractedData["TLI"] =
            extractRegex(Regex("TLI \\(kot\\)\\s+µg/l\\s+[\\d,.]+-[\\d,.]+\\s+Wynik\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["TLIUnit"] =
            extractRegex(Regex("TLI \\(kot\\)\\s+(µg/l)"), text) ?: "-"
        extractedData["TLIRangeMin"] =
            extractRegex(Regex("TLI \\(kot\\)\\s+µg/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["TLIRangeMax"] =
            extractRegex(Regex("TLI \\(kot\\)\\s+µg/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Kwas foliowy
        extractedData["KwasFoliowy"] =
            extractRegex(Regex("Kwas foliowy\\s+ng/ml\\s+[\\d,.]+-[\\d,.]+\\s+Wynik\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["KwasFoliowyUnit"] =
            extractRegex(Regex("Kwas foliowy\\s+(ng/ml)"), text) ?: "-"
        extractedData["KwasFoliowyRangeMin"] =
            extractRegex(Regex("Kwas foliowy\\s+ng/ml\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["KwasFoliowyRangeMax"] =
            extractRegex(Regex("Kwas foliowy\\s+ng/ml\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Witamina B12
        extractedData["WitaminaB12"] =
            extractRegex(Regex("Witamina B12\\s+Wynik pg/ml\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["WitaminaB12Unit"] =
            extractRegex(Regex("Witamina B12\\s+Wynik\\s+(pg/ml)"), text) ?: "-"
        extractedData["WitaminaB12RangeMin"] =
            extractRegex(Regex("Witamina B12\\s+Wynik pg/ml\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["WitaminaB12RangeMax"] =
            extractRegex(Regex("Witamina B12\\s+Wynik pg/ml\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // T4 wolna
        extractedData["T4Wolna"] =
            extractRegex(Regex("T4 wolna\\s+ng/dl\\s+[\\d,.]+-[\\d,.]+\\s+Wynik\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["T4WolnaUnit"] =
            extractRegex(Regex("T4 wolna\\s+(ng/dl)"), text) ?: "-"
        extractedData["T4WolnaRangeMin"] =
            extractRegex(Regex("T4 wolna\\s+ng/dl\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["T4WolnaRangeMax"] =
            extractRegex(Regex("T4 wolna\\s+ng/dl\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Alfa-globuliny
        extractedData["AlfaGlobuliny"] =
            extractRegex(Regex("alfa-globuliny g/l\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["AlfaGlobulinyUnit"] =
            extractRegex(Regex("alfa-globuliny\\s+(g/l)"), text) ?: "-"
        extractedData["AlfaGlobulinyRangeMin"] =
            extractRegex(Regex("alfa-globuliny g/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["AlfaGlobulinyRangeMax"] =
            extractRegex(Regex("alfa-globuliny g/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Beta-globuliny
        extractedData["BetaGlobuliny"] =
            extractRegex(Regex("beta-globuliny g/l\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["BetaGlobulinyUnit"] =
            extractRegex(Regex("beta-globuliny\\s+(g/l)"), text) ?: "-"
        extractedData["BetaGlobulinyRangeMin"] =
            extractRegex(Regex("beta-globuliny g/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["BetaGlobulinyRangeMax"] =
            extractRegex(Regex("beta-globuliny g/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Gamma-globuliny
        extractedData["GammaGlobuliny"] =
            extractRegex(Regex("gamma-globuliny g/l\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["GammaGlobulinyUnit"] =
            extractRegex(Regex("gamma-globuliny\\s+(g/l|g/dl)"), text) ?: "g/l"
        extractedData["GammaGlobulinyRangeMin"] =
            extractRegex(Regex("gamma-globuliny g/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["GammaGlobulinyRangeMax"] =
            extractRegex(Regex("gamma-globuliny g/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Albuminy/globuliny (stosunek)
        extractedData["AlbuminyGlobuliny"] =
            extractRegex(Regex("Stosunek: albuminy / globuliny\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["AlbuminyGlobulinyUnit"] = "-"
        extractedData["AlbuminyGlobulinyRangeMin"] =
            extractRegex(Regex("Stosunek: albuminy / globuliny\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["AlbuminyGlobulinyRangeMax"] =
            extractRegex(Regex("Stosunek: albuminy / globuliny\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // SAA (surowiczy amyloid A)
        extractedData["SAA"] =
            extractRegex(Regex("SAA \\(surowiczy amyloid A\\).*?\\s+mg/l\\s+[\\d,.]+-[\\d,.]+\\s+(<*[\\d,.]+)"), text) ?: "-"
        extractedData["SAAUnit"] =
            extractRegex(Regex("SAA \\(surowiczy amyloid A\\).*?\\s+(mg/l)"), text) ?: "-"
        extractedData["SAARangeMin"] =
            extractRegex(Regex("SAA \\(surowiczy amyloid A\\).*?mg/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["SAARangeMax"] =
            extractRegex(Regex("SAA \\(surowiczy amyloid A\\).*?mg/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"
        // GLDH
        extractedData["GLDH"] =
            extractRegex(Regex("GLDH\\s+U/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["GLDHUnit"] =
            extractRegex(Regex("GLDH\\s+(U/l)"), text) ?: "-"
        extractedData["GLDHRangeMin"] =
            extractRegex(Regex("GLDH\\s+U/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["GLDHRangeMax"] =
            extractRegex(Regex("GLDH\\s+U/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // Albuminy/globuliny (stosunek) – elektroforeza
        extractedData["AlbuminyGlobulinyElektroforeza"] =
            extractRegex(Regex("albuminy/globuliny\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["AlbuminyGlobulinyElektroforezaUnit"] = "-"
        extractedData["AlbuminyGlobulinyElektroforezaRangeMin"] =
            extractRegex(Regex("albuminy/globuliny\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["AlbuminyGlobulinyElektroforezaRangeMax"] =
            extractRegex(Regex("albuminy/globuliny\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // α-amylaza
        extractedData["AlfaAmylaza"] =
            extractRegex(Regex("α-amylaza\\s+U/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["AlfaAmylazaUnit"] =
            extractRegex(Regex("α-amylaza\\s+(U/l)"), text) ?: "-"
        extractedData["AlfaAmylazaRangeMin"] =
            extractRegex(Regex("α-amylaza\\s+U/l.*?([\\d,.]+)-"), text) ?: "-"
        extractedData["AlfaAmylazaRangeMax"] =
            extractRegex(Regex("α-amylaza\\s+U/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // HCT
        extractedData["HCT"] =
            extractRegex(Regex("HCT\\s+l/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["HCTUnit"] = "l/l"
        extractedData["HCTRangeMin"] =
            extractRegex(Regex("HCT\\s+l/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["HCTRangeMax"] =
            extractRegex(Regex("HCT\\s+l/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // MCV
        extractedData["MCV"] =
            extractRegex(Regex("MCV\\s+fl\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["MCVUnit"] = "fl"
        extractedData["MCVRangeMin"] =
            extractRegex(Regex("MCV\\s+fl\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["MCVRangeMax"] =
            extractRegex(Regex("MCV\\s+fl\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // MCH
        extractedData["MCH"] =
            extractRegex(Regex("MCH\\s+pg\\s+[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["MCHUnit"] = "pg"
        extractedData["MCHRangeMin"] =
            extractRegex(Regex("MCH\\s+pg\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["MCHRangeMax"] =
            extractRegex(Regex("MCH\\s+pg\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // MCHC
        extractedData["MCHC"] =
            extractRegex(Regex("MCHC\\s+g/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["MCHCUnit"] = "g/l"
        extractedData["MCHCRangeMin"] =
            extractRegex(Regex("MCHC\\s+g/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["MCHCRangeMax"] =
            extractRegex(Regex("MCHC\\s+g/l.*?[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        // PLT
        extractedData["PLT"] =
            extractRegex(Regex("PLT\\s+G/l.*?[\\d,.]+-[\\d,.]+\\s+(\\d+,\\d+)"), text) ?: "-"
        extractedData["PLTUnit"] = "G/l"
        extractedData["PLTRangeMin"] =
            extractRegex(Regex("PLT\\s+G/l\\s+([\\d,.]+)-"), text) ?: "-"
        extractedData["PLTRangeMax"] =
            extractRegex(Regex("PLT\\s+G/l\\s+[\\d,.]+-([\\d,.]+)"), text) ?: "-"

        return extractedData
    }

    private fun extractRegex(regex: Regex, text: String): String? =
        regex.find(text)?.groupValues?.get(1)?.trim()

}
