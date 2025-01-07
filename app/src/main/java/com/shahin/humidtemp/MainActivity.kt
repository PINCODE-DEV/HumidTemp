package com.shahin.humidtemp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.shahin.humidtemp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    // Binding
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null

    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnConnect.shrink()

        lifecycleScope.launch(Dispatchers.Default) {
            delay(2500)
            withContext(Dispatchers.Main) {
                binding.btnConnect.extend()
            }
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            showMessage("بلوتوث در دستگاه شما پشتیبانی نمی‌شود.")
            return
        }

        enableBluetooth()

        binding.btnConnect.setOnClickListener {
            if (!isConnected)
                connectToDevice()
            else
                disconnectDevice()
        }
    }

    private fun enableBluetooth() {
        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothLauncher.launch(enableBtIntent)
        }
    }

    private val bluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bluetoothAdapter!!.isEnabled)
                showMessage("بلوتوث فعال شد.")
            else
                showMessage("بلوتوث فعال نشد.")
        }

    private fun connectToDevice() {
        binding.txtLoading.apply {
            text = getString(R.string.connectingToDevice)
            isVisible = true
        }

        val device: BluetoothDevice = bluetoothAdapter!!.getRemoteDevice(DEVICE_ADDRESS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQUEST_BLUETOOTH_PERMISSION
                )
                return
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothSocket?.connect()

                withContext(Dispatchers.Main) {
                    showMessage("اتصال برقرار شد.")
                    with(binding) {
                        btnConnect.text = getString(R.string.pressToDisconnect)
                        txtLoading.text = getString(R.string.connected)
                        lifecycleScope.launch(Dispatchers.Default) {
                            delay(2500)
                            withContext(Dispatchers.Main) {
                                txtLoading.isVisible = false
                            }
                        }
                    }
                }

                startListeningForData()
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showMessage("خطا در اتصال به دستگاه.")
                    deviceDisconnectedUpdateUI(true)
                }
            }
        }
    }


    private fun startListeningForData() {
        val inputStream: InputStream? = bluetoothSocket?.inputStream

        if (inputStream != null) {
            Thread {
                val buffer = ByteArray(1024)
                var bytes: Int
                val stringBuilder = StringBuilder()

                while (true) {
                    try {
                        bytes = inputStream.read(buffer)
                        val receivedChunk = String(buffer, 0, bytes)

                        stringBuilder.append(receivedChunk)

                        if (stringBuilder.contains("\n")) {
                            val fullMessage = stringBuilder.toString().trim()

                            stringBuilder.clear()

                            runOnUiThread {
                                parseJsonData(fullMessage)
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        runOnUiThread {
                            showMessage("ارتباط قطع شد.")
                            deviceDisconnectedUpdateUI(true)
                        }
                        break
                    }
                }
            }.start()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun parseJsonData(json: String) {
        try {
            val dataModel = Gson().fromJson(json, DataModel::class.java)
            with(binding) {
                txtTemp.text = "${dataModel.temperature}°"
                txtHumidity.text = "${dataModel.humidity}%"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showMessage("خطا در پردازش JSON.")
        }
    }

    private fun disconnectDevice() {
        try {
            bluetoothSocket?.close()
            bluetoothSocket = null
            showMessage("اتصال قطع شد.")
            deviceDisconnectedUpdateUI()
        } catch (e: IOException) {
            e.printStackTrace()
            showMessage("خطا در قطع اتصال.")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (!grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                showMessage("مجوز اتصال بلوتوث رد شد.")
        }
    }

    private fun deviceDisconnectedUpdateUI(isConnectionLost: Boolean = false) {
        with(binding) {
            txtTemp.text = getString(R.string.empty)
            txtHumidity.text = getString(R.string.empty)
            btnConnect.text = getString(R.string.pressToConnect)
            txtLoading.apply {
                text = if (isConnectionLost)
                    getString(R.string.connectionLost)
                else
                    getString(R.string.disconnected)
                isVisible = true
            }
            lifecycleScope.launch(Dispatchers.Default) {
                delay(2500)
                withContext(Dispatchers.Main) {
                    txtLoading.isVisible = false
                }
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}
