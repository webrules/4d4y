package com.hipda

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
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hipda.LoginActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var textViewContent: TextView
    private lateinit var progressBar: ProgressBar
    private val client = OkHttpClient()
    private var myCookie: String? = null
    private var currentPage = 1
    private var hasNextPage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setTitle("来自D版带着爱")

        textViewContent = findViewById(R.id.textViewContent)
        textViewContent.setTextSize(18f)

        progressBar = findViewById(R.id.progressBar)

        // Retrieve saved cookie
        myCookie = getSharedPreferences("AppPreferences", MODE_PRIVATE)
            .getString("COOKIE", null)
        if ((myCookie.isNullOrEmpty()) || (myCookie.toString().length<50)) {
            // Redirect to LoginActivity if no cookie is available
            redirectToLogin()
        } else {

            loadPage(currentPage)
            val scrollView = findViewById<ScrollView>(R.id.scrollView)
            scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (hasNextPage) {
                    // Logic to detect scroll to bottom
                    val viewHeight = scrollView.getChildAt(0).measuredHeight
                    val scrollHeight = scrollView.measuredHeight + scrollY

                    if (scrollHeight >= viewHeight) {
                        // User has scrolled to the bottom
                        loadPage(currentPage + 1)
                        currentPage++
                    }
                }
            }
        }

    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // Close MainActivity to prevent going back without logging in
    }

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

//                                // Update the cookie if a new one is received
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

    private fun extractLinks(html: String?): CharSequence {
        if (html == null) return ""

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