package io.github.brainyjongmin.forestboardfriends

import android.content.Context
import android.media.*
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlin.random.Random

/** Preloaded short samples: the input callback only calls play(), never generates audio. */
internal class MoleAudio(context:Context, private val musicVolume:Float, private val effectsVolume:Float) {
    private val pool=SoundPool.Builder().setMaxStreams(6).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build()).build()
    private val loaded=mutableSetOf<Int>()
    private val samples=mutableMapOf<String,Int>()
    private var music:AudioTrack?=null
    init {
        pool.setOnLoadCompleteListener{_,id,status->if(status==0)loaded+=id}
        listOf("pop","hit","helmet","gold","miss","win","end").forEachIndexed { index,key ->
            val file=File(context.cacheDir,"mole-v11-$key.wav")
            file.writeBytes(MolePcm.wav(MolePcm.effect(index)))
            samples[key]=pool.load(file.absolutePath,1)
        }
        val pcm=MolePcm.music()
        music=runCatching { AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(MolePcm.RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size*2).build().apply{
                write(pcm,0,pcm.size);setLoopPoints(0,pcm.size,-1);setVolume(musicVolume*.24f)
            }
        }.getOrNull()
    }
    fun play(key:String, rate:Float=1f) {val id=samples[key]?:return;if(id in loaded && effectsVolume>0)pool.play(id,effectsVolume,effectsVolume,1,0,rate.coerceIn(.7f,1.5f))}
    fun running(active:Boolean) {runCatching{if(active && musicVolume>0)music?.play()else music?.pause()}}
    fun close() {pool.release();music?.release();music=null}
}

internal fun moleHaptic(view:View,type:MoleType,enabled:Boolean) {
    if(!enabled || !view.isHapticFeedbackEnabled || Settings.System.getInt(view.context.contentResolver,Settings.System.HAPTIC_FEEDBACK_ENABLED,1)==0)return
    val constant=when(type){MoleType.NORMAL->HapticFeedbackConstants.KEYBOARD_TAP;MoleType.HELMET,MoleType.RABBIT->HapticFeedbackConstants.CONTEXT_CLICK;MoleType.GOLD->HapticFeedbackConstants.CONFIRM}
    if(view.performHapticFeedback(constant))return
    val vibrator=view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator?:return
    if(vibrator.hasVibrator())runCatching{
        vibrator.cancel()
        vibrator.vibrate(VibrationEffect.createOneShot(if(type==MoleType.NORMAL)15 else 25,if(vibrator.hasAmplitudeControl())85 else VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

internal object MolePcm {
    const val RATE=22_050
    fun effect(kind:Int):ShortArray {
        val duration=if(kind>=5).85 else if(kind==3).32 else .14
        val random=Random(kind)
        return ShortArray((duration*RATE).toInt()){i->
            val t=i.toDouble()/RATE
            val noise=random.nextDouble(-1.0,1.0)
            val signal=when(kind){
                0->sin(2*PI*(280*t+1100*t*t))*exp(-t*28)
                1->(sin(2*PI*240*t)*.7+sin(2*PI*620*t)*.3+noise*.45)*exp(-t*35)
                2->(sin(2*PI*900*t)+sin(2*PI*1390*t)*.35+noise*.12)*exp(-t*32)
                3->(sin(2*PI*880*t)+sin(2*PI*1320*t)*.45)*exp(-t*12)
                4->sin(2*PI*(180*t-220*t*t))*exp(-t*30)*.45
                else->{val n=(t/.14).toInt();val f=if(kind==5)doubleArrayOf(523.25,659.25,783.99,1046.5,783.99,1046.5)[n.coerceAtMost(5)]else doubleArrayOf(440.0,392.0,329.63,293.66,261.63,261.63)[n.coerceAtMost(5)];sin(2*PI*f*t)*exp(-(t%.14)*12)}
            }
            (signal*15_000).toInt().coerceIn(-30_000,30_000).toShort()
        }
    }
    fun music():ShortArray {
        val melody=intArrayOf(72,76,79,76,74,77,81,77,71,74,79,74,72,76,79,84,81,79,76,72,77,81,84,81,79,76,74,71,72,79,76,72)
        val roots=intArrayOf(48,50,43,48,45,53,43,48)
        val stepTime=.19
        val random=Random(45)
        return ShortArray((melody.size*stepTime*RATE).toInt()){i->
            val t=i.toDouble()/RATE;val step=(t/stepTime).toInt().coerceAtMost(melody.lastIndex);val local=t%stepTime
            val f=440*2.0.pow((melody[step]-69)/12.0);val bass=440*2.0.pow((roots[step/4]-69)/12.0)
            val bell=(sin(2*PI*f*t)+.25*sin(4*PI*f*t))*exp(-local*8)
            val chord=(sin(4*PI*bass*t)+sin(6*PI*bass*t)+sin(8*PI*bass*t))*.12
            val kick=sin(2*PI*(90*local-180*local*local))*exp(-local*35)
            val snare=random.nextDouble(-1.0,1.0)*exp(-local*65)*(if(step%4==2).3 else .06)
            ((bell*.45+chord+sin(2*PI*bass*t)*.23+kick*.25+snare)*9000).toInt().toShort()
        }
    }
    fun wav(pcm:ShortArray):ByteArray=ByteBuffer.allocate(44+pcm.size*2).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray());putInt(36+pcm.size*2);put("WAVEfmt ".toByteArray());putInt(16);putShort(1);putShort(1)
        putInt(RATE);putInt(RATE*2);putShort(2);putShort(16);put("data".toByteArray());putInt(pcm.size*2);pcm.forEach{putShort(it)}
    }.array()
}
