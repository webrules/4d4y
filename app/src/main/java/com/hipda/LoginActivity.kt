package com.hipda

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.regex.Pattern

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var loginProgressBar: ProgressBar
    private val client = OkHttpClient()
    private var formHash: String = "58734250"
    private lateinit var securityQuestionSpinner: Spinner
    private lateinit var answerEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        setContentView(R.layout.activity_login)
        setTitle("来自D版带着爱")

        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        loginProgressBar = findViewById(R.id.loginProgressBar)
        securityQuestionSpinner = findViewById(R.id.securityQuestionSpinner)
        answerEditText = findViewById(R.id.answerEditText)

        loginButton.setOnClickListener {
            login()
        }
    }

    private fun extractFormHash(html: String) {
        val pattern = Pattern.compile("name=\"formhash\" value=\"([^\"]*)\"")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            formHash = matcher.group(1)
        }
    }

    private fun login() {
        val username = usernameEditText.text.toString()
        val password =
            passwordEditText.text.toString() //  In a real app, hash the password securely!
        val selectedQuestion = securityQuestionSpinner.selectedItemPosition.toString()
//        val answer = answerEditText.text.toString()
        val s1 = answerEditText.text.toString()
        println("Original String: $s1")
        val s2 = URLDecoder.decode(s1, "UTF-8")
        println("Decoded String: $s2")
        val answer = URLEncoder.encode(s2, "GBK")
        println("GBK Encoded String: $answer")

        val client = OkHttpClient()
        val url = "https://www.4d4y.com/forum/logging.php?action=login&loginsubmit=yes"

        // Request body (already URL-encoded)
        val requestBody = """
        sid=slxZ7x&formhash=$formHash&loginfield=username&username=$username
        &password=$password&questionid=$selectedQuestion
        &answer=$answer&loginsubmit=true
    """.trimIndent().replace("\n", "").toRequestBody("application/x-www-form-urlencoded".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .addHeader("accept-language", "en-US,en;q=0.9,zh-CN;q=0.8,zh-TW;q=0.7,zh;q=0.6")
            .addHeader("cache-control", "max-age=0")
            .addHeader("content-type", "application/x-www-form-urlencoded")
            .addHeader("cookie", "smile=1D1; discuz_fastpostrefresh=0; cdb_cookietime=2592000; cdb_fid2=1738294342; cdb_oldtopics=D3354010D3353748D3342352D3353996D3354025D3354031D3354035D; cdb_sid=slxZ7x; cdb_sid=Hgri4P; cdb_visitedfid=2")
            .addHeader("origin", "https://www.4d4y.com")
            .addHeader("priority", "u=0, i")
            .addHeader("referer", "https://www.4d4y.com/forum/forumdisplay.php?fid=2")
            .addHeader("sec-ch-ua", """ "Not A(Brand";v="8", "Chromium";v="132", "Microsoft Edge";v="132" """.trimIndent())
            .addHeader("sec-ch-ua-mobile", "?0")
            .addHeader("sec-ch-ua-platform", """"macOS"""")
            .addHeader("sec-fetch-dest", "document")
            .addHeader("sec-fetch-mode", "navigate")
            .addHeader("sec-fetch-site", "same-origin")
            .addHeader("sec-fetch-user", "?1")
            .addHeader("upgrade-insecure-requests", "1")
            .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0")
            .build()

        loginProgressBar.visibility = View.VISIBLE  // Show progress bar

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loginProgressBar.visibility = View.GONE
                    Toast.makeText(
                        this@LoginActivity,
                        "Login failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    loginProgressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
//                        println("Response body: $responseBody")

                        if (responseBody?.contains("欢迎您回来") == true) {
                            // Extract cookies from headers
                            val cookies = response.headers("Set-Cookie")
                            val cookieString =
                                cookies.joinToString("; ") { it.substringBefore(";") }

                            // Save cookies to SharedPreferences
                            getSharedPreferences("AppPreferences", MODE_PRIVATE).edit().apply {
                                putString("COOKIE", cookieString)
                                apply()
                            }

                            Toast.makeText(
                                this@LoginActivity,
                                "登录成功!",
                                Toast.LENGTH_SHORT
                            ).show()
                            redirectToMain()
                        } else {
                            Toast.makeText(
                                this@LoginActivity,
                                "登录失败，请检查你的用户名、密码等",
                                Toast.LENGTH_SHORT
                            ).show()
                        }


                    } else {
                        Toast.makeText(
                            this@LoginActivity,
                            "Login failed: ${response.code}",
                            Toast.LENGTH_SHORT
                        ).show()
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