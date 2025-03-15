package com.example.fipscan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.fipscan.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.os.Build
import android.view.WindowInsets
import android.view.ViewGroup.MarginLayoutParams

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        adjustToolbarPadding()

        val navView: BottomNavigationView = binding.navView
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
        val navController = navHostFragment?.navController

        if (navController != null) {
            val appBarConfiguration = AppBarConfiguration(
                setOf(R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            navView.setupWithNavController(navController)
        } else {
            throw IllegalStateException("NavController nie został poprawnie znaleziony.")
        }
    }

    private fun adjustToolbarPadding() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.setOnApplyWindowInsetsListener { view, windowInsets ->
                val statusBarHeight = windowInsets.getInsets(WindowInsets.Type.statusBars()).top
                val toolbarLayoutParams = binding.toolbar.layoutParams as MarginLayoutParams
                toolbarLayoutParams.topMargin = statusBarHeight
                binding.toolbar.layoutParams = toolbarLayoutParams
                view.onApplyWindowInsets(windowInsets)
            }
        } else {
            val statusBarHeight = resources.getDimensionPixelSize(
                resources.getIdentifier("status_bar_height", "dimen", "android")
            )
            val toolbarLayoutParams = binding.toolbar.layoutParams as MarginLayoutParams
            toolbarLayoutParams.topMargin = statusBarHeight
            binding.toolbar.layoutParams = toolbarLayoutParams
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
        val navController = navHostFragment?.navController
        return navController?.navigateUp() ?: false || super.onSupportNavigateUp()
    }
}
