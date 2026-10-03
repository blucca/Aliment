package com.github.kusa233.aliment.client

import com.github.kusa233.aliment.physiology.AlimentAttachments
import com.github.kusa233.aliment.physiology.AlimentClientState
import com.github.kusa233.aliment.physiology.AlimentSymptoms
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import kotlin.math.PI
import kotlin.math.sin

/**
 * Client half of the camera shake.
 *
 * The server only publishes a counter that goes up once per shake event (see [AlimentShakeState]),
 * so this class turns that into a short, decaying oscillation. The mixin asks for the current
 * offset every frame.
 */
object AlimentClientShake {

    /** Radians per second of the tremor; a few shakes per second reads as a shiver. */
    private const val YAW_FREQUENCY = 26.0
    private const val PITCH_FREQUENCY = 33.0

    /** Peak degrees of camera rotation at amplitude 1. */
    private const val YAW_DEGREES_PER_AMPLITUDE = 1.8
    private const val PITCH_DEGREES_PER_AMPLITUDE = 1.2

    private var ticksRemaining = 0
    private var amplitude = 0f
    private var yawPhase = 0f
    private var pitchPhase = 0f
    private var lastSequence = 0

    fun initialize() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register
            val state = player.getAttachedOrElse(AlimentAttachments.CLIENT, AlimentClientState.INACTIVE)

            if (state.shakeSequence != this.lastSequence) {
                this.lastSequence = state.shakeSequence
                this.amplitude = state.shakeAmplitude
                this.ticksRemaining = AlimentSymptoms.SHAKE_DURATION_TICKS
                val random = player.level().random
                this.yawPhase = random.nextFloat() * (2f * PI.toFloat())
                this.pitchPhase = random.nextFloat() * (2f * PI.toFloat())
            }

            if (this.ticksRemaining > 0) {
                this.ticksRemaining--
            }
        }
    }

    /** Current yaw offset in degrees; 0 when no shake is running. */
    @JvmStatic
    fun yawOffset(): Float = offset(this.yawPhase, YAW_FREQUENCY, YAW_DEGREES_PER_AMPLITUDE)

    /** Current pitch offset in degrees; 0 when no shake is running. */
    @JvmStatic
    fun pitchOffset(): Float = offset(this.pitchPhase, PITCH_FREQUENCY, PITCH_DEGREES_PER_AMPLITUDE)

    private fun offset(phase: Float, frequency: Double, degreesPerAmplitude: Double): Float {
        if (this.ticksRemaining <= 0) {
            return 0f
        }
        // Fade out linearly over the shake so it ends smoothly.
        val envelope = this.ticksRemaining.toFloat() / AlimentSymptoms.SHAKE_DURATION_TICKS
        val seconds = System.currentTimeMillis() / 1000.0
        val wave = sin(phase + seconds * frequency).toFloat()
        return (this.amplitude * envelope * wave * degreesPerAmplitude).toFloat()
    }
}
