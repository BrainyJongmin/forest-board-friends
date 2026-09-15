package io.github.brainyjongmin.forestboardfriends

import android.content.Context
import android.os.StatFs
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

class CoachManager(private val context: Context) : TextToSpeech.OnInitListener {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val model=File(context.filesDir,"LFM2.5-1.2B-Instruct_int4_gpu.litertlm")
    private val temp=File(context.filesDir,"coach-model.part")
    private var engine:Engine?=null
    private var tts=TextToSpeech(context,this)
    private var ttsReady=false
    private var pendingSpeech:String?=null
    var status by mutableStateOf(if(model.exists())"모델 준비 완료" else "모델을 받으면 더 자연스럽게 설명해 줘요.");private set
    val ready get()=model.exists()

    override fun onInit(code:Int){if(code==TextToSpeech.SUCCESS){tts.language=Locale.KOREAN;ttsReady=true;pendingSpeech?.let{tts.speak(it,TextToSpeech.QUEUE_FLUSH,null,"coach");pendingSpeech=null}}}

    fun download(){if(ready)return;scope.launch{status="저장 공간 확인 중...";try{withContext(Dispatchers.IO){
        if(StatFs(context.filesDir.path).availableBytes<1_200_000_000L)error("여유 공간이 1.2GB 이상 필요해요.")
        val conn=URL(MODEL_URL).openConnection() as HttpURLConnection;conn.connectTimeout=20_000;conn.readTimeout=30_000;conn.connect()
        if(conn.responseCode !in 200..299)error("다운로드 서버 오류 ${conn.responseCode}")
        val total=conn.contentLengthLong;var done=0L;conn.inputStream.use{input->temp.outputStream().buffered().use{out->val buf=ByteArray(1024*256);while(true){val n=input.read(buf);if(n<0)break;out.write(buf,0,n);done+=n;withContext(Dispatchers.Main){status="모델 받는 중 ${if(total>0)done*100/total else 0}%"}}}};conn.disconnect()
        val digest=MessageDigest.getInstance("SHA-256");temp.inputStream().buffered().use{input->val b=ByteArray(1024*256);while(true){val n=input.read(b);if(n<0)break;digest.update(b,0,n)}}
        val actual=digest.digest().joinToString(""){"%02x".format(it)};if(actual!=MODEL_SHA)error("모델 파일 검증에 실패했어요.")
        if(!temp.renameTo(model))error("모델 파일을 저장하지 못했어요.")
    };status="모델 준비 완료"}catch(e:Exception){temp.delete();status=e.message?:"다운로드에 실패했어요."}}}

    fun answer(name:String,game:String,question:String,fact:String,done:(String)->Unit){
        if(!ready){val text="$name, ${game}에서는 합법적인 칸을 빛나게 표시해 줄게. 한 수씩 천천히 살펴보자!";done(text);speak(text);return}
        scope.launch{try{val e=ensureEngine();val config=ConversationConfig(systemInstruction=Contents.of("너는 6~9세 어린이를 위한 한국어 보드게임 코치다. 현재 게임 밖의 질문에는 게임 이야기만 하자고 답한다. 개인정보를 묻지 않는다. 제공된 사실을 바꾸지 말고 3문장 이내로 쉽고 따뜻하게 답한다."),samplerConfig=SamplerConfig(topK=20,topP=.9,temperature=.3));val conversation=e.createConversation(config);val prompt="아이 이름: $name\n게임: $game\n확정된 사실: $fact\n질문: $question";val out=StringBuilder();conversation.sendMessageAsync(prompt).catch{throw it}.collect{out.append(it.toString())};conversation.close();val text=clean(out.toString()).ifBlank{"$name, 지금은 짧은 힌트부터 살펴보자!"};done(text);speak(text)}catch(e:Exception){status="AI 코치 오류: ${e.message}";val text="$name, 지금은 준비된 힌트로 도와줄게!";done(text);speak(text)}}
    }

    private suspend fun ensureEngine():Engine{engine?.let{return it};status="AI 코치 준비 중...";return withContext(Dispatchers.IO){Engine(EngineConfig(modelPath=model.absolutePath,backend=Backend.CPU(),cacheDir=context.cacheDir.absolutePath)).also{it.initialize();engine=it}}.also{status="모델 준비 완료"}}
    private fun clean(raw:String)=raw.replace(Regex("<[^>]+>"),"").replace(Regex("https?://\\S+"),"").trim().take(300)
    fun speak(text:String){if(ttsReady)tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"coach") else pendingSpeech=text}
    fun deleteModel(){engine?.close();engine=null;model.delete();temp.delete();status="모델을 받으면 더 자연스럽게 설명해 줘요."}
    fun close(){scope.cancel();engine?.close();tts.stop();tts.shutdown()}
    companion object{
        private const val MODEL_URL="https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/main/LFM2.5-1.2B-Instruct_int4_gpu.litertlm?download=true"
        private const val MODEL_SHA="36f7f0221bcc42c75291da1d7e3422901024a5b06b9bfa3c02d7feface04f70a"
    }
}
