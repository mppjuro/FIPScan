package com.example.fipscan.ui.settings

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import com.example.fipscan.R
import com.example.fipscan.databinding.FragmentSettingsBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class SettingsFragment : Fragment() {
    companion object {
        private const val AUTHOR_NAME = "Miłosz Piórkowski"
        private const val AUTHOR_EMAIL = "mppjuro@gmail.com"
        private const val AUTHOR_PHONE = "+48-724-928-250"
        private const val GITHUB_PROJECT_URL = "https://github.com/mppjuro/FIPScan"
        private const val GITHUB_RELEASES_URL = "https://github.com/mppjuro/FIPScan/releases"
        private const val FACEBOOK_PROFILE_URL = "https://www.facebook.com/profile.php?id=100004175231300"
        private const val EASTER_EGG_CLICKS = 5
        private const val PREF_APP_LANGUAGE = "app_language"
        private const val PREF_DARK_MODE = "dark_mode"
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedPreferences: SharedPreferences
    private var clickCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        sharedPreferences = requireContext().getSharedPreferences("AppPreferences", 0)

        setupDarkModeSwitch()
        setupLanguageSelection()
        setupEasterEgg()

        return binding.root
    }

    private fun setupDarkModeSwitch() {
        binding.switchDarkMode.isChecked = sharedPreferences.getBoolean(PREF_DARK_MODE, false)
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(PREF_DARK_MODE, isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }

    private fun setupLanguageSelection() {
        val currentLanguage = sharedPreferences.getString(PREF_APP_LANGUAGE, "pl")

        when (currentLanguage) {
            "pl" -> binding.radioPolish.isChecked = true
            "en" -> binding.radioEnglish.isChecked = true
            "de" -> binding.radioGerman.isChecked = true
            "fr" -> binding.radioFrench.isChecked = true
            else -> binding.radioPolish.isChecked = true
        }

        binding.languageRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedLanguage = when (checkedId) {
                R.id.radioPolish -> "pl"
                R.id.radioEnglish -> "en"
                R.id.radioGerman -> "de"
                R.id.radioFrench -> "fr"
                else -> "pl"
            }

            if (currentLanguage != selectedLanguage) {
                setAppLocale(selectedLanguage)
            }
        }
    }

    private fun setAppLocale(languageCode: String) {
        sharedPreferences.edit().putString(PREF_APP_LANGUAGE, languageCode).apply()
        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    private fun setupEasterEgg() {
        binding.textHistory.setOnClickListener {
            clickCount++
            if (clickCount >= EASTER_EGG_CLICKS) {
                clickCount = 0
                showEasterEggDialog()
            }
        }
    }

    private fun showEasterEggDialog() {
        val dialogView = layoutInflater.inflate(
            R.layout.dialog_easter_egg,
            null
        )

        val textAuthor = dialogView.findViewById<TextView>(R.id.textAuthorInfo)
        val imageQR = dialogView.findViewById<ImageView>(R.id.imageQrCode)

        val fullText = """
            Autor: $AUTHOR_NAME
            
            Kontakt: $AUTHOR_EMAIL
            
            Tel.: $AUTHOR_PHONE
            
            Github projektu: $GITHUB_PROJECT_URL
        """.trimIndent()

        val spannableString = SpannableString(fullText)

        val nameStart = fullText.indexOf(AUTHOR_NAME)
        val nameEnd = nameStart + AUTHOR_NAME.length
        spannableString.setSpan(object : ClickableSpan() { override fun onClick(widget: View) { openUrl(FACEBOOK_PROFILE_URL) } }, nameStart, nameEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val emailStart = fullText.indexOf(AUTHOR_EMAIL)
        val emailEnd = emailStart + AUTHOR_EMAIL.length
        spannableString.setSpan(object : ClickableSpan() { override fun onClick(widget: View) { openEmail(AUTHOR_EMAIL) } }, emailStart, emailEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val phoneStart = fullText.indexOf(AUTHOR_PHONE)
        val phoneEnd = phoneStart + AUTHOR_PHONE.length
        spannableString.setSpan(object : ClickableSpan() { override fun onClick(widget: View) { openPhone(AUTHOR_PHONE) } }, phoneStart, phoneEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val githubStart = fullText.indexOf(GITHUB_PROJECT_URL)
        val githubEnd = githubStart + GITHUB_PROJECT_URL.length
        spannableString.setSpan(object : ClickableSpan() { override fun onClick(widget: View) { openUrl(GITHUB_PROJECT_URL) } }, githubStart, githubEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        textAuthor.text = spannableString
        textAuthor.movementMethod = LinkMovementMethod.getInstance()

        val qrBitmap = generateQRCode(GITHUB_RELEASES_URL)
        imageQR.setImageBitmap(qrBitmap)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.o_aplikacji))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openEmail(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openPhone(phone: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.replace("-", "")}"))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateQRCode(text: String, size: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)

        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}