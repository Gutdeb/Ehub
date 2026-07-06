package com.emailhub.app

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val config by lazy { AppConfig.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val message = intent.getStringExtra(EXTRA_MESSAGE)
        if (!message.isNullOrBlank()) {
            findViewById<TextView>(R.id.textMessage).apply {
                text = message
                visibility = View.VISIBLE
            }
        }

        val etUrl = findViewById<EditText>(R.id.etServerUrl)
        val etUser = findViewById<EditText>(R.id.etUsername)
        val etPass = findViewById<EditText>(R.id.etPassword)
        val cbShowPass = findViewById<CheckBox>(R.id.cbShowPassword)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnClear = findViewById<Button>(R.id.btnClear)

        etUrl.setText(config.serverUrl)
        etUser.setText(config.username)
        etPass.setText(config.password)

        cbShowPass.setOnCheckedChangeListener { _, isChecked ->
            etPass.inputType = if (isChecked)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        btnSave.setOnClickListener {
            val url = etUrl.text.toString().trim().trimEnd('/')
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString()

            if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, R.string.msg_all_fields, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                Toast.makeText(this, R.string.msg_invalid_url, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            config.serverUrl = url
            config.username = user
            config.password = pass
            // Force re-login with new credentials
            config.sessionCookie = ""

            Toast.makeText(this, R.string.msg_saved, Toast.LENGTH_SHORT).show()
            finish()
        }

        btnClear.setOnClickListener {
            config.clearAll()
            etUrl.setText("")
            etUser.setText("")
            etPass.setText("")
            Toast.makeText(this, R.string.msg_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_MESSAGE = "extra_message"
    }
}
