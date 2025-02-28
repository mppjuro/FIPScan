package com.example.fipscan.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.fipscan.databinding.FragmentHomeBinding
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import java.lang.StringBuilder

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var pdfUri: Uri? = null
    private lateinit var extractedData: Map<String, String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        val btnLoadPdf: Button = binding.buttonLoadPdf
        btnLoadPdf.setOnClickListener { openFilePicker() }

        return binding.root
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        filePicker.launch(intent)
    }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                pdfUri = result.data?.data
                extractTextFromPDF()
            }
        }

    private fun extractTextFromPDF() {
        pdfUri?.let { uri ->
            try {
                // Otwieramy strumień z pliku PDF
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                inputStream?.let { stream ->
                    val pdfReader = PdfReader(stream)
                    val numPages = pdfReader.numberOfPages
                    val extractedText = StringBuilder()

                    // iTextPDF numeruje strony od 1
                    for (i in 1..numPages) {
                        val pageText = PdfTextExtractor.getTextFromPage(pdfReader, i)
                        extractedText.append(pageText).append("\n")
                    }
                    pdfReader.close()
                    stream.close()

                    // Parsowanie tekstu i aktualizacja UI
                    extractedData = parseLabResults(extractedText.toString())
                    updateUI()
                } ?: run {
                    binding.textHome.text = "Nie udało się otworzyć pliku PDF!"
                }
            } catch (e: Exception) {
                binding.textHome.text = "Błąd przetwarzania PDF!"
                Log.e("PDF_ERROR", "Błąd", e)
            }
        }
    }

    private fun parseLabResults(text: String): Map<String, String> {
        val data = mutableMapOf<String, String>()
        Log.d("PDF_DEBUG", "Pełna treść PDF:\n$text")

        // Parsowanie danych pacjenta (przetwarzamy cały tekst, bo te dane są na początku)
        val regexPacjent = Regex(
            "Pacjent:\\s+([\\p{L}]+)\\s+" +
                    "Gatunek:\\s+([\\p{L}]+)\\s+" +
                    "Rasa:\\s+([\\p{L}]+)\\s+" +
                    "Płeć:\\s+([\\p{L}]+)\\s+" +
                    "Wiek:\\s+([\\d]+\\s+[\\p{L}]+)"
        )
        regexPacjent.find(text)?.let {
            data["Pacjent"] = it.groupValues[1].trim()
            data["Gatunek"] = it.groupValues[2].trim()
            data["Rasa"] = it.groupValues[3].trim()
            data["Płeć"] = it.groupValues[4].trim()
            data["Wiek"] = it.groupValues[5].trim()
        }

// Parsowanie mikrochipa i umaszczenia – umaszczenie to jedno lub dwa słowa, bez dodatkowych fragmentów (np. "Lecznica")
        val regexMikrochip = Regex(
            "Mikrochip:\\s+(\\d+)\\s+" +
                    "Umaszczenie:\\s+([\\p{L}]+(?:\\s+[\\p{L}]+)?)(?=\\s|$)"
        )
        regexMikrochip.find(text)?.let {
            data["Mikrochip"] = it.groupValues[1].trim()
            data["Umaszczenie"] = it.groupValues[2].trim()
        }

        // Odczytujemy sekcję badań dopiero od wystąpienia słowa "Morfologia"
        val labSection = if (text.contains("Morfologia")) {
            text.substringAfter("Morfologia").trim()
        } else {
            ""
        }
        Log.d("PDF_DEBUG", "Lab Section:\n$labSection")

        // Regex dla WBC: wyszukujemy wartość i zakres normy
        val regexWBC = Regex("WBC\\s+([\\d.,]+)\\s+G/l\\s+([\\d.,]+-[\\d.,]+)")
        regexWBC.find(labSection)?.let {
            val value = it.groupValues[1].trim()
            val norm = it.groupValues[2].trim()
            data["WBC"] = "$value (norma: $norm)"
        } ?: Log.d("PDF_DEBUG", "WBC nie został znaleziony!")

        // Regexy dla wyników badań – pobierają wynik (grupa 1) oraz normę (grupa 2)
        val regexBadania = mapOf(
            "WBC" to Regex("WBC\\s+([\\d.,]+)\\s+G/l\\s+([\\d.,]+-[\\d.,]+)"),
            "NEU" to Regex("NEU\\s+([\\d.,]+)\\s+G/l\\s+([\\d.,]+-[\\d.,]+)"),
            "NEU_pct" to Regex("NEU\\s+%\\s+([\\d.,]+)\\s+%\\s+([\\d.,]+-[\\d.,]+)"),
            "LYM" to Regex("LYM\\s+([\\d.,]+)\\s+G/l\\s+([\\d.,]+-[\\d.,]+)"),
            "LYM_pct" to Regex("LYM\\s+%\\s+([\\d.,]+)\\s+%\\s+([\\d.,]+-[\\d.,]+)"),
            "MONO" to Regex("MONO\\s+([\\d.,]+)\\s+G/l\\s+([\\d.,]+-[\\d.,]+)"),
            "MONO_pct" to Regex("MONO\\s+%\\s+([\\d.,]+)\\s+%\\s+([\\d.,]+-[\\d.,]+)"),
            "EOS" to Regex("EOS\\s+([\\d.,]+)\\s+G/l\\s+([\\d.,]+-[\\d.,]+)"),
            "EOS_pct" to Regex("EOS\\s+%\\s+([\\d.,]+)\\s+%\\s+([\\d.,]+-[\\d.,]+)"),
            "BASO" to Regex("BASO\\s+([\\d.,]+)\\s+G/l\\s+([\\d.,]+-[\\d.,]+)"),
            "BASO_pct" to Regex("BASO\\s+%\\s+([\\d.,]+)\\s+%\\s+([\\d.,]+-[\\d.,]+)"),
            "RBC" to Regex("RBC\\s+([\\d.,]+)\\s+T/l\\s+([\\d.,]+-[\\d.,]+)"),
            "HGB" to Regex("HGB\\s+([\\d.,]+)\\s+g/l\\s+([\\d.,]+-[\\d.,]+)"),
            "HCT" to Regex("HCT\\s+([\\d.,]+)\\s+l/l\\s+([\\d.,]+-[\\d.,]+)"),
            "MCV" to Regex("MCV\\s+([\\d.,]+)\\s+fl\\s+([\\d.,]+-[\\d.,]+)"),
            "MCH" to Regex("MCH\\s+([\\d.,]+)\\s+pg\\s+([\\d.,]+-[\\d.,]+)"),
            "MCHC" to Regex("MCHC\\s+([\\d.,]+)\\s+g/l\\s+([\\d.,]+-[\\d.,]+)"),
            "PLT" to Regex("PLT\\s+([\\d.,]+)\\s+G/l\\s+([\\d.,]+-[\\d.,]+)")
        )

        // Dla każdego testu, jeśli dopasowanie zostanie znalezione w labSection, zapisujemy wynik
        for ((key, regex) in regexBadania) {
            regex.find(labSection)?.let {
                val result = it.groupValues[1].trim()
                val norm = it.groupValues[2].trim()
                data[key] = "$result (norma: $norm)"
            }
        }

        return data
    }


    private fun updateUI() {
        // Łączenie sparsowanych danych w jeden string do wyświetlenia
        val displayText = extractedData.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        binding.textHome.text = displayText
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
