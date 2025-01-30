package com.hipda

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private lateinit var textViewContent: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var editTextSubject: EditText
    private lateinit var editTextBody: EditText
    private lateinit var buttonSubmit: Button

    private val client = OkHttpClient()
    private var myCookie: String = ""
    private var formHash: String = "58734250"
    private var currentPage = 1
    private var hasNextPage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setTitle("来自D版带着爱")

        textViewContent = findViewById(R.id.textViewContent)
        progressBar = findViewById(R.id.progressBar)
        editTextSubject = findViewById(R.id.editTextSubject)
        editTextBody = findViewById(R.id.editTextBody)
        buttonSubmit = findViewById(R.id.buttonSubmit)

        myCookie = getSharedPreferences("AppPreferences", MODE_PRIVATE)
            .getString("COOKIE", null) ?: ""

        if (myCookie.length < 50) {
            redirectToLogin()
        } else {
            initializeApp()
        }

        buttonSubmit.setOnClickListener {
            val subject = editTextSubject.text.toString().trim()
            val body = editTextBody.text.toString().trim()
            if (subject.isEmpty() || body.isEmpty()) {
                Toast.makeText(this, "Subject and body cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            submitNewThread(subject, body)
        }
    }

    private fun initializeApp() {
        loadPage(currentPage)
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (hasNextPage) {
                val viewHeight = scrollView.getChildAt(0).measuredHeight
                val scrollHeight = scrollView.measuredHeight + scrollY
                if (scrollHeight >= viewHeight) {
                    loadPage(currentPage + 1)
                    currentPage++
                }
            }
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

        // Manually build form data with proper encoding
        val formData = "formhash=$formHash" +
                "&posttime=1738155158" +
                "&wysiwyg=1" +
                "&iconid=" +
                "&subject=$subjectEncoded" +
                "&typeid=56" +
                "&message=$messageEncoded" +
                "&tags=" +
                "&attention_add=1"

        val requestBody = formData.toRequestBody("application/x-www-form-urlencoded".toMediaType())

        val request = Request.Builder()
            .url("https://www.4d4y.com/forum/post.php?action=newthread&fid=2&extra=&topicsubmit=yes")
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
                    Toast.makeText(this@MainActivity, "提交失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { progressBar.visibility = View.GONE }

                if (response.isSuccessful) {
//                    val responseHtml = response.body?.string()
                    if (response.request.url.toString().contains("https://www.4d4y.com/forum/viewthread.php?tid=")) {
//                    if (responseHtml?.contains("您的帖子已成功提交") == true) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "发帖成功!", Toast.LENGTH_SHORT).show()
                            editTextSubject.text.clear()
                            editTextBody.text.clear()
                            hideKeyboard()
                            currentPage = 1
                            textViewContent.text = SpannableStringBuilder()
                            loadPage(currentPage)
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "发帖失败，请检查内容", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "服务器错误: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

//    private fun extractHiddenFormFields(html: String): Map<String, String> {
//        val regex = Regex("<input type=\"hidden\" name=\"([^\"]+)\" value=\"([^\"]*)\"")
//        return regex.findAll(html).associate { it.groupValues[1] to it.groupValues[2] }
//    }

    private fun loadPage(page: Int) {
        progressBar.visibility = View.VISIBLE

        val urlToFetch = "https://www.4d4y.com/forum/forumdisplay.php?fid=2&page=" + page.toString()

        val request = Request.Builder()
            .url(urlToFetch)
            .addHeader("cookie", myCookie ?: "")
            .addHeader("referer", "https://www.4d4y.com/forum/")
            .addHeader("upgrade-insecure-requests", "1")
            .addHeader(
                "user-agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0"
            )
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Network Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    progressBar.visibility = View.GONE
                }
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { progressBar.visibility = View.GONE }
                if (response.isSuccessful) {
                    val content = response.body?.string()
                    if (content != null) {
                        if (content.contains("您还未登录")) {
                            redirectToLogin()
                        }
                        else {
                            val threads = extractLinks(content)

                            runOnUiThread {
                                if (currentPage == 1) {
                                    textViewContent.text = threads
                                } else {
                                    textViewContent.append(threads)
                                }
                                textViewContent.movementMethod = LinkMovementMethod.getInstance()

                                // Update the cookie if a new one is received
                                val updatedCookies = response.headers("Set-Cookie").joinToString("; ") { it.substringBefore(";") }
                                myCookie = mergeCookies(updatedCookies)
//                                response.headers("Set-Cookie").forEach { headerValue ->
//                                    val cookieValue = headerValue.substringBefore(";", headerValue)
//                                    myCookie = cookieValue
//                                }

                                hasNextPage = content.contains("class=\"next\"")
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Error fetching data: ${response.code}",
                            Toast.LENGTH_SHORT
                        ).show()
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

    private fun extractFormHash(html: String){
        val pattern = Pattern.compile("name=\"formhash\" value=\"([^\"]*)\"")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            formHash = matcher.group(1)
        }
    }

    private fun extractLinks(html: String?): CharSequence {
        if (html == null) return ""
        extractFormHash(html)

//        val regex = "<a href=\"viewthread\\.php\\?tid=(\\d+)&amp;extra=page%3D$currentPage\">(.*?)</a>".toRegex()
//        val regex = "<a href=\"viewthread\\.php\\?tid=(\\d+)&amp;extra=page%3D$currentPage\">(.*?)</a></span>\\n.*?</th>\\n<td class=\"author\">\\n<cite>\\n<a href=\".*?\">(.*?)</a>".toRegex()
        val regex = "<tbody id=\"normalthread_(\\d+)\">[\\s\\S]*?<span id=\"thread_\\d+\"><a[^>]*>([^<]+)</a></span>[\\s\\S]*?<cite>\\s*<a[^>]*>([^<]+)</a>\\s*</cite>".toRegex()
        val matches = regex.findAll(html)
        val spannableStringBuilder = SpannableStringBuilder()

        matches.forEach { matchResult ->
            val tid = matchResult.groupValues[1]
            val text = matchResult.groupValues[2]
            val author = matchResult.groupValues[3]

            val line = "$text\n\n"
//            val line = "$text [$author]\n\n"
            val spannableLine = SpannableString(line)

            val clickableSpan = object : ClickableSpan() {
                override fun onClick(view: View) {
                    startThreadActivity(tid, text)
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = Color.BLACK
                    ds.isUnderlineText = false // Adds an underline for visibility
                }
            }

            spannableLine.setSpan(clickableSpan, 0, line.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableStringBuilder.append(spannableLine)
        }

        return spannableStringBuilder
    }

    private fun startThreadActivity(tid: String, title: String) {
        val intent = Intent(this, ThreadActivity::class.java)
        intent.putExtra("tid", tid)
        intent.putExtra("title", title)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.thread_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.refresh -> {
                loadPage(currentPage)
                true
            }

            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}