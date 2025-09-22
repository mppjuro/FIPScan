package com.example.fipscan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.fipscan.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val sharedViewModel: SharedResultViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Odczytaj preferencje trybu ciemnego przed wywołaniem super.onCreate()
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val isDarkModeEnabled = sharedPreferences.getBoolean("dark_mode", false)

        if (isDarkModeEnabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)

        // Inicjalizuj binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Wyczyść nieznanych pacjentów z bazy danych
        val db = AppDatabase.getDatabase(applicationContext)
        lifecycleScope.launch(Dispatchers.IO) {
            db.resultDao().deleteUnknownPatients()
        }

        // Konfiguruj nawigację
        setupNavigation()
    }

    private fun setupNavigation() {
        val navView: BottomNavigationView = binding.navView
        val navHostFragment = supportFragmentManager
            .findFragmentById(com.example.fipscan.R.id.nav_host_fragment_activity_main) as? NavHostFragment

        val navController = navHostFragment?.navController

        if (navController != null) {
            navView.setupWithNavController(navController)
        } else {
            throw IllegalStateException("NavController nie został poprawnie znaleziony.")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(com.example.fipscan.R.id.nav_host_fragment_activity_main) as? NavHostFragment
        val navController = navHostFragment?.navController
        return navController?.navigateUp() ?: false || super.onSupportNavigateUp()
    }
}