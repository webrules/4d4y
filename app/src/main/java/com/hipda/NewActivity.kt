package com.hipda

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.regex.Pattern
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class NewActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var editTextSubject: EditText
    private lateinit var editTextBody: EditText
    private lateinit var buttonSubmit: Button

    private val client = OkHttpClient()
    private var myCookie: String = ""
    private var formHash: String = "58734250"
    private var textColor = 1
    private var devMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        setContentView(R.layout.activity_new)
        setTitle("来自D版带着爱")

        progressBar = findViewById(R.id.progressBar)
        editTextSubject = findViewById(R.id.editTextSubject)
        editTextBody = findViewById(R.id.editTextBody)
        buttonSubmit = findViewById(R.id.buttonSubmit)

        myCookie = getSharedPreferences("AppPreferences", MODE_PRIVATE)
            .getString("COOKIE", null) ?: ""

        if (myCookie.length < 50) {
            redirectToLogin()
        }

        buttonSubmit.setOnClickListener {
            val subject = editTextSubject.text.toString().trim()
            val body = editTextBody.text.toString().trim()
            if (subject.isEmpty() || body.isEmpty()) {
                Toast.makeText(this, "话题和正文都不能为空，请完成填写", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if ((subject.length<5) || (body.length<5)) {
                Toast.makeText(this, "话题和正文都至少需要五个字，请完成填写", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            submitNewThread(subject, body)
        }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // Close MainActivity to prevent going back without logging in
    }

    @SuppressLint("ServiceCast")
    private fun hideKeyboard() {
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        // Find the currently focused view
        val view = currentFocus
        if (view != null) {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            // Clear focus to prevent the keyboard from reopening
            view.clearFocus()
        }
    }

    private fun submitNewThread(subject: String, body: String) {
        // Encode parameters using GBK
        val subjectEncoded = URLEncoder.encode(subject, "GBK")
        val messageEncoded = URLEncoder.encode(body, "GBK")
        var typeid=56
        var fid = 2
        if (devMode) {
            typeid=7
            fid=62
        }
        // Manually build form data with proper encoding
        val formData = "formhash=$formHash" +
                "&posttime=1738155158" +
                "&wysiwyg=1" +
                "&iconid=" +
                "&subject=$subjectEncoded" +
                "&typeid=$typeid" +
                "&message=$messageEncoded" +
                "&tags=" +
                "&attention_add=1"

        val requestBody = formData.toRequestBody("application/x-www-form-urlencoded".toMediaType())

        val request = Request.Builder()
//            .url("https://www.4d4y.com/forum/post.php?action=newthread&fid=2&extra=&topicsubmit=yes")
            .url("https://www.4d4y.com/forum/post.php?action=newthread&fid=$fid&extra=&topicsubmit=yes")
            .addHeader("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .addHeader("accept-language", "en-US,en;q=0.9,zh-CN;q=0.8,zh-TW;q=0.7,zh;q=0.6")
            .addHeader("cache-control", "max-age=0")
            .addHeader("content-type", "application/x-www-form-urlencoded")
            .addHeader("cookie", myCookie ?: "")
            .addHeader("origin", "https://www.4d4y.com")
            .addHeader("priority", "u=0, i")
            .addHeader("referer", "https://www.4d4y.com/forum/post.php?action=newthread&fid=62")
            .addHeader("sec-ch-ua", "\"Not A(Brand\";v=\"8\", \"Chromium\";v=\"132\", \"Microsoft Edge\";v=\"132\"")
            .addHeader("sec-ch-ua-mobile", "?0")
            .addHeader("sec-ch-ua-platform", "\"macOS\"")
            .addHeader("sec-fetch-dest", "document")
            .addHeader("sec-fetch-mode", "navigate")
            .addHeader("sec-fetch-site", "same-origin")
            .addHeader("sec-fetch-user", "?1")
            .addHeader("upgrade-insecure-requests", "1")
            .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@NewActivity, "提交失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { progressBar.visibility = View.GONE }

                if (response.isSuccessful) {
//                    val responseHtml = response.body?.string()
                    val url = response.request.url.toString()
                    if (url.contains("https://www.4d4y.com/forum/viewthread.php?tid=")) {
                        val tid = Uri.parse(url).getQueryParameter("tid")?:""
//                    if (responseHtml?.contains("您的帖子已成功提交") == true) {
                        runOnUiThread {
                            Toast.makeText(this@NewActivity, "发帖成功!", Toast.LENGTH_SHORT).show()
//                            redirectToMain()
                            startThreadActivity(tid,editTextSubject.text.toString().trim())
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@NewActivity, "发帖失败，请检查内容", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@NewActivity, "服务器错误: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    fun mergeCookies(updatedCookie: String): String {
        // Step 1: Parse the existing cookies into a Map
        val cookieMap = myCookie.split("; ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .associate {
                val (key, value) = it.split("=", limit = 2)
                key to it // Store the full cookie string as the value
            }.toMutableMap()

        // Step 2: Parse the updated cookie
        val updatedCookieName = updatedCookie.substringBefore("=")
        cookieMap[updatedCookieName] = updatedCookie // Replace or add the updated cookie

        // Step 3: Reconstruct the final cookie string
        return cookieMap.values.joinToString("; ")
    }

    private fun extractFormHash(html: String) {
        val pattern = Pattern.compile("name=\"formhash\" value=\"([^\"]*)\"")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            formHash = matcher.group(1)
        }
    }

    private fun startThreadActivity(tid: String, title: String) {
        val intent = Intent(this, ThreadActivity::class.java)
        intent.putExtra("tid", tid)
        intent.putExtra("title", title)
        startActivity(intent)
    }

    private fun redirectToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Close MainActivity to prevent going back without logging in
    }

}