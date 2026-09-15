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
        if(!ready){val q=question.lowercase();val text=when{listOf("안녕","반가워").any(q::contains)->"$name, 안녕! 오늘도 만나서 반가워. 게임도 하고 재미있는 이야기도 나누자!";listOf("슬퍼","속상","화나","무서워").any(q::contains)->"$name, 그런 기분이 들었구나. 천천히 숨을 쉬고, 믿을 수 있는 어른에게 이야기해 보자.";listOf("뭐 해","뭐해","심심").any(q::contains)->"$name, 나는 네 옆에서 $game 이야기를 나누고 있어! 오늘 있었던 재미있는 일도 들려줘.";else->"$name, 모델을 받으면 게임뿐 아니라 학교, 취미, 기분 같은 가벼운 이야기도 나눌 수 있어. 지금은 $game 힌트부터 도와줄게!"};done(text);speak(text);return}
        scope.launch{try{val e=ensureEngine();val config=ConversationConfig(systemInstruction=Contents.of("너는 6~9세 어린이와 한국어로 이야기하는 따뜻한 보드게임 친구다. 보드게임뿐 아니라 인사, 기분, 학교생활, 가족, 음식, 취미 같은 가벼운 일상대화에도 답한다. 주소, 학교명, 전화번호, 비밀번호 같은 개인정보를 묻거나 기억하지 말고, 아이가 말하면 반복하지 말고 공유하지 말라고 알려준다. 위험한 행동, 성인 주제, 의료·법률 판단은 시도하지 말고 믿을 수 있는 보호자에게 물어보라고 안내한다. 제공된 게임 사실은 바꾸지 말고 언제나 쉽고 다정하게 3문장 이내로 답한다."),samplerConfig=SamplerConfig(topK=20,topP=.9,temperature=.3));val conversation=e.createConversation(config);val prompt="아이 이름: $name\n현재 게임: $game\n게임 상태(게임 질문일 때만 참고): $fact\n아이의 말: $question";val out=StringBuilder();conversation.sendMessageAsync(prompt).catch{throw it}.collect{out.append(it.toString())};conversation.close();val text=clean(out.toString()).ifBlank{"$name, 다시 한 번 짧게 말해 줄래?"};done(text);speak(text)}catch(e:Exception){status="AI 코치 오류: ${e.message}";val text="$name, 지금은 준비된 힌트로 도와줄게!";done(text);speak(text)}}
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
