package org.unrealvoodoo.simpwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.net.Uri
import android.os.*
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.util.Log
import android.view.*
import android.widget.Toast
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.util.EventLogger
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.*


class MyWatchFace : CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine {
        val engine = Engine()

        // Hackety hack hack: prevent the watch face engine from touching the surface, since we need
        // to pass it onto Exoplayer.
        val f1: Field = engine.javaClass.superclass.getDeclaredField("mDestroyed")
        f1.isAccessible = true
        f1.setBoolean(engine, true)
        return engine
    }

    private class EngineHandler(reference: MyWatchFace.Engine) : Handler(Looper.myLooper()!!) {
        private val mWeakReference: WeakReference<MyWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    // MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {

        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        private lateinit var mPlayer: ExoPlayer

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@MyWatchFace)
                    .setHideStatusBar(true)
                    .setHideNotificationIndicator(true)
                    .setShowUnreadCountIndicator(false)
                    .setAcceptsTapEvents(true)
                    .build())

            mCalendar = Calendar.getInstance()

            val displaySize = Point()
            val display: Display = (getSystemService(WINDOW_SERVICE) as WindowManager)
                .defaultDisplay
            display.getSize(displaySize)

            val renderersFactory = DefaultRenderersFactory(baseContext)
            renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            mPlayer = ExoPlayer.Builder(baseContext, renderersFactory).build()
            mPlayer.addAnalyticsListener(EventLogger())
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                "WatchFaceWakelockTag"
            )
            mPlayer.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        wakeLock.acquire()
                    } else {
                        wakeLock.release()
                    }
                }
            })
            mPlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            playRandomVideo()
        }

        private fun playRandomVideo() {
            val directory: File = File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "Movies")
            if (directory.exists()) {
                val files: Array<File>? = directory.listFiles()
                if (files == null) {
                    Log.e("SimpWatch", "Unable to list media files. Is external storage access permission granted?")
                    Toast.makeText(applicationContext, "No external media permission", Toast.LENGTH_SHORT).show()
                    return
                }
                if (files.isEmpty()) {
                    Log.e("SimpWatch", "No media files.")
                    Toast.makeText(applicationContext, "No media files", Toast.LENGTH_SHORT).show()
                    return
                }
                val file = files.random()
                val mediaItem: MediaItem = MediaItem.fromUri(Uri.parse(file.path))
                mPlayer.setMediaItem(mediaItem)
                mPlayer.prepare()
                mPlayer.play()
            }
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                    WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(
                    WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            mPlayer.setVideoSurfaceHolder(holder)
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP ->
                    // The user has completed the tap gesture.
                    playRandomVideo()
            }
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            Log.i("SimpWatch", "OnVisibilityChanged: " + visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren"t visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                // invalidate()
                mPlayer.play()
            } else {
                unregisterReceiver()
                mPlayer.pause()
            }
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }
    }
}