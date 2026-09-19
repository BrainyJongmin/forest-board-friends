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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val history=mutableListOf<String>()
    private val inference=Mutex()
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

    fun answer(name:String,game:String,question:String,fact:String,done:(String)->Unit)=respond(name,game,question,fact,false,done)
    fun comment(name:String,game:String,fact:String,done:(String)->Unit)=respond(name,game,"지금 판을 보며 먼저 건넬 자연스러운 한마디",fact,true,done)

    private fun respond(name:String,game:String,question:String,fact:String,spontaneous:Boolean,done:(String)->Unit){
        if(!ready){val q=question.lowercase();val text=if(spontaneous)listOf("$name, 서두르지 말고 네 리듬대로 가자!","오, 판이 점점 재미있어지는데?","좋아, 다음 수를 같이 천천히 보자!").random() else when{listOf("안녕","반가워").any(q::contains)->"$name, 안녕! 만나서 반가워. 오늘 뭐가 제일 재미있었어?";listOf("슬퍼","속상","화나","무서워").any(q::contains)->"그랬구나, 많이 속상했겠다. 내가 옆에서 들어 줄게. 무슨 일이 있었어?";listOf("뭐 해","뭐해","심심").any(q::contains)->"나도 $name 기다리고 있었지! $game 한 판 할까, 아니면 오늘 있었던 일 이야기할까?";else->"$name, 지금은 기본 대화 모드라 자세히 답하기 어려워. 홈의 AI 코치에서 모델을 받으면 그 이야기도 같이 할 수 있어!"};done(text);speak(text);return}
        scope.launch{inference.withLock{try{val e=ensureEngine();val recent=history.takeLast(4).joinToString("\n");val config=ConversationConfig(systemInstruction=Contents.of("너는 ${name}와 한국어 반말로 대화하는 다정하고 장난기 있는 숲속 여우 친구다. 질문의 핵심에 먼저 정확히 답하고 꼭 필요할 때만 되묻는다. 게임에 한정하지 말고 학교생활, 가족, 음식, 취미, 간단한 지식, 상상놀이와 농담도 자연스럽게 이야기한다. 모르는 것은 꾸며내지 말고 솔직히 말한다. '아이', '사용자', '좋아합니다' 같은 3인칭 보고체는 쓰지 말고 보통 1~5문장으로 말한다. 주소, 학교명, 전화번호, 비밀번호 같은 개인정보를 묻거나 기억하지 않는다. 위험 행동이나 성인 주제는 돕지 말고 보호자에게 물어보도록 안내한다."),samplerConfig=SamplerConfig(topK=50,topP=.9,temperature=.35));val conversation=e.createConversation(config);val prompt=if(spontaneous)"우리는 $game 중이야. 현재 상태: $fact\n같은 말을 반복하지 말고 지금 상황에 어울리는 짧은 감탄이나 응원을 먼저 한마디 해." else "최근 대화:\n${recent.ifBlank{"아직 없음"}}\n게임 정보는 질문과 관련 있을 때만 참고해: $game, $fact\n$name: $question\n여우:";val out=StringBuilder();try{conversation.sendMessageAsync(prompt).catch{throw it}.collect{out.append(it.toString())}}finally{conversation.close()};val text=clean(out.toString()).ifBlank{"$name, 잘 못 들었어. 한 번만 더 말해 줄래?"};if(!spontaneous){history+="$name: $question\n여우: $text";while(history.size>6)history.removeAt(0)};done(text);speak(text)}catch(e:Exception){status="AI 코치 오류: ${e.message}";val text="$name, 잠깐 생각이 꼬였어. 한 번만 더 말해 줄래?";done(text);speak(text)}}}
    }

    private suspend fun ensureEngine():Engine{engine?.let{return it};status="AI 코치 준비 중...";return withContext(Dispatchers.IO){Engine(EngineConfig(modelPath=model.absolutePath,backend=Backend.CPU(),cacheDir=context.cacheDir.absolutePath)).also{it.initialize();engine=it}}.also{status="모델 준비 완료"}}
    private fun clean(raw:String)=raw.replace(Regex("<[^>]+>"),"").replace(Regex("https?://\\S+"),"").trim().take(600)
    private var listening=false
    fun setListening(value:Boolean){listening=value;if(value){pendingSpeech=null;tts.stop()}}
    fun speak(text:String){if(listening)return;if(ttsReady)tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"coach") else pendingSpeech=text}
    fun deleteModel(){engine?.close();engine=null;history.clear();model.delete();temp.delete();status="모델을 받으면 더 자연스럽게 설명해 줘요."}
    fun close(){scope.cancel();engine?.close();tts.stop();tts.shutdown()}
    companion object{
        private const val MODEL_URL="https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/main/LFM2.5-1.2B-Instruct_int4_gpu.litertlm?download=true"
        private const val MODEL_SHA="36f7f0221bcc42c75291da1d7e3422901024a5b06b9bfa3c02d7feface04f70a"
    }
}
