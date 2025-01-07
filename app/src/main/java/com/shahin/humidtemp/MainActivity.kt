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
            showMessage("Bluetooth is not supported on your device.")
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
                showMessage("Bluetooth is activated.")
            else
                showMessage("Bluetooth was not activated.")
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
                    showMessage("Connection successfully!")
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
                    showMessage("Error connecting to the device.")
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
                            showMessage("The connection was lost.")
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
                txtTemp.text = formatTemperature(dataModel.temperature)
                txtHumidity.text = formatHumidity(dataModel.humidity)

                dataModel.temperature?.let {
                    txtTemp.setTextColor(getTempColor(it))
                }

                dataModel.humidity?.let {
                    txtHumidity.setTextColor(getHumidityColor(it))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showMessage("Error processing JSON.")
        }
    }

    // Helper function to format temperature text
    private fun formatTemperature(temperature: Double?): String =
        temperature?.let { "$it°" } ?: "N/A"

    // Helper function to format humidity text
    private fun formatHumidity(humidity: Double?): String =
        humidity?.let { "$it%" } ?: "N/A"

    // Helper function to determine temperature color
    private fun getTempColor(temperature: Double): Int =
        if (temperature > 30 || temperature < 10) getColor(R.color.red) else getColor(R.color.green)

    // Helper function to determine humidity color
    private fun getHumidityColor(humidity: Double): Int =
        if (humidity > 70 || humidity < 30) getColor(R.color.red) else getColor(R.color.green)


    private fun disconnectDevice() {
        try {
            bluetoothSocket?.close()
            bluetoothSocket = null
            showMessage("The connection was disconnected.")
            deviceDisconnectedUpdateUI()
        } catch (e: IOException) {
            e.printStackTrace()
            showMessage("Error disconnecting.")
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
                showMessage("Bluetooth connection permission denied.")
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
            if (txtTemp.currentTextColor != getColor(R.color.green))
                txtTemp.setTextColor(getColor(R.color.green))
            if (txtHumidity.currentTextColor != getColor(R.color.green))
                txtHumidity.setTextColor(getColor(R.color.green))
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}
