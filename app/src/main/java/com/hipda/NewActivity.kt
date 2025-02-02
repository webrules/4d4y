package com.hipda

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.regex.Pattern
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.asRequestBody
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset

class NewActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var editTextSubject: EditText
    private lateinit var editTextBody: EditText
    private lateinit var buttonSubmit: Button
    private lateinit var tvSizeInfo: TextView

    private val client = OkHttpClient()
    private var myCookie: String = ""
    private var formHash: String = "58734250"
    private var textColor = 1
    private var devMode = false
    private lateinit var imageRecyclerView: RecyclerView
    private lateinit var imageAdapter: ImageAdapter
    private val PICK_IMAGE_REQUEST = 1001
    private var typeid = "56"
    private var fid = "2"
    private var formData: Map<String, String> = emptyMap()
    private val selectedImages = mutableListOf<Uri>()
    private val selectedImageFiles = mutableListOf<File>()
    private var uploadedImageIds: MutableList<String> = mutableListOf()
    private var tid: String = ""

    private val MAX_FILES = 100
    private val MAX_FILE_SIZE = 8 * 1024 * 1024 // 8MB in bytes
    private val MAX_TOTAL_SIZE = 800 * 1024 * 1024 // 800MB in bytes
    private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "gif", "png", "bmp")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        setContentView(R.layout.activity_new)
        setTitle("来自D版带着爱")

        myCookie = getSharedPreferences("AppPreferences", MODE_PRIVATE)
            .getString("COOKIE", null) ?: ""

        if (myCookie.length < 50) {
            redirectToLogin()
        }

        if (devMode) {
            typeid ="7"
            fid = "62"
        }

        progressBar = findViewById(R.id.progressBar)
        editTextSubject = findViewById(R.id.editTextSubject)
        editTextBody = findViewById(R.id.editTextBody)
        buttonSubmit = findViewById(R.id.buttonSubmit)
        tvSizeInfo = findViewById(R.id.tvSizeInfo)

        // Initialize RecyclerView
        imageRecyclerView = findViewById(R.id.imageRecyclerView)
        imageAdapter = ImageAdapter(selectedImages) { position ->
            selectedImages.removeAt(position)
            imageAdapter.notifyItemRemoved(position)
        }
        imageRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        imageRecyclerView.adapter = imageAdapter

        // Set camera button click listener
        findViewById<ImageButton>(R.id.buttonCamera).setOnClickListener {
            openImagePicker()
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

            // Additional image validation
            if (selectedImages.size > MAX_FILES) {
                Toast.makeText(this, "最多只能上传$MAX_FILES 个文件", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalSize = selectedImages.sumOf { getFileSizeFromUri(it) }
            if (totalSize > MAX_TOTAL_SIZE) {
                Toast.makeText(this, "总文件大小不能超过${MAX_TOTAL_SIZE/1024/1024}MB", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check individual file sizes
            selectedImages.forEach { uri ->
                if (getFileSizeFromUri(uri) > MAX_FILE_SIZE) {
                    Toast.makeText(this, "包含超过${MAX_FILE_SIZE/1024/1024}MB的文件", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // Check extensions
            if (selectedImages.any { !isValidExtension(it) }) {
                Toast.makeText(this, "包含不支持的文件类型", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            submitNewThread(subject, body)
        }

        fetchInitialFormData()
    }

    private fun fetchInitialFormData() {
        progressBar.visibility = View.VISIBLE
        buttonSubmit.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("https://www.4d4y.com/forum/post.php?action=newthread&fid=$fid")
                    .get()
                    .addHeader("cookie", myCookie ?: "")
                    .addHeader("referer", "https://www.4d4y.com/forum/")
                    .addHeader(
                        "user-agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0"
                    )
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
//                    println(html)
                    formData = parseFormData(html)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@NewActivity, "数据准备完成!", Toast.LENGTH_SHORT).show()
                        buttonSubmit.isEnabled = true // Enable button after fetching data
                    }
                } else {
                    handleError("数据准备失败，请稍后重试: ${response.code}")
                }
            } catch (e: IOException) {
                handleError("网络故障，数据准备失败: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun handleError(message: String) {
        Log.e("NewPostActivity", message)
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun parseFormData(html: String): Map<String, String> {
        var doc = Jsoup.parse(html)
        var form = doc.select("form#postform") // Select the form
        var inputElements = form.select("input[type=hidden]") // Select hidden input elements

        val formDataMap = mutableMapOf<String, String>()
        for (input in inputElements) {
            val name = input.attr("name")
            val value = input.attr("value")
            if (name.isNotEmpty()) {
                formDataMap[name] = value
            }
        }

        form = doc.select("form#imgattachform") // Select the form
        inputElements = form.select("input[type=hidden]") // Select hidden input elements
        for (input in inputElements) {
            val name = input.attr("name")
            val value = input.attr("value")
            if (name.isNotEmpty()) {
                formDataMap[name] = value
            }
        }

        // Add wysiwyg and iconid as they are also needed and might not be hidden
        formDataMap["wysiwyg"] = doc.selectFirst("input[name=wysiwyg]")?.attr("value") ?: "1" // Default to 1 if not found
        formDataMap["iconid"] = doc.selectFirst("input[name=iconid]")?.attr("value") ?: "" // Default to empty if not found

//        Log.d("FormData", "Parsed form data: $formDataMap")
        return formDataMap
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK) {
            val newUris = mutableListOf<Uri>()
            var currentCount = selectedImages.size
            var currentTotal = selectedImages.sumOf { getFileSizeFromUri(it) }

            when {
                data?.clipData != null -> {
                    val clipData = data.clipData!!
                    for (i in 0 until clipData.itemCount) {
                        if (currentCount >= MAX_FILES) {
                            Toast.makeText(this, "最多只能选择$MAX_FILES 个文件", Toast.LENGTH_SHORT).show()
                            break
                        }

                        val uri = clipData.getItemAt(i).uri
                        when {
                            !isValidExtension(uri) -> {
                                Toast.makeText(this, "不支持的文件类型", Toast.LENGTH_SHORT).show()
                            }
                            getFileSizeFromUri(uri) > MAX_FILE_SIZE -> {
                                Toast.makeText(this, "单个文件不能超过${MAX_FILE_SIZE/1024/1024}MB", Toast.LENGTH_SHORT).show()
                            }
                            currentTotal + getFileSizeFromUri(uri) > MAX_TOTAL_SIZE -> {
                                Toast.makeText(this, "总大小不能超过${MAX_TOTAL_SIZE/1024/1024}MB", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                newUris.add(uri)
                                currentCount++
                                currentTotal += getFileSizeFromUri(uri)
                            }
                        }
                    }
                }
                data?.data != null -> {
                    val uri = data.data!!
                    when {
                        selectedImages.size >= MAX_FILES -> {
                            Toast.makeText(this, "最多只能选择$MAX_FILES 个文件", Toast.LENGTH_SHORT).show()
                        }
                        !isValidExtension(uri) -> {
                            Toast.makeText(this, "不支持的文件类型", Toast.LENGTH_SHORT).show()
                        }
                        getFileSizeFromUri(uri) > MAX_FILE_SIZE -> {
                            Toast.makeText(this, "单个文件不能超过${MAX_FILE_SIZE/1024/1024}MB", Toast.LENGTH_SHORT).show()
                        }
                        selectedImages.sumOf { getFileSizeFromUri(it) } + getFileSizeFromUri(uri) > MAX_TOTAL_SIZE -> {
                            Toast.makeText(this, "总大小不能超过${MAX_TOTAL_SIZE/1024/1024}MB", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            newUris.add(uri)
                        }
                    }
                }
            }

            selectedImages.addAll(newUris)
            imageAdapter.notifyDataSetChanged()
            updateSizeDisplay()
        }
    }

    // Add extension validation
    private fun isValidExtension(uri: Uri): Boolean {
        val extension = getFileExtension(this, uri)?.lowercase()
        return extension in ALLOWED_EXTENSIONS
    }

    private fun getFileExtension(context: Context, uri: Uri): String? {
        return try {
            // First try to get from filename
            var extension: String? = null
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val fileName = cursor.getString(nameIndex)
                        extension = fileName.substringAfterLast('.', "")
                            .takeIf { it.isNotEmpty() }
                            ?.lowercase()
                    }
                }
            }

            // If filename method failed, try MIME type
            extension ?: run {
                context.contentResolver.getType(uri)?.let { mimeType ->
                    MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(mimeType)
                        ?.lowercase()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // Update your size display
    private fun updateSizeDisplay() {
        val currentCount = selectedImages.size
        val totalSizeMB = selectedImages.sumOf { getFileSizeFromUri(it) } / (1024 * 1024)

        findViewById<TextView>(R.id.tvSizeInfo).text =
            "已选${currentCount}/$MAX_FILES 个文件，总大小${totalSizeMB}MB/${MAX_TOTAL_SIZE/1024/1024}MB"
    }

    // Add this inner class for the adapter
    inner class ImageAdapter(
        private val images: List<Uri>,
        private val onRemove: (Int) -> Unit
    ) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.imageView)
            val removeButton: ImageButton = view.findViewById(R.id.removeButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_image, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val uri = images[position]
            Glide.with(this@NewActivity)
                .load(uri)
//                .override(200, 200)
                .centerCrop()
                .into(holder.imageView)

            holder.removeButton.setOnClickListener {
                onRemove(position)
            }
        }

        override fun getItemCount() = images.size
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // Close MainActivity to prevent going back without logging in
    }

    private fun uriToFile(uri: Uri, context: Context): File? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        return try {
            val extension = "." + getFileExtension(context, uri)
            val tempFile = File.createTempFile("temp_image", extension, context.cacheDir).apply {
                deleteOnExit()
            }

            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            tempFile
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } finally {
            inputStream.close()
        }
    }

    private fun getOriginalFileName(context: Context, uri: Uri): String? {
        var fileName: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        fileName = cursor.getString(displayNameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FileUtils", "Error getting original filename", e)
        }
        return fileName ?: "image_${System.currentTimeMillis()}.jpg" // Fallback name
    }

    private fun populateImageFiles() {
        selectedImageFiles.clear() // Clear previous Files
        for (imageUri in selectedImages) {
            val file = uriToFile(imageUri, this) // Convert Uri to File
            if (file != null) {
                selectedImageFiles.add(file)
            } else {
                // Handle conversion failure (e.g., show a Toast)
                Log.e("ImageConversion", "Failed to convert Uri to File for: $imageUri")
                Toast.makeText(this, "Error converting some images.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitNewThread(subject: String, messageBody: String) {
        progressBar.visibility = View.VISIBLE
        buttonSubmit.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            uploadedImageIds.clear() // Clear previous uploads for a new post

            populateImageFiles()

            // 1. Upload Images
            val imageUploadResults = selectedImageFiles.map { imageFile ->
                async { uploadImage(imageFile) } // Upload images concurrently
            }.awaitAll()

            uploadedImageIds.addAll(imageUploadResults.filterNotNull()) // Collect successful image IDs

            // 2. Create Post Message with Image IDs
            val formattedMessage = formatMessageWithImages(messageBody, uploadedImageIds)

            // 3. Submit the Post
            val postSuccessful = submitPostToServer(subject, typeid, formattedMessage)

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                buttonSubmit.isEnabled = true
                if (postSuccessful) {
                    Toast.makeText(this@NewActivity, "成功发布了你的新帖子!", Toast.LENGTH_LONG).show()
                    startThreadActivity(tid, editTextSubject.text.toString().trim())
                } else {
                    Toast.makeText(this@NewActivity, "新帖发布失败", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun uploadImage(imageFile: File): String? {
        Log.d("UploadImage", "Starting upload for: ${imageFile.name}")
        try {
            // Get original filename from selectedImages Uris
            val index = selectedImageFiles.indexOf(imageFile)
            if (index == -1) {
                Log.e("UploadImage", "File not found in selectedImageFiles list")
                return null
            }
            val imageUri = selectedImages[index]
            val originalFileName = getOriginalFileName(this, imageUri)

            // Get required parameters from form data
            val uid = formData["uid"] ?: run {
                Log.e("UploadImage", "UID not found in form data")
                return null
            }
            val hash = formData["hash"] ?: run {
                Log.e("UploadImage", "Hash not found in form data")
                return null
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("uid", uid)
                .addFormDataPart("hash", hash)
                .addFormDataPart(
                    "Filedata",
                    originalFileName, // Use original filename here
                    imageFile.asRequestBody("image/*".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("https://www.4d4y.com/forum/misc.php?action=swfupload&operation=upload&simple=1&type=image")
                .post(requestBody)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh-TW;q=0.7,zh;q=0.6")
                .header("Cache-Control", "max-age=0")
                .header("Origin", "https://www.4d4y.com")
                .addHeader("cookie", myCookie ?: "")
                .header("Referer", "https://www.4d4y.com/forum/post.php?action=newthread&fid=$fid")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0") // Mimic browser User-Agent
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.d("UploadImage", "Upload Response: $responseBody")
                return extractImageIdFromUploadResponse(responseBody)
            } else {
                Log.e("UploadImage", "Upload failed for ${imageFile.name}: ${response.code} - ${response.message}")
                return null
            }
        } catch (e: IOException) {
            Log.e("UploadImage", "Network error uploading ${imageFile.name}: ${e.message}")
            return null
        }
    }

    private fun extractImageIdFromUploadResponse(responseBody: String): String? {
        val parts = responseBody.split("|")
        if (parts.size >= 3 && parts[0] == "DISCUZUPLOAD" && parts[1] == "0") {
            return parts[2] // Image ID is the third part
        }
        Log.e("UploadImage", "Failed to extract image ID from response: $responseBody")
        return null
    }

    private fun formatMessageWithImages(messageBody: String, imageIds: List<String>): String {
        var formattedMessage = messageBody
        imageIds.forEach { imageId ->
            formattedMessage += "\n[attachimg]$imageId[/attachimg]\n"
        }
        return formattedMessage
    }

    private fun consolidateImages(): String {
        var consolidatedImages = ""
        uploadedImageIds.forEach { imageId ->
            consolidatedImages += "[attachimg]$imageId[/attachimg]"
        }
        return consolidatedImages
    }

    private suspend fun submitPostToServer(subject: String, typeId: String, message: String): Boolean {
        try {

            val formBodyBuilder = okhttp3.FormBody.Builder(Charset.forName("GBK")) // Important: Use GBK charset
                .add("formhash", formData["formhash"] ?: "")
                .add("posttime", formData["posttime"] ?: "")
                .add("wysiwyg", formData["wysiwyg"] ?: "1")
                .add("iconid", formData["iconid"] ?: "")
                .add("subject", subject)
                .add("typeid", typeId)
                .add("message", message + consolidateImages())
                .add("tags", "") // Add tags if you have them
                .add("attention_add", "1") // Default to attention_add=1

            uploadedImageIds.forEachIndexed { index, imageId ->
                formBodyBuilder.add("attachnew[${imageId}][description]", "") // Empty description for images
            }

            val formBody = formBodyBuilder.build()

            val request = Request.Builder()
                .url("https://www.4d4y.com/forum/post.php?action=newthread&fid=$fid&extra=&topicsubmit=yes")
                .post(formBody)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh-TW;q=0.7,zh;q=0.6")
                .header("Cache-Control", "max-age=0")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://www.4d4y.com")
                .addHeader("cookie", myCookie ?: "")
                .header("Referer", "https://www.4d4y.com/forum/post.php?action=newthread&fid=$fid")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0") // Mimic browser User-Agent
                .build()

            val response = client.newCall(request).execute()
            return if (response.isSuccessful) {
                val url = response.request.url.toString()
                if (url.contains("https://www.4d4y.com/forum/viewthread.php?tid=")) {
                    tid = Uri.parse(url).getQueryParameter("tid") ?: ""
                }
                Log.d("SubmitPost", "Post submitted successfully! Response: ${response.body?.string()}")
                true
            } else {
                Log.e("SubmitPost", "Post submission failed: ${response.code} - ${response.message} - ${response.body?.string()}")
                false
            }
        } catch (e: IOException) {
            Log.e("SubmitPost", "Network error submitting post: ${e.message}")
            return false
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