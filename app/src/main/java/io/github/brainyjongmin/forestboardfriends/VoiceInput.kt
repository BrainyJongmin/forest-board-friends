package io.github.brainyjongmin.forestboardfriends

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

internal class VoiceInput(private val context:Context,private val recording:(Boolean)->Unit,private val result:(String)->Unit) {
    var active by mutableStateOf(false);private set
    var message by mutableStateOf("");private set
    var offerOnline by mutableStateOf(false)
    private var recognizer:SpeechRecognizer?=null
    private var generation=0
    private val handler=Handler(Looper.getMainLooper())
    private val timeout=Runnable{cancel();message="음성 입력 시간이 끝났어요. 마이크를 눌러 다시 말해 주세요."}
    fun start(online:Boolean=false) {
        if(active)return
        message=""
        if(!online&&!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)){offerOnline=true;return}
        if(online&&!SpeechRecognizer.isRecognitionAvailable(context)){message="이 기기에는 음성 인식 서비스가 없어요. 키보드로 입력해 주세요.";return}
        val token=++generation
        try {
            val local=if(online)SpeechRecognizer.createSpeechRecognizer(context)else SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer=local
            local.setRecognitionListener(object:RecognitionListener {
                override fun onReadyForSpeech(params:Bundle?){if(token==generation)message="듣고 있어요. 편하게 말해 주세요."}
                override fun onBeginningOfSpeech(){}
                override fun onRmsChanged(rmsdB:Float){}
                override fun onBufferReceived(buffer:ByteArray?){}
                override fun onEndOfSpeech(){if(token==generation)message="말씀을 글자로 바꾸고 있어요…"}
                override fun onEvent(eventType:Int,params:Bundle?){}
                override fun onPartialResults(partialResults:Bundle?){if(token==generation)partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let{message=it}}
                override fun onResults(results:Bundle?) {
                    if(token!=generation)return
                    val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                    cancel()
                    if(text.isNotEmpty()){result(text);message="입력한 문장을 확인한 뒤 전송해 주세요."}else message="잘 듣지 못했어요. 다시 말해 주세요."
                }
                override fun onError(error:Int) {
                    if(token!=generation)return
                    cancel()
                    if(!online&&error in listOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,SpeechRecognizer.ERROR_SERVER,SpeechRecognizer.ERROR_CLIENT)){offerOnline=true}
                    message=when(error){SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->"마이크 권한이 필요해요. 앱 설정에서 허용해 주세요.";SpeechRecognizer.ERROR_NO_MATCH,SpeechRecognizer.ERROR_SPEECH_TIMEOUT->"잘 듣지 못했어요. 마이크를 눌러 다시 말해 주세요.";SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT->"인터넷 연결을 확인해 주세요. 입력한 글은 그대로 있어요.";else->"음성 인식을 시작하지 못했어요. 다시 시도하거나 키보드를 사용해 주세요."}
                }
            })
            active=true;recording(true)
            local.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)})
            handler.postDelayed(timeout,15_000)
        }catch(e:Exception){cancel();if(!online)offerOnline=true;message="음성 입력을 사용할 수 없어요. 키보드 입력은 계속할 수 있어요."}
    }
    fun stop(){runCatching{recognizer?.stopListening()}.onFailure{cancel()}}
    fun cancel(){generation++;handler.removeCallbacks(timeout);runCatching{recognizer?.cancel();recognizer?.destroy()};recognizer=null;active=false;message="";recording(false)}
    fun denied(){message="마이크 권한을 허용하면 음성으로 입력할 수 있어요. 키보드 입력은 그대로 사용할 수 있어요."}
}

@Composable internal fun VoiceInputButton(onRecording:(Boolean)->Unit,onResult:(String)->Unit) {
    val context=LocalContext.current
    val recording by rememberUpdatedState(onRecording);val result by rememberUpdatedState(onResult)
    val voice=remember{VoiceInput(context,{recording(it)},{result(it)})}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed->if(allowed)voice.start()else voice.denied()}
    DisposableEffect(voice){val lifecycle=(context as ComponentActivity).lifecycle;val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){voice.cancel();voice.offerOnline=false}};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);voice.cancel()}}
    IconButton(onClick={if(voice.active)voice.stop()else if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)voice.start()else permission.launch(Manifest.permission.RECORD_AUDIO)},modifier=Modifier.semantics{contentDescription="음성 입력"}) {
        Canvas(Modifier.size(23.dp)){val w=size.width;val h=size.height;val ink=Color(0xFF3F6E52);drawRoundRect(ink,Offset(w*.34f,0f),Size(w*.32f,h*.59f),CornerRadius(w*.16f));drawArc(ink,0f,180f,false,Offset(w*.17f,h*.20f),Size(w*.66f,h*.55f),style=Stroke(w*.07f));drawLine(ink,Offset(w*.5f,h*.73f),Offset(w*.5f,h*.94f),w*.07f);drawLine(ink,Offset(w*.31f,h*.96f),Offset(w*.69f,h*.96f),w*.07f)}
    }
    if(voice.active)AlertDialog(onDismissRequest={voice.cancel()},title={Text("음성 입력")},text={Text(voice.message.ifBlank{"마이크를 준비하고 있어요…"})},confirmButton={Button(onClick={voice.stop()}){Text("말하기 완료")}},dismissButton={TextButton(onClick={voice.cancel()}){Text("취소")}})
    else if(voice.offerOnline)AlertDialog(onDismissRequest={voice.offerOnline=false},title={Text("온라인 음성 인식을 사용할까요?")},text={Text("이 기기에서 한국어 기기 내 인식을 사용할 수 없어요. 온라인 인식을 선택하면 음성이 기기의 음성 인식 서비스로 전송될 수 있어요.")},confirmButton={Button(onClick={voice.offerOnline=false;voice.start(true)}){Text("온라인 인식")}},dismissButton={TextButton(onClick={voice.offerOnline=false}){Text("취소")}})
    else if(voice.message.isNotEmpty()) {
        LaunchedEffect(voice.message){android.widget.Toast.makeText(context,voice.message,android.widget.Toast.LENGTH_LONG).show()}
    }
}
