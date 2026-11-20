package com.nowbar.combo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun send(action: String, block: Intent.() -> Unit = {}) {
        startForegroundService(Intent(this, ComboService::class.java).setAction(action).apply(block))
    }

    private fun btn(t: String, a: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setOnClickListener { a() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply {
            addView(root)
            setOnApplyWindowInsetsListener { v, ins ->
                val b = ins.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(b.left, b.top, b.right, b.bottom)
                ins
            }
        }
        root.addView(TextView(this).apply {
            text = "Now Bar Combo"; textSize = 20f
        })
        root.addView(TextView(this).apply {
            text = "Aynı sayaç iki yerde: kilit ekranı/Now Bar medya kartı ve her uygulamanın üstünde yüzen çubuk."
            textSize = 13f; setPadding(0, dp(6), 0, dp(10))
        })
        status = TextView(this).apply { textSize = 14f; setPadding(0, 0, 0, dp(10)) }
        root.addView(status)

        root.addView(btn("1) Yüzen çubuk izni ver") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        root.addView(btn("2) Başlat: 25 dk") { send("start") { putExtra("ms", 25 * 60_000L) } })
        root.addView(btn("Başlat: 1 dk (test)") { send("start") { putExtra("ms", 60_000L) } })
        root.addView(btn("Yüzen çubuğu aç / kapat") { send("t_overlay") })
        root.addView(btn("Durdur") { stopService(Intent(this, ComboService::class.java)) })

        root.addView(TextView(this).apply {
            text = "\nİpuçları:\n• Yüzen çubuğa dokun: tuşları gizle/göster\n• Sürükle: yerini değiştir (ekran dışına çıkmaz)\n• Telefonu kilitle: Now Bar'da medya kartı görünür\n• Karttaki ileri/geri ±60 sn, sağdaki + süreye 1 dk ekler"
            textSize = 13f
        })
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (Settings.canDrawOverlays(this))
            "Yüzen çubuk izni: AÇIK ✔" else "Yüzen çubuk izni: KAPALI — önce 1. butona bas"
    }
}
