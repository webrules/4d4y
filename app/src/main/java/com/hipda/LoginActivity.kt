package com.hipda

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hipda.R
import okhttp3.*
import java.io.IOException
import java.util.regex.Pattern

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var loginProgressBar: ProgressBar
    private val client = OkHttpClient()
    private var formHash: String = "58734250"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        setTitle("来自D版带着爱")

        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        loginProgressBar = findViewById(R.id.loginProgressBar)

        loginButton.setOnClickListener {
            login()
        }
    }

    private fun extractFormHash(html: String){
        val pattern = Pattern.compile("name=\"formhash\" value=\"([^\"]*)\"")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            formHash = matcher.group(1)
        }
    }

    private fun login() {
        val username = usernameEditText.text.toString()
        val password = passwordEditText.text.toString() //  In a real app, hash the password securely!

        val formBody = FormBody.Builder()
            .add("formhash", formHash)
            .add("referer", "https://www.4d4y.com/forum/")
            .add("loginfield", "username")
            .add("username", username)
            .add("password", password) // Hash this securely in a real app
            .add("questionid", "0")
            .add("answer", "")
            .add("cookietime", "2592000")
            .add(
                "user-agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0"
            )
            .build()

        val request = Request.Builder()
            .url("https://www.4d4y.com/forum/logging.php?action=login&loginsubmit=yes&inajax=1")
            .post(formBody)
            // ... Add other headers from your curl command as needed
            //.addHeader("Cookie", "cdb_cookietime=2592000; ...") // Update with your actual cookie
            .build()

        loginProgressBar.visibility = View.VISIBLE  // Show progress bar

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loginProgressBar.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    loginProgressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        // Parse the response to check for successful login
                        Log.d("Login Response", responseBody.toString())
                        if (responseBody?.contains("欢迎您回来") == true) {
                            // Extract cookies from headers
                            val cookies = response.headers("Set-Cookie")
                            val cookieString = cookies.joinToString("; ") { it.substringBefore(";") }

                            // Save cookies to SharedPreferences
                            getSharedPreferences("AppPreferences", MODE_PRIVATE).edit().apply {
                                putString("COOKIE", cookieString)
                                apply()
                            }

                            Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                            redirectToMain()
                        } else {
                            Toast.makeText(this@LoginActivity, "Login failed. Check credentials.", Toast.LENGTH_SHORT).show()
                        }


                    } else {
                        Toast.makeText(this@LoginActivity, "Login failed: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun redirectToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Close MainActivity to prevent going back without logging in
    }

}