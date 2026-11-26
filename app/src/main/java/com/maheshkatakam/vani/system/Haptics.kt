package com.maheshkatakam.vani.system

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.maheshkatakam.vani.Vani

/** Small vibrations so you can use Vani without looking (start, stop, done, error). */
object Haptics {
    fun start(ctx: Context) = play(ctx, VibrationEffect.EFFECT_HEAVY_CLICK)
    fun stop(ctx: Context) = play(ctx, VibrationEffect.EFFECT_DOUBLE_CLICK)
    fun success(ctx: Context) = play(ctx, VibrationEffect.EFFECT_TICK)
    fun error(ctx: Context) = play(ctx, VibrationEffect.EFFECT_DOUBLE_CLICK)

    private fun play(ctx: Context, effect: Int) {
        if (!Vani.settings.current.haptics) return
        runCatching {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createPredefined(effect))
        }
    }
}
