package com.client

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface.OnClickListener
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.client.databinding.ActivityLockedWithPinBinding
import com.client.services.client.isLocked
import com.client.services.client.isLockedWithPin
import com.client.services.client.killAll

class LockedWithPinActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLockedWithPinBinding
    private lateinit var pin: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLockedWithPinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        pin = intent.getStringExtra("Pin").toString()
        binding.pinView.itemCount = pin.length
        val handler = Handler(Looper.getMainLooper())
        val preferences = getSharedPreferences("Preferences", Context.MODE_MULTI_PROCESS)
        handler.postDelayed(object : Runnable {
            override fun run() {
                isLockedWithPin = preferences.getBoolean("IsLockedWithPin", false)
                pin = preferences.getString("Pin", "000000").toString()
                if (binding.pinView.itemCount != pin.length) {
                    binding.pinView.itemCount = pin.length
                }
                killAll = preferences.getBoolean("killAll", false)
                if (killAll || !isLockedWithPin) {
                    Log.d("LockedWithPinActivity", "Killing activity")
                    finishAffinity()
                } else {
                    handler.postDelayed(this, 500)
                }
            }
        }, 500)
        binding.pinView.requestFocus()
        binding.pinView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s?.length == pin.length) {
                    if (s.toString() == pin) {
                        with (preferences.edit()) {
                            putBoolean("IsLockedWithPin", false)
                            putBoolean("IsLocked", false)
                            putBoolean("killAll", true)
                            commit()
                        }
                        isLockedWithPin = false
                        isLocked = false
                        killAll = true
                        finishAffinity()
                    } else {
                        binding.pinView.setText("")
                        AlertDialog.Builder(this@LockedWithPinActivity)
                            .setTitle("Incorrect PIN")
                            .setMessage("Please try again")
                            .setPositiveButton("OK") { dialog, which -> dialog?.dismiss() }
                            .create()
                            .show()
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })
    }
}