package com.hipda

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var editTextSubject: EditText
    private lateinit var editTextBody: EditText
    private lateinit var buttonSubmit: Button

    private val client = OkHttpClient()
    private var myCookie: String = ""
    private var formHash: String = "58734250"
    private var currentPage = 1
    private var hasNextPage = false
    private var textColor  = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        setContentView(R.layout.activity_main)
        setTitle("来自D版带着爱")

        textViewContent = findViewById(R.id.textViewContent)
        this.textColor = ContextCompat.getColor(this, R.color.textColor)
        progressBar = findViewById(R.id.progressBar)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

//        editTextSubject = findViewById(R.id.editTextSubject)
//        editTextBody = findViewById(R.id.editTextBody)
//        buttonSubmit = findViewById(R.id.buttonSubmit)

        myCookie = getSharedPreferences("AppPreferences", MODE_PRIVATE)
            .getString("COOKIE", null) ?: ""

        if (myCookie.length < 50) {
            redirectToLogin()
        } else {
            initializeApp()
        }

//        buttonSubmit.setOnClickListener {
//            val subject = editTextSubject.text.toString().trim()
//            val body = editTextBody.text.toString().trim()
//            if (subject.isEmpty() || body.isEmpty()) {
//                Toast.makeText(this, "Subject and body cannot be empty", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//            submitNewThread(subject, body)
//        }
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

        swipeRefreshLayout.setOnRefreshListener {
            currentPage = 1
            loadPage(1)
            swipeRefreshLayout.isRefreshing = false
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

    private fun loadPage(page: Int) {
        if (page == 1) {
            textViewContent.text = ""
        }
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
                    ds.isUnderlineText = false // Adds an underline for visibility
                    val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                        ds.color = textColor
                    } else {
                        ds.color = Color.BLACK
                    }
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
                loadPage(1)
                true
            }

            R.id.newTopic -> { // Handle plus icon click
                startActivity(Intent(this, NewActivity::class.java))
                true
            }
            android.R.id.home -> {
                loadPage(1)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}