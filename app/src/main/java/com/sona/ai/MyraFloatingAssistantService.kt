package com.sona.ai

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * User-started floating MYRA orb. It only runs after Android's overlay permission is granted.
 * Tapping the orb opens MYRA; dragging the orb repositions it; the close control dismisses it.
 */
class MyraFloatingAssistantService : Service() {
    private var windowManager: WindowManager? = null
    private var overlay: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlay == null) showOverlay()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = FrameLayout(this)
        val web = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            addJavascriptInterface(OrbBridge(), "Myra")
            webViewClient = WebViewClient()
            loadDataWithBaseURL(null, HTML, "text/html", "UTF-8", null)
        }
        root.addView(web, FrameLayout.LayoutParams(176, 176))
        val layout = WindowManager.LayoutParams(
            176, 176,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 220
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = layout.x; startY = layout.y; moved = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    if (moved) {
                        layout.x = (startX - dx).coerceAtLeast(0)
                        layout.y = (startY + dy).coerceAtLeast(0)
                        try { windowManager?.updateViewLayout(root, layout) } catch (_: Exception) { }
                    }
                    moved
                }
                else -> moved
            }
        }
        overlay = root
        params = layout
        try {
            windowManager?.addView(root, layout)
        } catch (_: Exception) {
            overlay = null
            stopSelf()
        }
    }

    private inner class OrbBridge {
        @JavascriptInterface fun openApp() {
            val launch = Intent(this@MyraFloatingAssistantService, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(launch)
        }
        @JavascriptInterface fun closeOrb() { stopSelf() }
    }

    override fun onDestroy() {
        try { overlay?.let { windowManager?.removeView(it) } } catch (_: Exception) { }
        overlay = null
        windowManager = null
        super.onDestroy()
    }

    private companion object {
        val HTML = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
            <style>
            *{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent;font-family:Arial,sans-serif}
            .stage{width:176px;height:176px;position:relative;display:flex;align-items:center;justify-content:center}
            .ring{position:absolute;border-radius:50%;border:1px solid #b46cff66;inset:8px;animation:spin 9s linear infinite}
            .ring.two{inset:18px;border-color:#ff6fb588;animation-direction:reverse;animation-duration:6s}
            .orb{width:112px;height:112px;border-radius:50%;border:2px solid #ff6fb5;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#fff;background:radial-gradient(circle at 35% 25%,#51316f,#15142b 55%,#070a16);box-shadow:0 0 18px #b46cff88,inset 0 0 18px #ff6fb544;cursor:pointer;animation:pulse 2s ease-in-out infinite}
            .logo{font-size:27px;font-weight:900;letter-spacing:1px}.label{font-size:8px;letter-spacing:2px;color:#e8d7ff;margin-top:3px}
            .close{position:absolute;right:5px;top:5px;border:1px solid #b46cff99;border-radius:50%;width:25px;height:25px;color:#fff;background:#11152bee;font-size:16px;line-height:20px}
            @keyframes pulse{50%{transform:scale(1.04);box-shadow:0 0 26px #ff6fb599,inset 0 0 20px #b46cff77}}
            @keyframes spin{to{transform:rotate(360deg)}}
            </style></head><body><div class="stage"><div class="ring"></div><div class="ring two"></div>
            <button class="close" onclick="Myra.closeOrb()">×</button>
            <div class="orb" onclick="Myra.openApp()"><div class="logo">MYRA</div><div class="label">AI COMPANION</div></div>
            </div></body></html>
        """.trimIndent()
    }
}
