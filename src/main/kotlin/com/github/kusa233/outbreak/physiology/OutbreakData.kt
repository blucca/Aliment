package com.github.kusa233.outbreak.physiology

import com.github.kusa233.outbreak.registry.Registration
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * Everything Outbreak tracks about a player's body.
 *
 * The values are deliberately unit-less "index" numbers in a fixed range so that the whole system
 * stays readable and tunable:
 *
 * * [inflammation] 0..100 - the immune response. A healthy player drifts back to
 *   [SAFE_INFLAMMATION_LOW]..[SAFE_INFLAMMATION_HIGH]. Below [IMMUNOSUPPRESSION_THRESHOLD] the
 *   immune system gives up and pathogens run away; above [IMMUNE_STORM_THRESHOLD] the response
 *   itself starts hurting the player.
 * * [electrolytes] 0..100 - salt balance. Fever drains it, rest restores it, and a low value
 *   makes every other symptom worse.
 * * [bacteria] and [virus] 0..100 - pathogen load per pathogen family.
 * * [salicin] 0..[SALICIN_CAP] - the drug concentration in the blood. Willow bark soup is the
 *   only source so far.
 *
 * The class is immutable on purpose: every tick produces a new instance, which is trivial to
 * serialise and to compare.
 */
data class OutbreakData(
    val inflammation: Float,
    val electrolytes: Float,
    val bacteria: Float,
    val virus: Float,
    val salicin: Float,
) {

    /** Combined pathogen load, 0..200 in theory, 0..100 in practice. */
    val pathogenLoad: Float
        get() = this.bacteria + this.virus

    /** True once the load is high enough to actually make the player feel ill. */
    val isSymptomatic: Boolean
        get() = this.pathogenLoad >= SYMPTOM_THRESHOLD

    val isImmuneStorm: Boolean
        get() = this.inflammation >= IMMUNE_STORM_THRESHOLD

    val isImmunosuppressed: Boolean
        get() = this.inflammation <= IMMUNOSUPPRESSION_THRESHOLD

    /** 0..1 severity used to scale symptoms; saturates at [SEVERE_LOAD]. */
    val severity: Float
        get() = (this.pathogenLoad / SEVERE_LOAD).coerceIn(0f, 1f)

    companion object {
        const val MIN_INFLAMMATION = 0f
        const val MAX_INFLAMMATION = 100f

        /** The band a healthy player sits in. */
        const val SAFE_INFLAMMATION_LOW = 20f
        const val SAFE_INFLAMMATION_HIGH = 30f
        const val BASELINE_INFLAMMATION = 25f

        /** Below this the immune system stops keeping up with the pathogens. */
        const val IMMUNOSUPPRESSION_THRESHOLD = 12f

        /** Above this the immune response itself becomes the problem. */
        const val IMMUNE_STORM_THRESHOLD = 75f

        const val MIN_ELECTROLYTES = 0f
        const val MAX_ELECTROLYTES = 100f
        const val LOW_ELECTROLYTES = 30f

        const val MAX_PATHOGEN = 100f

        /** Load at which symptoms start showing. */
        const val SYMPTOM_THRESHOLD = 8f

        /** Load at which symptoms are at full strength. */
        const val SEVERE_LOAD = 60f

        /** Drug concentration needed before salicin starts damping inflammation. */
        const val SALICIN_EFFECTIVE = 1f

        /** Highest concentration a player can build up. */
        const val SALICIN_CAP = 3f

        /** Salicin is fully metabolised after three in-game days. */
        const val SALICIN_METABOLISM_TICKS = 72_000
        const val SALICIN_DECAY_PER_TICK = SALICIN_CAP / SALICIN_METABOLISM_TICKS

        /** What a healthy player looks like. */
        val HEALTHY = OutbreakData(
            inflammation = BASELINE_INFLAMMATION,
            electrolytes = MAX_ELECTROLYTES,
            bacteria = 0f,
            virus = 0f,
            salicin = 0f,
        )

        val CODEC: Codec<OutbreakData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.FLOAT.fieldOf("inflammation").forGetter(OutbreakData::inflammation),
                Codec.FLOAT.fieldOf("electrolytes").forGetter(OutbreakData::electrolytes),
                Codec.FLOAT.fieldOf("bacteria").forGetter(OutbreakData::bacteria),
                Codec.FLOAT.fieldOf("virus").forGetter(OutbreakData::virus),
                Codec.FLOAT.fieldOf("salicin").forGetter(OutbreakData::salicin),
            ).apply(instance, ::OutbreakData)
        }
    }
}

/**
 * The only part of the physiology the client needs: a counter that ticks up whenever the server
 * decides the player should shake, plus how hard. Sending a counter instead of a countdown keeps
 * the traffic to one packet per shake event.
 */
data class OutbreakShakeState(val sequence: Int, val amplitude: Float) {
    companion object {
        val INACTIVE = OutbreakShakeState(0, 0f)

        val CODEC: Codec<OutbreakShakeState> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("sequence").forGetter(OutbreakShakeState::sequence),
                Codec.FLOAT.fieldOf("amplitude").forGetter(OutbreakShakeState::amplitude),
            ).apply(instance, ::OutbreakShakeState)
        }

        val STREAM_CODEC: StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, OutbreakShakeState> =
            StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                OutbreakShakeState::sequence,
                ByteBufCodecs.FLOAT,
                OutbreakShakeState::amplitude,
                ::OutbreakShakeState,
            )
    }
}

object OutbreakAttachments {

    /**
     * The full physiology. Persistent and server-authoritative, so it is never synced: the client
     * only ever needs [SHAKE].
     */
    val DATA: AttachmentType<OutbreakData> = AttachmentRegistry.create(Registration.id("physiology")) { builder ->
        builder
            .persistent(OutbreakData.CODEC)
            .copyOnDeath()
            .initializer { OutbreakData.HEALTHY }
    }

    /** Synced to every client that can see the player so the camera shake can be rendered. */
    val SHAKE: AttachmentType<OutbreakShakeState> = AttachmentRegistry.create(Registration.id("shake")) { builder ->
        builder
            .persistent(OutbreakShakeState.CODEC)
            .initializer { OutbreakShakeState.INACTIVE }
            .syncWith(OutbreakShakeState.STREAM_CODEC, AttachmentSyncPredicate.all())
    }

    /**
     * Per-player timers that only matter while the player is online, so they are neither saved nor
     * synced.
     */
    val RUNTIME: AttachmentType<OutbreakRuntime> = AttachmentRegistry.create(Registration.id("runtime"))

    fun initialize() {
        // Registration happens in the property initialisers above.
    }
}

/** Short lived counters; deliberately mutable and never serialised. */
class OutbreakRuntime {
    /** Ticks until the next "will the view shake?" roll. */
    var shakeCooldown: Int = 0

    /** Ticks during which touching a bat cannot roll an infection again. */
    var batCooldown: Int = 0
}
