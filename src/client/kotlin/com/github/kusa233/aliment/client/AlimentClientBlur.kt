package com.github.kusa233.aliment.client

import com.github.kusa233.aliment.physiology.AlimentAttachments
import com.github.kusa233.aliment.physiology.AlimentClientState
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/**
 * Client half of the mandrake blur.
 *
 * The server decides (see `Physiology.isVisionBlurred`) and publishes the answer in the synced client
 * state; this only remembers it for the fog mixin, which runs on the render thread and must not walk
 * the attachment every frame.
 *
 * The blur has two halves, and this is the half the server cannot draw: the post effect is requested
 * as an ordinary screen effect, but fog is a rendering decision, so the player's view is closed in to
 * `AlimentData.ANTICHOLINERGIC_BLUR_DISTANCE` blocks by `FogRendererMixin` here.
 */
object AlimentClientBlur {

    @Volatile
    private var blurred = false

    fun initialize() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register
            val state = player.getAttachedOrElse(AlimentAttachments.CLIENT, AlimentClientState.INACTIVE)
            this.blurred = state.blurred
        }
    }

    /** True while the player's sight is blurred; read from the render thread. */
    @JvmStatic
    fun isBlurred(): Boolean = this.blurred
}
