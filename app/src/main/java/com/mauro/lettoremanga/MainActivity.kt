package com.mauro.lettoremanga

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tvPageInfo: TextView
    private lateinit var btnDirection: Button
    private lateinit var btnBookmark: Button
    private lateinit var topBar: LinearLayout

    private var pages: List<DocumentFile> = emptyList()
    private var isMangaRtl = true
    private var currentFolderUri: String = ""

    private val openFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            loadFolder(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        viewPager = ViewPager2(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(viewPager, FrameLayout.LayoutParams(-1, -1))

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#CC111111"))
            setPadding(24, 48, 24, 24)
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnFolder = Button(this).apply {
            text = "Cartella"
            setOnClickListener { openFolderLauncher.launch(null) }
        }

        btnDirection = Button(this).apply {
            text = "DX→SX"
            setOnClickListener {
                isMangaRtl = !isMangaRtl
                text = if (isMangaRtl) "DX→SX" else "SX→DX"
                viewPager.layoutDirection = if (isMangaRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            }
        }

        btnBookmark = Button(this).apply {
            text = "Segnalibro"
            setOnClickListener {
                if (pages.isNotEmpty() && currentFolderUri.isNotEmpty()) {
                    val prefs = getSharedPreferences("manga_prefs", Context.MODE_PRIVATE)
                    val saved = prefs.getInt(currentFolderUri, -1)
                    val current = viewPager.currentItem
                    if (saved == current) {
                        Toast.makeText(context, "Già al segnalibro (Pag. ${current + 1})", Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.edit().putInt(currentFolderUri, current).apply()
                        Toast.makeText(context, "Segnalibro salvato a Pag. ${current + 1}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            setOnLongClickListener {
                val prefs = getSharedPreferences("manga_prefs", Context.MODE_PRIVATE)
                val saved = prefs.getInt(currentFolderUri, -1)
                if (saved in pages.indices) {
                    viewPager.setCurrentItem(saved, false)
                    Toast.makeText(context, "Tornato al segnalibro (Pag. ${saved + 1})", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }

        tvPageInfo = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            text = "Apri cartella"
            setPadding(16, 0, 0, 0)
        }

        topBar.addView(btnFolder)
        topBar.addView(btnDirection)
        topBar.addView(btnBookmark)
        topBar.addView(tvPageInfo)
        root.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        setContentView(root)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tvPageInfo.text = "${position + 1}/${pages.size}"
            }
        })

        val lastUri = getSharedPreferences("manga_prefs", Context.MODE_PRIVATE).getString("last_uri", null)
        if (lastUri != null) {
            try { loadFolder(Uri.parse(lastUri)) } catch (_: Exception) {}
        }
    }

    private fun loadFolder(uri: Uri) {
        val docDir = DocumentFile.fromTreeUri(this, uri) ?: return
        currentFolderUri = uri.toString()
        getSharedPreferences("manga_prefs", Context.MODE_PRIVATE).edit().putString("last_uri", currentFolderUri).apply()

        val imageFiles = mutableListOf<DocumentFile>()
        fun collectImages(dir: DocumentFile) {
            dir.listFiles().sortedWith(NaturalComparator).forEach { file ->
                if (file.isDirectory) {
                    collectImages(file)
                } else if (file.type?.startsWith("image/") == true) {
                    imageFiles.add(file)
                }
            }
        }
        collectImages(docDir)
        pages = imageFiles

        viewPager.adapter = MangaAdapter(pages) {
            topBar.visibility = if (topBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        val savedPage = getSharedPreferences("manga_prefs", Context.MODE_PRIVATE).getInt(currentFolderUri, 0)
        if (savedPage in pages.indices) {
            viewPager.setCurrentItem(savedPage, false)
        }
        tvPageInfo.text = if (pages.isEmpty()) "Nessuna immagine" else "${viewPager.currentItem + 1}/${pages.size}"
    }
}

object NaturalComparator : Comparator<DocumentFile> {
    private val regex = Regex("(\\d+|\\D+)")
    override fun compare(a: DocumentFile, b: DocumentFile): Int {
        val n1 = a.name ?: ""
        val n2 = b.name ?: ""
        val parts1 = regex.findAll(n1).map { it.value }.toList()
        val parts2 = regex.findAll(n2).map { it.value }.toList()
        for (i in 0 until minOf(parts1.size, parts2.size)) {
            val p1 = parts1[i]
            val p2 = parts2[i]
            val num1 = p1.toLongOrNull()
            val num2 = p2.toLongOrNull()
            val cmp = if (num1 != null && num2 != null) num1.compareTo(num2) else p1.compareTo(p2, ignoreCase = true)
            if (cmp != 0) return cmp
        }
        return parts1.size.compareTo(parts2.size)
    }
}

class MangaAdapter(
    private val items: List<DocumentFile>,
    private val onSingleTap: () -> Unit
) : RecyclerView.Adapter<MangaAdapter.PageVH>() {

    class PageVH(val view: MagnifierImageView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val iv = MagnifierImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.FIT_CENTER
            onSingleTapListener = onSingleTap
        }
        return PageVH(iv)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        holder.view.resetMagnifier()
        holder.view.load(items[position].uri)
    }

    override fun getItemCount() = items.size
}

class MagnifierImageView(context: Context) : androidx.appcompat.widget.AppCompatImageView(context) {
    var onSingleTapListener: (() -> Unit)? = null
    private var showMagnifier = false
    private var touchX = 0f
    private var touchY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val matrixZoom = Matrix()

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (showMagnifier) {
                showMagnifier = false
                invalidate()
            } else {
                onSingleTapListener?.invoke()
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            showMagnifier = !showMagnifier
            touchX = e.x
            touchY = e.y
            parent?.requestDisallowInterceptTouchEvent(showMagnifier)
            invalidate()
            return true
        }
    })

    fun resetMagnifier() {
        showMagnifier = false
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        if (showMagnifier) {
            parent?.requestDisallowInterceptTouchEvent(true)
            if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
                touchX = event.x
                touchY = event.y
                invalidate()
            }
            return true
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showMagnifier || width == 0 || height == 0) return

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(bitmap)
        super.onDraw(tempCanvas)

        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val zoom = 2.5f
        val radius = 260f
        matrixZoom.reset()
        matrixZoom.setScale(zoom, zoom, touchX, touchY)
        shader.setLocalMatrix(matrixZoom)
        paint.shader = shader

        canvas.drawCircle(touchX, touchY, radius, paint)
        canvas.drawCircle(touchX, touchY, radius, borderPaint)
    }
}
