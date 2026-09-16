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
        if(!ready){val q=question.lowercase();val text=when{listOf("안녕","반가워").any(q::contains)->"$name, 안녕! 만나서 반가워. 오늘 뭐가 제일 재미있었어?";listOf("슬퍼","속상","화나","무서워").any(q::contains)->"그랬구나, 많이 속상했겠다. 내가 옆에서 들어 줄게. 무슨 일이 있었어?";listOf("뭐 해","뭐해","심심").any(q::contains)->"나도 $name 기다리고 있었지! $game 한 판 할까, 아니면 오늘 있었던 일 이야기할까?";listOf("좋아","재밌","최고").any(q::contains)->"오, 나도 듣기만 해도 신난다! 어떤 점이 제일 좋았어?";listOf("학교","친구","선생님").any(q::contains)->"그랬구나! 오늘 학교에서 제일 기억나는 일은 뭐였어?";listOf("먹","맛있","간식").any(q::contains)->"맛있는 이야기 좋지! 나는 숲속 친구들과 도토리 쿠키를 골라 볼래. ${name}은 뭐가 제일 좋아?";else->"응, 듣고 있어! 그 얘기에서 제일 재미있었던 부분을 조금 더 들려줄래?"};done(text);speak(text);return}
        scope.launch{try{val e=ensureEngine();val config=ConversationConfig(systemInstruction=Contents.of("너는 화면 앞의 ${name}에게 직접 한국어 반말로 말하는 다정하고 장난기 있는 숲속 여우 친구다. 상담 기록이나 설명문처럼 쓰지 말고 실제 친구처럼 짧게 반응하고 자연스럽게 되물어라. '아이', '아이가', '사용자', '좋아합니다' 같은 3인칭·보고체 표현은 쓰지 않는다. 게임뿐 아니라 인사, 기분, 학교생활, 가족, 음식, 취미 같은 가벼운 일상대화도 한다. 주소, 학교명, 전화번호, 비밀번호 같은 개인정보를 묻거나 기억하지 않는다. 위험한 행동, 성인 주제, 의료·법률 판단은 하지 말고 믿을 수 있는 보호자에게 물어보라고 한다. 제공된 게임 상태는 바꾸지 말고 1~3문장으로 답한다."),samplerConfig=SamplerConfig(topK=40,topP=.92,temperature=.6));val conversation=e.createConversation(config);val prompt="우리는 지금 $game 중이야. 게임 질문일 때만 이 상태를 참고해: $fact\n${name}가 방금 너에게 직접 말했어: \"$question\"\n${name}에게 건넬 대답만 말해.";val out=StringBuilder();conversation.sendMessageAsync(prompt).catch{throw it}.collect{out.append(it.toString())};conversation.close();val text=clean(out.toString()).ifBlank{"$name, 잘 못 들었어. 한 번만 더 말해 줄래?"};done(text);speak(text)}catch(e:Exception){status="AI 코치 오류: ${e.message}";val text="$name, 잠깐 생각이 꼬였어. 한 번만 더 말해 줄래?";done(text);speak(text)}}
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
