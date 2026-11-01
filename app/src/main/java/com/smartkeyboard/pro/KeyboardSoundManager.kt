package com.smartkeyboard.pro

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

data class SoundProfileOption(
    val id: String,
    val name: String,
    val description: String,
    val icon: String
)

object KeyboardSoundManager {

    val availableProfiles = listOf(
        SoundProfileOption(
            id = "laptop",
            name = "Laptop Chiclet",
            description = "Suara keyboard laptop asli (empuk, renyah & taktil)",
            icon = "💻"
        ),
        SoundProfileOption(
            id = "mechanical",
            name = "Mechanical Thock",
            description = "Keyboard mechanical switch (klik solid & mantap)",
            icon = "⌨️"
        ),
        SoundProfileOption(
            id = "typewriter",
            name = "Mesin Tik Retro",
            description = "Sensasi ketikan mesin tik klasik tempo dulu",
            icon = "📜"
        ),
        SoundProfileOption(
            id = "bubble",
            name = "Bubble Pop",
            description = "Efek pop gelembung air yang lucu & lembut",
            icon = "🫧"
        ),
        SoundProfileOption(
            id = "wood",
            name = "Wood Block",
            description = "Ketukan balok kayu akustik organik minimalis",
            icon = "🪵"
        ),
        SoundProfileOption(
            id = "system",
            name = "Android Default",
            description = "Suara klik bawaan sistem perangkat Android",
            icon = "📱"
        )
    )

    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<String, Int>()
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized && soundPool != null) return

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(audioAttributes)
                .build()

            val soundDir = File(context.filesDir, "keyboard_sounds").apply { mkdirs() }

            // Generate synthesis files if missing
            val profilesToGenerate = listOf("laptop", "mechanical", "typewriter", "bubble", "wood")
            for (profile in profilesToGenerate) {
                // Key sound
                val keyFile = File(soundDir, "${profile}_key.wav")
                if (!keyFile.exists() || keyFile.length() < 100) {
                    generateWavFile(keyFile, profile, isDelete = false)
                }
                val keyId = soundPool?.load(keyFile.absolutePath, 1) ?: 0
                if (keyId != 0) soundMap["${profile}_key"] = keyId

                // Delete sound
                val deleteFile = File(soundDir, "${profile}_delete.wav")
                if (!deleteFile.exists() || deleteFile.length() < 100) {
                    generateWavFile(deleteFile, profile, isDelete = true)
                }
                val deleteId = soundPool?.load(deleteFile.absolutePath, 1) ?: 0
                if (deleteId != 0) soundMap["${profile}_delete"] = deleteId
            }

            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playKeySound(
        profile: String,
        isDelete: Boolean = false,
        audioManager: AudioManager? = null,
        volume: Float = 0.85f
    ) {
        if (profile == "system") {
            try {
                val effect = if (isDelete) AudioManager.FX_KEYPRESS_DELETE else AudioManager.FX_KEY_CLICK
                audioManager?.playSoundEffect(effect, volume)
            } catch (e: Exception) {
                // Ignore
            }
            return
        }

        val pool = soundPool ?: return
        val soundKey = if (isDelete) "${profile}_delete" else "${profile}_key"
        val soundId = soundMap[soundKey] ?: soundMap["${profile}_key"] ?: return
        if (soundId == 0) return

        try {
            // Subtle random pitch variation (0.97x - 1.03x) for organic physical key feel
            val pitch = 0.97f + (Random.nextFloat() * 0.06f)
            pool.play(soundId, volume, volume, 1, 0, pitch)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateWavFile(file: File, profile: String, isDelete: Boolean) {
        val sampleRate = 44100
        val samples: ShortArray = when (profile) {
            "laptop" -> synthesizeLaptopSound(sampleRate, isDelete)
            "mechanical" -> synthesizeMechanicalSound(sampleRate, isDelete)
            "typewriter" -> synthesizeTypewriterSound(sampleRate, isDelete)
            "bubble" -> synthesizeBubbleSound(sampleRate, isDelete)
            "wood" -> synthesizeWoodSound(sampleRate, isDelete)
            else -> synthesizeLaptopSound(sampleRate, isDelete)
        }
        writePcmWav(file, sampleRate, samples)
    }

    /**
     * Ultra-realistic Laptop Chiclet Key (Scissor Switch bottom-out sound)
     * Combines a high-frequency transient tactile click with chassis bottom-out resonance.
     */
    private fun synthesizeLaptopSound(sampleRate: Int, isDelete: Boolean): ShortArray {
        val durationSec = if (isDelete) 0.038f else 0.032f
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)

        val snapFreq = if (isDelete) 2200.0 else 2850.0
        val thumpFreq = if (isDelete) 165.0 else 215.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // High frequency scissor tactile snap (decays within ~12ms)
            val snapDecay = exp(-t * 260.0)
            val noise = (Random.nextDouble() * 2.0 - 1.0) * 0.35
            val snapTone = sin(2.0 * PI * snapFreq * t) * 0.65
            val snap = (snapTone + noise) * snapDecay

            // Low frequency bottom-out plastic chassis thump (decays within ~25ms)
            val thumpDecay = exp(-t * 130.0)
            val thump = sin(2.0 * PI * thumpFreq * t) * thumpDecay * 0.7

            // Soft master envelope to prevent clicking
            val attack = (i.toDouble() / 15.0).coerceAtMost(1.0)
            val total = (snap * 0.65 + thump * 0.55) * attack
            val sampleVal = (total * 27000.0).coerceIn(-32767.0, 32767.0).toInt().toShort()
            samples[i] = sampleVal
        }
        return samples
    }

    /**
     * Mechanical Keyboard (Thock & Clicky Switch)
     * Sharp metal leaf impact transient followed by a deep hollow keycap reverberation.
     */
    private fun synthesizeMechanicalSound(sampleRate: Int, isDelete: Boolean): ShortArray {
        val durationSec = if (isDelete) 0.048f else 0.042f
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)

        val baseThock = if (isDelete) 220.0 else 320.0
        val clickFreq = if (isDelete) 2900.0 else 3600.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Crisp switch contact leaf click
            val clickDecay = exp(-t * 310.0)
            val clickNoise = (Random.nextDouble() * 2.0 - 1.0) * 0.4
            val click = (sin(2.0 * PI * clickFreq * t) * 0.6 + clickNoise) * clickDecay

            // Deep mechanical thock resonance
            val sweepFreq = baseThock * (1.0 - t * 6.0).coerceAtLeast(0.6)
            val thockDecay = exp(-t * 90.0)
            val thock = sin(2.0 * PI * sweepFreq * t) * thockDecay * 0.85

            val attack = (i.toDouble() / 12.0).coerceAtMost(1.0)
            val total = (click * 0.75 + thock * 0.65) * attack
            samples[i] = (total * 28000.0).coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return samples
    }

    /**
     * Vintage Typewriter Strike & Chassis Clack
     */
    private fun synthesizeTypewriterSound(sampleRate: Int, isDelete: Boolean): ShortArray {
        val durationSec = if (isDelete) 0.055f else 0.048f
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)

        val strikeFreq = if (isDelete) 1400.0 else 1900.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Metallic impact clatter
            val strikeDecay = exp(-t * 220.0)
            val metalPing = (sin(2.0 * PI * strikeFreq * t) + 0.4 * sin(2.0 * PI * (strikeFreq * 2.1) * t)) * strikeDecay
            val impactNoise = (Random.nextDouble() * 2.0 - 1.0) * 0.5 * exp(-t * 380.0)

            // Heavy carriage chassis thud
            val chassisThud = sin(2.0 * PI * 130.0 * t) * exp(-t * 70.0) * 0.6

            val attack = (i.toDouble() / 10.0).coerceAtMost(1.0)
            val total = (metalPing * 0.6 + impactNoise * 0.5 + chassisThud * 0.5) * attack
            samples[i] = (total * 29000.0).coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return samples
    }

    /**
     * Playful Bubble Pop Sound
     */
    private fun synthesizeBubbleSound(sampleRate: Int, isDelete: Boolean): ShortArray {
        val durationSec = if (isDelete) 0.040f else 0.034f
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)

        val startFreq = if (isDelete) 320.0 else 440.0
        val endFreq = if (isDelete) 820.0 else 1150.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = (t / durationSec).coerceIn(0.0, 1.0)
            val currentFreq = startFreq + (endFreq - startFreq) * progress * progress
            val decay = exp(-t * 110.0)
            val pop = sin(2.0 * PI * currentFreq * t) * decay

            val attack = (i.toDouble() / 20.0).coerceAtMost(1.0)
            samples[i] = (pop * attack * 28000.0).coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return samples
    }

    /**
     * Organic Wood Block / Bamboo Percussion
     */
    private fun synthesizeWoodSound(sampleRate: Int, isDelete: Boolean): ShortArray {
        val durationSec = if (isDelete) 0.038f else 0.030f
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)

        val fundamental = if (isDelete) 680.0 else 880.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val decay = exp(-t * 175.0)
            val h1 = sin(2.0 * PI * fundamental * t) * 0.75
            val h2 = sin(2.0 * PI * (fundamental * 1.95) * t) * 0.35
            val h3 = sin(2.0 * PI * (fundamental * 3.1) * t) * 0.15
            val click = (Random.nextDouble() * 2.0 - 1.0) * 0.3 * exp(-t * 400.0)

            val attack = (i.toDouble() / 10.0).coerceAtMost(1.0)
            val total = (h1 + h2 + h3 + click) * decay * attack
            samples[i] = (total * 28000.0).coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        return samples
    }

    private fun writePcmWav(file: File, sampleRate: Int, samples: ShortArray) {
        val totalAudioLen = samples.size * 2
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * 2

        val buffer = ByteBuffer.allocate(44 + totalAudioLen).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(totalDataLen)
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // 'fmt ' chunk
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(1) // AudioFormat (1 = PCM)
        buffer.putShort(1) // NumChannels (1 = Mono)
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(2) // BlockAlign
        buffer.putShort(16) // BitsPerSample

        // 'data' chunk
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(totalAudioLen)

        // Write PCM samples
        for (sample in samples) {
            buffer.putShort(sample)
        }

        file.writeBytes(buffer.array())
    }
}
