package com.example.grocery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.example.grocery.databinding.ActivityMainBinding
import com.example.grocery.ui.theme.GroceryTheme

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {
    private var isNotificationPermissionGranted = false
    private val homeViewModel by viewModels<HomeViewModel>()
    private lateinit var binding: ActivityMainBinding
    lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        val multiplePermissionsRequest =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                // Check if all permissions are granted
                isNotificationPermissionGranted =
                    permissions[if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.POST_NOTIFICATIONS
                    } else {
                        true
                    }] ?: isNotificationPermissionGranted

            }
        requestPermissions(multiplePermissionsRequest)
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setContentView(binding.root)


        val navView: BottomNavigationView = binding.navView

        navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.

        navView.setupWithNavController(navController)
        navController.addOnDestinationChangedListener(this)

    }

    override fun onResume() {
        super.onResume()

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                if (token != null) {
                    homeViewModel.updateFcmToken(token.toString())
                } else {
                    Log.e("MainActivity", "FCM token is null")
                    Toast.makeText(this, "FCM token is null", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("MainActivity", "Failed to get FCM token", task.exception)
                Toast.makeText(this, "Failed to get FCM token", Toast.LENGTH_SHORT).show()
                // Handle the exception appropriately
            }
        }
//        try {
//            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
//                // Get new FCM registration token
//                val token = task.result
//                homeViewModel.updateFcmToken(token.toString())
//            }
//        } catch (e: Exception) {
//
//        }
        homeViewModel.updateFcmTokenLiveData.observe(this) {
        }
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        when (destination.id) {
            R.id.navigation_home, R.id.navigation_inventory, R.id.navigation_shopping,
            R.id.navigation_recipe, R.id.navigation_analytics ->
                binding.navView.visible()

            else -> {
                binding.navView.remove()
            }
        }
    }

    private fun requestPermissions(multiplePermissionsRequest: ActivityResultLauncher<Array<String>>) {


        val notificationPermission = Manifest.permission.POST_NOTIFICATIONS
        val permissionsToRequest = mutableListOf<String>()

        isNotificationPermissionGranted = ContextCompat.checkSelfPermission(
            this,
            notificationPermission
        ) == PackageManager.PERMISSION_GRANTED

        if (!isNotificationPermissionGranted) {
            permissionsToRequest.add(notificationPermission)
        }

        if (permissionsToRequest.isNotEmpty()) {
            // Request the permissions
            multiplePermissionsRequest.launch(permissionsToRequest.toTypedArray())
        } else {


            // Permissions have already been granted
            // Perform the action that requires the permissions
            // ...
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        //handle new intent here
        try {
            if (intent?.hasExtra("notification") == true) {

                navController.navigate(R.id.notificationsFragment)
            }
        } catch (e: Exception) {
            e.stackTrace
        }
    }
}