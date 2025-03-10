package com.example.fipscan.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.fipscan.databinding.FragmentHomeBinding
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var pdfUri: Uri? = null
    private val extractedData = mutableMapOf<String, Any>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.buttonLoadPdf.setOnClickListener { openFilePicker() }
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
                requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                    val pdfReader = PdfReader(stream)
                    val extractedText = StringBuilder()

                    for (i in 1..pdfReader.numberOfPages) {
                        extractedText.append(PdfTextExtractor.getTextFromPage(pdfReader, i)).append("\n")
                    }
                    pdfReader.close()

                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val filename = "pdf_${timestamp}.txt"
                    val savedFile = saveExtractedTextToFile(extractedText.toString(), filename)

                    Thread {
                        uploadFileToFTP(savedFile)
                    }.start()

                    parseLabResults(extractedText.toString())
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

    private fun extractRegex(regex: Regex, text: String): String? =
        regex.find(text)?.groupValues?.get(1)?.trim()

    private fun parseLabResults(text: String) {
        extractedData.clear()

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
            extractRegex(Regex("Mikrochip:\\s+(\\d+)"), text) ?: "-"

        /*
        val regexMikrochip = Regex(
            "Mikrochip:\\s+(\\d+)\\s+" +
                    "Umaszczenie:\\s+([\\p{L}]+(?:\\s+[\\p{L}]+)?)(?=\\s|$)"
        )
        regexMikrochip.find(text)?.let {
            extractedData["Mikrochip"] = it.groupValues[1].trim()
            extractedData["Umaszczenie"] = it.groupValues[2].trim()
        }
        */

        val testRegex = Regex("([\\w% /\\-α-γ]+)\\s+([\\d.,]+)\\s+(\\S+)\\s+([\\d.,]+)-([\\d.,]+)")
        testRegex.findAll(text).forEach { match ->
            val key = match.groupValues[1].replace("[ %/\\-()]".toRegex(), "_").replace("__+", "_").trim().lowercase(Locale.getDefault())
            extractedData["${key}_Value"] = match.groupValues[2].replace(",", ".").toDouble()
            extractedData["${key}_Unit"] = match.groupValues[3]
            extractedData["${key}_Min"] = match.groupValues[4].replace(",", ".").toDouble()
            extractedData["${key}_Max"] = match.groupValues[5].replace(",", ".").toDouble()
        }
    }

    private fun updateUI() {
        val info = buildString {
            append("Pacjent: ${extractedData["Pacjent"] ?: "-"}\n")
            append("Gatunek: ${extractedData["Gatunek"] ?: "-"}\n")
            append("Rasa: ${extractedData["Rasa"] ?: "-"}\n")
            append("Wiek: ${extractedData["Wiek"] ?: "-"}\n")
            append("Mikrochip: ${extractedData["Mikrochip"] ?: "-"}\n")
            append("Umaszczenie: ${extractedData["Umaszczenie"] ?: "-"}\n\n")

            /*
            val exampleTests = listOf(
                "wbc", "neu", "neu_pct", "lym", "lym_pct", "mono", "mono_pct",
                "eos", "eos_pct", "baso", "baso_pct", "rbc", "hgb", "hct",
                "mcv", "mch", "mchc", "plt", "albuminy", "globuliny", "albuminy_globuliny"
            )

            exampleTests.forEach { test ->
                val value = extractedData["${test}_Value"] ?: "-"
                val unit = extractedData["${test}_Unit"] ?: ""
                val min = extractedData["${test}_Min"] ?: "-"
                val max = extractedData["${test}_Max"] ?: "-"
                append("${test.uppercase()}: $value $unit (norma: $min - $max)\n")
            }
            */
        }
        binding.textHome.text = info
    }

    private fun saveExtractedTextToFile(content: String, filename: String): File {
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(storageDir, filename)
        file.writeText(content)
        return file
    }

    private fun uploadFileToFTP(file: File) {
        FTPClient().apply {
            try {
                connect("fippolska.pl", 21)
                enterLocalPassiveMode()
                login("admin@fippolska.pl", "1540033Mp!")
                setFileType(FTP.BINARY_FILE_TYPE)
                changeWorkingDirectory("/public_html/")
                changeWorkingDirectory("/pl/")
                file.inputStream().use { inputStream ->
                    storeFile(file.name, inputStream)
                }
                logout()
                disconnect()
                Log.d("FTP_UPLOAD", "Plik wysłany poprawnie.")
            } catch (ex: Exception) {
                Log.e("FTP_UPLOAD", "Błąd wysyłania pliku FTP", ex)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
