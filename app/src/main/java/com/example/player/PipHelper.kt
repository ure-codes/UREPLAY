package com.example.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import com.example.R

object PipHelper {
    const val ACTION_PIP_CONTROL = "com.aistudio.veloplay.PIP_CONTROL"
    const val EXTRA_CONTROL_TYPE = "control_type"
    const val CONTROL_REWIND = 1
    const val CONTROL_PLAY_PAUSE = 2
    const val CONTROL_FORWARD = 3

    fun Context.findActivity(): ComponentActivity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is ComponentActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildPipParams(
        context: Context,
        isPlaying: Boolean,
        videoWidth: Int,
        videoHeight: Int,
        sourceRectHint: Rect?,
        autoEnterEnabled: Boolean
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()

        val aspectRatio = computeSafeAspectRatio(videoWidth, videoHeight)
        builder.setAspectRatio(aspectRatio)

        if (sourceRectHint != null && !sourceRectHint.isEmpty) {
            builder.setSourceRectHint(sourceRectHint)
        }

        builder.setActions(buildRemoteActions(context, isPlaying))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnterEnabled)
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }

    private fun computeSafeAspectRatio(width: Int, height: Int): Rational {
        if (width <= 0 || height <= 0) return Rational(16, 9)
        val ratio = width.toFloat() / height.toFloat()
        // Android PiP strictly requires aspect ratio between 1:2.39 and 2.39:1
        return when {
            ratio > 2.35f -> Rational(235, 100)
            ratio < (1f / 2.35f) -> Rational(100, 235)
            else -> Rational(width, height)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildRemoteActions(context: Context, isPlaying: Boolean): List<RemoteAction> {
        val rewindIntent = createActionPendingIntent(context, CONTROL_REWIND, 101)
        val playPauseIntent = createActionPendingIntent(context, CONTROL_PLAY_PAUSE, 102)
        val forwardIntent = createActionPendingIntent(context, CONTROL_FORWARD, 103)

        val rewindAction = RemoteAction(
            Icon.createWithResource(context, android.R.drawable.ic_media_rew),
            context.getString(R.string.action_rewind_10),
            context.getString(R.string.action_rewind_10),
            rewindIntent
        )
        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseLabel = if (isPlaying) {
            context.getString(R.string.action_pause)
        } else {
            context.getString(R.string.action_play)
        }
        val playPauseAction = RemoteAction(
            Icon.createWithResource(context, playPauseIcon),
            playPauseLabel,
            playPauseLabel,
            playPauseIntent
        )
        val forwardAction = RemoteAction(
            Icon.createWithResource(context, android.R.drawable.ic_media_ff),
            context.getString(R.string.action_forward_10),
            context.getString(R.string.action_forward_10),
            forwardIntent
        )
        return listOf(rewindAction, playPauseAction, forwardAction)
    }

    private fun createActionPendingIntent(
        context: Context,
        controlType: Int,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(ACTION_PIP_CONTROL).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_CONTROL_TYPE, controlType)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
