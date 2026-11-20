package com.nowbar.combo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Yüzen çubuğun ilerleme çizgisi. */
class BarView(c: Context) : View(c) {
    var fraction = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val h = height.toFloat()
        p.color = Color.parseColor("#44FFFFFF")
        cv.drawRoundRect(0f, 0f, width.toFloat(), h, h / 2, h / 2, p)
        p.color = Color.parseColor("#FF6A00")
        cv.drawRoundRect(0f, 0f, width * fraction.coerceIn(0f, 1f), h, h / 2, h / 2, p)
    }
}

class ComboService : Service() {

    private val channelId = "combo"
    private val notifId = 9
    private val h = Handler(Looper.getMainLooper())
    private lateinit var nm: NotificationManager
    private lateinit var session: MediaSession
    private lateinit var wm: WindowManager
    private var inited = false
    private var fg = false

    // --- Zamanlayıcı durumu (iki yüzey de buradan beslenir) ---
    private var durationMs = 25 * 60_000L
    private var posBase = 0L
    private var baseTime = 0L
    private var playing = true

    private fun now() = SystemClock.elapsedRealtime()
    private fun pos(): Long {
        val p = if (playing) posBase + (now() - baseTime) else posBase
        return p.coerceIn(0L, durationMs)
    }
    private fun mmss(ms: Long): String {
        val s = (ms.coerceAtLeast(0L) + 999) / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }
    private fun finished() = pos() >= durationMs
    private fun titleText() = if (finished()) "Bitti" else "Kalan " + mmss(durationMs - pos())

    private fun setPlaying(p: Boolean) {
        if (finished() && p) return
        posBase = pos(); baseTime = now(); playing = p; publish()
    }

    private fun seekTo(p: Long) {
        posBase = p.coerceIn(0L, durationMs); baseTime = now(); publish()
    }

    private val cb = object : MediaSession.Callback() {
        override fun onPlay() = setPlaying(true)
        override fun onPause() = setPlaying(false)
        override fun onSkipToNext() = seekTo(pos() + 60_000)
        override fun onSkipToPrevious() = seekTo(pos() - 60_000)
        override fun onSeekTo(pos: Long) = seekTo(pos)
        override fun onCustomAction(action: String, extras: Bundle?) {
            when (action) {
                "reset" -> { posBase = 0; baseTime = now(); playing = true; publish() }
                "add1" -> { durationMs += 60_000; publish() }
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!inited) return
            if (playing && finished()) {
                posBase = durationMs; baseTime = now(); playing = false
            }
            publish()
            h.postDelayed(this, 1000)
        }
    }

    // --- Yüzen çubuk ---
    private var root: LinearLayout? = null
    private var timeTv: TextView? = null
    private var playTv: TextView? = null
    private var btnRow: LinearLayout? = null
    private var bar: BarView? = null
    private var expanded = true
    private lateinit var lp: WindowManager.LayoutParams

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun clamp() {
        val r = root ?: return
        val b = wm.currentWindowMetrics.bounds
        val w = if (r.width > 0) r.width else dp(220)
        val hh = if (r.height > 0) r.height else dp(60)
        lp.x = lp.x.coerceIn(0, (b.width() - w).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (b.height() - hh).coerceAtLeast(0))
    }

    private fun chip(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(5), dp(10), dp(5))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#33FFFFFF"))
        }
        setOnClickListener { onClick() }
    }

    private fun showOverlay() {
        if (root != null || !Settings.canDrawOverlays(this)) return
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat(); setColor(Color.parseColor("#F2161618"))
            }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        timeTv = TextView(this).apply {
            textSize = 20f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        }
        top.addView(timeTv)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val lpc = { LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) } }
        playTv = chip("❚❚") { setPlaying(!playing) }
        row.addView(playTv, lpc())
        row.addView(chip("+1 dk") { durationMs += 60_000; publish() }, lpc())
        row.addView(chip("↺") { posBase = 0; baseTime = now(); playing = true; publish() }, lpc())
        top.addView(row, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(10) })
        btnRow = row
        bar = BarView(this)
        r.addView(top)
        r.addView(bar, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(8) })

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(16); y = dp(140) }

        var sx = 0; var sy = 0; var dx = 0f; var dy = 0f; var moved = false
        r.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; dx = e.rawX; dy = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val mx = e.rawX - dx; val my = e.rawY - dy
                    if (abs(mx) > dp(6) || abs(my) > dp(6)) moved = true
                    if (moved) {
                        lp.x = (sx + mx).toInt(); lp.y = (sy + my).toInt()
                        clamp(); wm.updateViewLayout(r, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        expanded = !expanded
                        btnRow?.visibility = if (expanded) View.VISIBLE else View.GONE
                        r.post { clamp(); wm.updateViewLayout(r, lp) }
                    }
                    true
                }
                else -> false
            }
        }
        try {
            wm.addView(r, lp)
            root = r
        } catch (e: Exception) {
            root = null
        }
    }

    private fun hideOverlay() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
    }

    private fun updateOverlay() {
        timeTv?.text = if (finished()) "Bitti" else mmss(durationMs - pos())
        playTv?.text = if (playing) "❚❚" else "▶"
        bar?.fraction = pos().toFloat() / durationMs
        bar?.invalidate()
    }

    // --- Yaşam döngüsü ---
    private fun ensureInit() {
        if (inited) return
        nm = getSystemService(NotificationManager::class.java)
        wm = getSystemService(WindowManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Now Bar Combo", NotificationManager.IMPORTANCE_DEFAULT)
        )
        session = MediaSession(this, "NowBarCombo")
        session.setCallback(cb)
        session.isActive = true
        baseTime = now()
        inited = true
        showOverlay()
        h.postDelayed(tick, 1000)
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        ensureInit()
        when (i?.action) {
            "start" -> {
                durationMs = i.getLongExtra("ms", 25 * 60_000L)
                posBase = 0; baseTime = now(); playing = true
                showOverlay()
            }
            "t_overlay" -> if (root == null) showOverlay() else hideOverlay()
        }
        publish()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        hideOverlay()
        if (inited) { session.release(); inited = false }
        fg = false
        super.onDestroy()
    }

    // --- Yayın ---
    private fun publish() {
        if (!inited) return
        val art = art()
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, titleText())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Odak sayacı")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                .build()
        )
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    pos(), if (playing) 1f else 0f, now()
                )
                .addCustomAction("reset", "Sıfırla", android.R.drawable.ic_menu_revert)
                .addCustomAction("add1", "+1 dk", android.R.drawable.ic_input_add)
                .build()
        )
        val n = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(titleText())
            .setContentText("Odak sayacı")
            .setLargeIcon(art)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            .build()
        if (!fg) {
            startForeground(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            fg = true
        } else nm.notify(notifId, n)
        updateOverlay()
    }

    private fun art(): Bitmap {
        val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#FF6A00"))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 90f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText(if (finished()) "Bitti" else mmss(durationMs - pos()), 150f, 175f, p)
        return bmp
    }
}
