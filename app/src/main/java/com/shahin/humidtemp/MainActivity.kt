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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.shahin.humidtemp.databinding.ActivityMainBinding
import java.io.IOException
import java.io.InputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    // Binding
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val DEVICE_ADDRESS = "00:00:13:02:67:1F" // آدرس MAC دستگاه بلوتوث
    private val REQUEST_BLUETOOTH_PERMISSION = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            showToast("بلوتوث در دستگاه شما پشتیبانی نمی‌شود.")
            return
        }

        // فعال‌سازی بلوتوث
        enableBluetooth()

        // دکمه اتصال
        binding.btnConnect.setOnClickListener {
            connectToDevice()
        }

        // دکمه قطع اتصال
        binding.btnDisconnect.setOnClickListener {
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
            if (bluetoothAdapter!!.isEnabled) {
                showToast("بلوتوث فعال شد.")
            } else {
                showToast("بلوتوث فعال نشد.")
            }
        }

    private fun connectToDevice() {
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

        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
            bluetoothSocket?.connect()
            showToast("اتصال برقرار شد.")
            startListeningForData()
        } catch (e: IOException) {
            e.printStackTrace()
            showToast("خطا در اتصال به دستگاه.")
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

                        // افزودن بخش جدید به stringBuilder
                        stringBuilder.append(receivedChunk)

                        // اگر پیام کامل دریافت شد (مثلاً شامل '\n' باشد)
                        if (stringBuilder.contains("\n")) {
                            val fullMessage = stringBuilder.toString().trim()

                            // حذف داده‌های پردازش‌شده از stringBuilder
                            stringBuilder.clear()

                            // پردازش داده کامل JSON
                            runOnUiThread {
                                parseJsonData(fullMessage)
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        runOnUiThread {
                            showToast("ارتباط قطع شد.")
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
            binding.tvData.text =
                "Timestamp: ${dataModel.timestamp}\nTemperature: ${dataModel.temperature}\nHumidity: ${dataModel.humidity}"
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("خطا در پردازش JSON.")
        }
    }


    private fun disconnectDevice() {
        try {
            bluetoothSocket?.close()
            bluetoothSocket = null
            showToast("اتصال قطع شد.")
        } catch (e: IOException) {
            e.printStackTrace()
            showToast("خطا در قطع اتصال.")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToDevice()
            } else {
                showToast("مجوز اتصال بلوتوث رد شد.")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
