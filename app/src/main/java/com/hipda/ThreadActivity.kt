package com.hipda

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.text.HtmlCompat
import com.bumptech.glide.Glide
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.regex.Pattern

class ThreadActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var threadProgressBar: ProgressBar
    private var threadId: String? = null
    private lateinit var toolbar: Toolbar
    private val client = OkHttpClient()
    private var currentPage = 1
    private var hasNextPage = false
    private var myCookie: String? = null

    // Data classes for structured post information
    private data class Post(
        val author: String,
        val content: String,
        val images: List<String>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_thread)

        container = findViewById(R.id.container)
        threadProgressBar = findViewById(R.id.threadProgressBar)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        threadId = intent.getStringExtra("tid")
        val threadTitle = intent.getStringExtra("title")
        supportActionBar?.title = threadTitle

        // Retrieve saved cookie
        myCookie = getSharedPreferences("AppPreferences", MODE_PRIVATE)
            .getString("COOKIE", null)
        if (myCookie.isNullOrEmpty()) {
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
        val urlToFetch = "https://www.4d4y.com/forum/viewthread.php?tid=$threadId&extra=page%3D1&page=$page"

        val request = Request.Builder()
            .url(urlToFetch)
            .addHeader("cookie", myCookie ?: "")
            .addHeader("referer", "https://www.4d4y.com/forum/")
            .addHeader("upgrade-insecure-requests", "1")
            .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ThreadActivity, "Network Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    threadProgressBar.visibility = View.GONE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { threadProgressBar.visibility = View.GONE }
                if (response.isSuccessful) {
                    val content = response.body?.string()
                    if (content != null) {
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

    private fun extractPosts(html: String): List<Post> {
        val pattern = Pattern.compile("(?s)<td class=\"postauthor\".*?<div class=\"postinfo\">.*?<a[^>]*?>(.*?)</a>.*?</div>.*?<td class=\"t_msgfont\" id=\"postmessage_(\\d+)\">(.*?)</td>")
        val matcher = pattern.matcher(html)
        val posts = mutableListOf<Post>()

        while (matcher.find()) {
            val author = matcher.group(1)
            var content = matcher.group(3)

            // Remove <div class="t_attach"> sections
            val tAttachRegex = Regex("<div class=\"t_attach\".*?</div>", RegexOption.DOT_MATCHES_ALL)
            content = tAttachRegex.replace(content, "")

            // Extract image URLs
            val imageRegex = "<img.*?src=\"(.*?)\".*?>".toRegex()
            val imageUrls = imageRegex.findAll(content)
                .map { it.groupValues[1] }
                .filterNot { url -> url.contains("default/attachimg.gif") || url.contains("smilies/") || url.contains("common/back.gif") }
                .toList()

            // Remove image tags from content
            val modifiedContent = imageRegex.replace(content, "")

            posts.add(Post(author, modifiedContent, imageUrls))
        }
        return posts
    }

    private fun formatPosts(posts: List<Post>) {
        posts.forEach { post ->
            // Add author
            val authorView = TextView(this).apply {
                text = post.author
                setTypeface(null, Typeface.BOLD)
                textSize = 18f
                setPadding(0, 16, 0, 8)
            }
            container.addView(authorView)

            // Add content
            val contentView = TextView(this).apply {
                text = HtmlCompat.fromHtml(post.content, HtmlCompat.FROM_HTML_MODE_LEGACY)
                textSize = 18f
                setPadding(0, 8, 0, 16)
            }
            container.addView(contentView)

            // Add images
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
}