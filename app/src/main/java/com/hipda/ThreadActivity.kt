package com.hipda

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.text.HtmlCompat
import com.bumptech.glide.Glide
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.regex.Pattern

class ThreadActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var container: LinearLayout
    private lateinit var threadProgressBar: ProgressBar
    private lateinit var replyLayout: LinearLayout
    private lateinit var replyEditText: EditText
    private lateinit var replyButton: Button
    private var threadId: String? = null
    private lateinit var toolbar1: Toolbar
    private lateinit var toolbar: Toolbar
    private val client = OkHttpClient()
    private var currentPage = 1
    private var hasNextPage = false
    private var myCookie: String = ""
    private var formHash: String? = "58734250"
    private var fid: String? = "2"
    private var isNightMode = false

    private data class Post(
        val author: String,
        val content: String,
        val images: List<String>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isNightMode=(nightModeFlags == Configuration.UI_MODE_NIGHT_YES)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
//        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
//        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
//            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
//        } else {
//            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
//        }
        setContentView(R.layout.activity_thread)
        scrollView = findViewById(R.id.scrollView)
        container = findViewById(R.id.container)
        threadProgressBar = findViewById(R.id.threadProgressBar)
        replyLayout= findViewById(R.id.replyLayout)
        replyEditText = findViewById(R.id.replyEditText)
        replyButton = findViewById(R.id.replyButton)
        toolbar1 = findViewById(R.id.toolbar1)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if(isNightMode) {
            scrollView.setBackgroundColor(Color.BLACK)
            container.setBackgroundColor(Color.BLACK)
            replyEditText.setBackgroundColor(Color.BLACK)
            replyButton.setBackgroundColor(Color.BLACK)
            replyLayout.setBackgroundColor(Color.BLACK)
            toolbar1.setBackgroundColor(Color.BLACK)
            toolbar.setBackgroundColor(Color.BLACK)
            replyEditText.setTextColor(Color.WHITE)
            replyButton.setTextColor(Color.WHITE)
            toolbar.setTitleTextColor(Color.WHITE)
        }

        threadId = intent.getStringExtra("tid")
        val threadTitle = intent.getStringExtra("title")
        supportActionBar?.title = threadTitle

        myCookie = getSharedPreferences("AppPreferences", MODE_PRIVATE)
            .getString("COOKIE", null) ?: ""

        if (myCookie.length < 50) {
            redirectToLogin()
        } else {
            currentPage = 1
            loadPage(currentPage)

            val scrollView = findViewById<ScrollView>(R.id.scrollView)
            scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (hasNextPage) {
                    val viewHeight = scrollView.getChildAt(0).measuredHeight
                    val scrollHeight = scrollView.measuredHeight + scrollY
                    if (scrollHeight >= viewHeight) {
                        loadPage(currentPage)
                    }
                }
            }
        }

        replyButton.setOnClickListener {
            postReply()
        }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.thread_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.refresh -> {
                container.removeAllViews()
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

    private fun loadPage(page: Int) {
        threadProgressBar.visibility = View.VISIBLE
        val urlToFetch =
            "https://www.4d4y.com/forum/viewthread.php?tid=$threadId&extra=page%3D1&page=$page"

        val request = Request.Builder()
            .url(urlToFetch)
            .addHeader("cookie", myCookie ?: "")
            .addHeader("referer", "https://www.4d4y.com/forum/")
            .addHeader(
                "user-agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0"
            )
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@ThreadActivity,
                        "Network Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    threadProgressBar.visibility = View.GONE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { threadProgressBar.visibility = View.GONE }
                if (response.isSuccessful) {
                    val content = response.body?.string()
                    if (content == null || content.contains("您还未登录")) {
                        redirectToLogin()
                    } else {
                        extractFormHash(content)
//                        fid = extractFid(content)

                        // Update the cookie if a new one is received
                        val updatedCookies = response.headers("Set-Cookie")
                            .joinToString("; ") { it.substringBefore(";") }
                        myCookie = mergeCookies(updatedCookies)

                        val posts = extractPosts(content)
                        runOnUiThread {
                            formatPosts(posts)
                            hasNextPage = content.contains("class=\"next\"")
                            currentPage++
                        }
                    }
                }
            }
        })
    }

    private fun extractFormHash(html: String) {
        val pattern = Pattern.compile("name=\"formhash\" value=\"([^\"]*)\"")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            formHash = matcher.group(1)
        }
    }

    //    private fun extractFid(content: String): String? {
