package com.dome.recordscreen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dome.recordscreen.ScreenRecordService.Companion.KEY_RECORDING_CONFIG
import com.dome.recordscreen.ScreenRecordService.Companion.SNAP
import com.dome.recordscreen.ScreenRecordService.Companion.START_RECORDING
import com.dome.recordscreen.ScreenRecordService.Companion.STOP_RECORDING


class MainActivity : ComponentActivity() {

    private lateinit var handler: Handler
    private lateinit var captureRunnable: Runnable



    private val mediaProjectionManager by lazy {
        getSystemService<MediaProjectionManager>()!!
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)




        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val isServiceRunning by ScreenRecordService
                    .isServiceRunning
                    .collectAsStateWithLifecycle()

                var snapCount by remember { mutableStateOf(0) }



                var hasNotificationPermission by remember {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                applicationContext,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    } else mutableStateOf(true)
                }
                val screenRecordLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val intent = result.data ?: return@rememberLauncherForActivityResult
                    val config = ScreenRecordConfig(
                        resultCode = result.resultCode,
                        data = intent
                    )

                    val serviceIntent = Intent(
                        applicationContext,
                        ScreenRecordService::class.java
                    ).apply {
                        action = START_RECORDING
                        putExtra(KEY_RECORDING_CONFIG, config)
                    }
                    startForegroundService(serviceIntent)
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasNotificationPermission = isGranted
                    if (hasNotificationPermission && !isServiceRunning) {
                        screenRecordLauncher.launch(
                            mediaProjectionManager.createScreenCaptureIntent()
                        )
                    }
                }


                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center

                    )
                    {

                        Button(
                            onClick = {
                                if (!hasNotificationPermission &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    if (isServiceRunning) {
                                        Intent(
                                            applicationContext,
                                            ScreenRecordService::class.java
                                        ).also {
                                            it.action = STOP_RECORDING
                                            startForegroundService(it)
                                        }
                                    } else {
                                        screenRecordLauncher.launch(
                                            mediaProjectionManager.createScreenCaptureIntent()
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServiceRunning) {
                                    Color.Red
                                } else Color.Green
                            )
                        ) {
                            Text(
                                text = if (isServiceRunning) {
                                    "Stop recording"
                                } else "Start recording",
                                color = Color.Black
                            )
                        }


                        if (isServiceRunning){
                        Button(onClick = {

                            handler = Handler()


                            // Set up the capture runnable loop here
                            // HACK: don't set this up in service, it will crash the app
                            captureRunnable = object : Runnable {
                                override fun run() {


                                    Intent(
                                        applicationContext,
                                        ScreenRecordService::class.java
                                    ).also {
                                        it.action = SNAP
                                        startForegroundService(it)
                                    }
                                    snapCount++


                                    handler.postDelayed(this, 1000)  // Capture once every 1 second (1 fps)
                                }
                            }

                            handler.post(captureRunnable)


                            }


                        ){Text(text="start take screenshot loop")}

                            Text(text = "Snap count: $snapCount", fontSize = 20.sp)
                    }








                    }






                }














            }
        }






    }




}