//        val pattern = Pattern.compile("name=\"fid\" value=\"(\\d+)\"")
//        val matcher = pattern.matcher(content)
//        return if (matcher.find()) matcher.group(1) else null
//    }
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

    private fun extractPosts(html: String): List<Post> {
        val pattern =
            Pattern.compile("(?s)<td class=\"postauthor\".*?<div class=\"postinfo\">.*?<a[^>]*?>(.*?)</a>.*?</div>.*?<td class=\"t_msgfont\" id=\"postmessage_(\\d+)\">(.*?)</td>")
        val matcher = pattern.matcher(html)
        val posts = mutableListOf<Post>()

        while (matcher.find()) {
            val author = matcher.group(1)
            var content = matcher.group(3)

            val tAttachRegex =
                Regex("<div class=\"t_attach\".*?</div>", RegexOption.DOT_MATCHES_ALL)
            content = tAttachRegex.replace(content, "")

            val imageRegex = "<img.*?src=\"(.*?)\".*?>".toRegex()
            val imageUrls = imageRegex.findAll(content)
                .map { it.groupValues[1] }
                .filterNot { url ->
                    url.contains("default/attachimg.gif") || url.contains("smilies/") || url.contains(
                        "common/back.gif"
                    )
                }
                .toList()

            val modifiedContent = imageRegex.replace(content, "")
            posts.add(Post(author, modifiedContent, imageUrls))
        }
        return posts
    }

    private fun formatPosts(posts: List<Post>) {

        posts.forEach { post ->
            val authorView = TextView(this).apply {
                text = post.author
                setTypeface(null, Typeface.BOLD)
                textSize = 18f
                setPadding(0, 16, 0, 8)
            }
            if (isNightMode) {
                authorView.setTextColor(Color.WHITE)
                authorView.setBackgroundColor(Color.BLACK)
            }
            container.addView(authorView)

            val contentView = TextView(this).apply {
                text = HtmlCompat.fromHtml(post.content, HtmlCompat.FROM_HTML_MODE_LEGACY)
                textSize = 18f
                setPadding(0, 8, 0, 16)
            }
            if (isNightMode) {
                contentView.setTextColor(Color.WHITE)
                contentView.setBackgroundColor(Color.BLACK)
            }
            container.addView(contentView)

            post.images.forEach { imageUrl ->
                val imageView = ImageView(this).apply {
                    adjustViewBounds = true
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 16)
                    }
                }
                container.addView(imageView)
                loadImage(imageUrl, imageView)
            }
        }
    }

    private fun loadImage(imageUrl: String, imageView: ImageView) {
        Glide.with(this)
            .load(imageUrl)
            .into(imageView)
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


    private fun postReply() {
        val message = replyEditText.text.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, "Reply cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val client = OkHttpClient()

        val requestBody =
            "formhash=$formHash&subject=&usesig=0&message=".plus(URLEncoder.encode(message, "GBK"))
                .toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(
                "https://www.4d4y.com/forum/post.php?action=reply&fid=2&tid=" +
                        this.threadId + "&extra=page%3D" + this.currentPage +
                        "&replysubmit=yes&infloat=yes&handlekey=fastpost&inajax=1"
            )
            .post(requestBody)
            .addHeader(
                "accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
            )
            .addHeader("accept-language", "en-US,en;q=0.9,zh-CN;q=0.8,zh-TW;q=0.7,zh;q=0.6")
            .addHeader("cookie", myCookie ?: "")
            .addHeader(
                "user-agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0"
            )
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@ThreadActivity,
                        "Failed to post reply: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        replyEditText.setText("")
                        Toast.makeText(
                            this@ThreadActivity,
                            "Reply posted successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        hideKeyboard()
                        container.removeAllViews()
//                        currentPage = 1
                        loadPage(currentPage)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@ThreadActivity,
                            "Failed to post reply: ${response.code}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }
}