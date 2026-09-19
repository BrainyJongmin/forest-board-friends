@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.brainyjongmin.forestboardfriends

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val Forest = Color(0xFF3F6E52)
private val Cream = Color(0xFFFFF8E8)
private val Bark = Color(0xFF6D4C41)
private class GameSounds{
    private var tone=runCatching{ToneGenerator(AudioManager.STREAM_MUSIC,70)}.getOrNull()
    private val boardMusic=audioTrack(forestMelody(),true)?.apply{setVolume(.15f)}
    private val blockMusic=arrayOf(audioTrack(korobeiniki(),true),audioTrack(kalinka(),true)).onEach{it?.setVolume(.17f)}
    private val applause=audioTrack(applause(),false)?.apply{setVolume(.8f)}
    private val pieceHit=audioTrack(impact(720.0),false)
    private val blockHit=audioTrack(impact(180.0),false)
    private val lineClear=audioTrack(lineClear(),false)
    private var musicMode:Boolean?=null;private var blockTheme=0;private var musicVolume=100;private var soundVolume=70
    fun move(){playFx(pieceHit){tone?.startTone(ToneGenerator.TONE_PROP_BEEP,65)}}
    fun menu(){tone?.startTone(ToneGenerator.TONE_PROP_ACK,90)}
    fun block(){playFx(blockHit){tone?.startTone(ToneGenerator.TONE_PROP_BEEP2,100)}}
    fun clear(lines:Int){playFx(lineClear){tone?.startTone(ToneGenerator.TONE_PROP_ACK,220)};if(lines>=4)tone?.startTone(ToneGenerator.TONE_CDMA_HIGH_L,300)}
    fun item(){tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,420)}
    fun volumes(music:Int,sound:Int){musicVolume=music.coerceIn(0,100);boardMusic?.setVolume(.15f*musicVolume/100);blockMusic.forEach{it?.setVolume(.17f*musicVolume/100)};soundVolume=sound.coerceIn(0,100);listOf(pieceHit,blockHit,lineClear).forEach{it?.setVolume(.8f*soundVolume/100)};applause?.setVolume(.8f*soundVolume/100);tone?.release();tone=if(soundVolume==0)null else runCatching{ToneGenerator(AudioManager.STREAM_MUSIC,soundVolume)}.getOrNull();val active=activeMusic();if(musicVolume==0){boardMusic?.pause();blockMusic.forEach{it?.pause()}}else if(active?.playState!=AudioTrack.PLAYSTATE_PLAYING)runCatching{active?.play()}}
    fun music(block:Boolean?){musicMode=block;runCatching{boardMusic?.pause();blockMusic.forEach{it?.pause()};activeMusic()?.let{if(musicVolume>0){it.setPlaybackHeadPosition(0);it.play()}}}}
    fun blockLevel(level:Int){val next=(level-1)%blockMusic.size;if(next==blockTheme)return;blockTheme=next;if(musicMode==true)music(true)}
    fun finish(){tone?.startTone(ToneGenerator.TONE_PROP_PROMPT,350);runCatching{applause?.pause();applause?.setPlaybackHeadPosition(0);applause?.play()}}
    fun close(){tone?.release();boardMusic?.release();blockMusic.forEach{it?.release()};listOf(pieceHit,blockHit,lineClear,applause).forEach{it?.release()}}
    private fun activeMusic()=when(musicMode){true->blockMusic[blockTheme];false->boardMusic;null->null}
    private fun playFx(track:AudioTrack?,fallback:()->Unit){if(track==null){fallback();return};runCatching{track.pause();track.setPlaybackHeadPosition(0);track.play()}.onFailure{fallback()}}
    private fun audioTrack(samples:ShortArray,loop:Boolean)=runCatching{AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(22_050).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size*2).build().apply{write(samples,0,samples.size);if(loop)setLoopPoints(0,samples.size,-1)}}.getOrNull()
    private fun forestMelody()=melody(intArrayOf(64,67,71,74,71,67,64,67,62,66,69,74,69,66,62,66,60,64,67,72,67,64,60,64,62,67,71,74,71,67,66,62),IntArray(32){1},intArrayOf(40,38,36,35),.21,.13,.65)
    private fun korobeiniki()=melody(intArrayOf(76,71,72,74,72,71,69,69,72,76,74,72,71,71,72,74,76,72,69,69,74,77,81,79,77,76,72,76,74,72,71,71,72,74,76,72,69,69),intArrayOf(2,1,1,2,1,1,2,1,1,2,1,1,2,1,1,2,2,2,2,4,2,1,2,1,1,2,1,2,1,1,2,1,1,2,2,2,2,6),intArrayOf(40,45,47,40,38,36,35,45),.15,.2,1.15)
    private fun kalinka()=melody(intArrayOf(76,76,74,72,71,72,74,72,71,69,76,76,74,72,71,72,74,72,71,69,81,79,77,76,74,72,74,76,77,76,74,72),intArrayOf(1,1,1,1,2,1,1,1,1,4,1,1,1,1,2,1,1,1,1,4,2,1,1,2,1,1,1,1,2,1,1,6),intArrayOf(45,40,43,45,41,43,40,45),.14,.22,1.2)
    private fun melody(notes:IntArray,lengths:IntArray,chords:IntArray,beat:Double,drive:Double,drum:Double):ShortArray{val song=notes.indices.flatMap{n->List(lengths[n]){notes[n]}};val rate=22_050;val intervals=intArrayOf(0,7,12,7);return ShortArray((song.size*beat*rate).toInt()){i->val step=(i/(beat*rate)).toInt().coerceAtMost(song.lastIndex);val local=i/rate.toDouble()-step*beat;val f=440.0*2.0.pow((song[step]-69)/12.0);val root=440.0*2.0.pow((chords[step/8%chords.size]-69)/12.0);val arp=root*2.0.pow(intervals[step%4]/12.0);val lead=if(sin(2*PI*f*i/rate)>=0)1.0 else -1.0;val guitar=(if(sin(2*PI*root*i/rate)>=0)1.0 else -1.0)+(if(sin(3*PI*root*i/rate)>=0)1.0 else -1.0)*.5;val envelope=minOf(1.0,local/.012)*(1-local/beat*.32);(((lead*.34+sin(2*PI*f*i/rate)*.23+sin(2*PI*arp*i/rate)*.18+guitar*drive)*envelope+drums(i,rate,beat,drum))*4_000).toInt().coerceIn(-32767,32767).toShort()}}
    private fun drums(i:Int,rate:Int,beat:Double,power:Double):Double{val t=i/rate.toDouble();val bar=beat*4;val kickTime=t%bar;val snareTime=(t+bar-beat*2)%bar;val hatTime=t%beat;val noise=sin(i*12.9898)*sin(i*.173);val kick=if(kickTime<.1)sin(2*PI*(95-kickTime*320)*kickTime)*exp(-kickTime*24)else 0.0;val snare=if(snareTime<.09)noise*exp(-snareTime*30)else 0.0;val hat=if(hatTime<.035)noise*exp(-hatTime*90)else 0.0;return(kick*.65+snare*.32+hat*.12)*power}
    private fun impact(pitch:Double):ShortArray{val rate=22_050;val random=Random(pitch.toInt());return ShortArray((rate*.13).toInt()){i->val t=i/rate.toDouble();val click=(random.nextDouble()*2-1)*exp(-t*95);val body=(sin(2*PI*pitch*t)*.7+sin(2*PI*pitch*.42*t)*.45)*exp(-t*28);((click*.8+body)*11_000).toInt().coerceIn(-32767,32767).toShort()}}
    private fun lineClear():ShortArray{val rate=22_050;val random=Random(19);return ShortArray((rate*.55).toInt()){i->val t=i/rate.toDouble();val sweep=sin(2*PI*(260*t+920*t*t))*exp(-t*2.8);val burst=(random.nextDouble()*2-1)*exp(-(t%.09)*48)*(1-t/.55);((sweep*.75+burst*.32)*10_000).toInt().coerceIn(-32767,32767).toShort()}}
    private fun applause():ShortArray{val rate=22_050;val out=DoubleArray((2.4*rate).toInt());val random=Random(7);repeat(24){val start=((it*.09+random.nextDouble()*.18)*rate).toInt();val length=(.13*rate).toInt();var last=0.0;for(j in 0 until length)if(start+j<out.size){val noise=random.nextDouble()*2-1;val crisp=noise-last*.65;last=noise;out[start+j]+=crisp*(1-j.toDouble()/length).pow(2)*9_000}};return ShortArray(out.size){out[it].toInt().coerceIn(-24_000,24_000).toShort()}}
}
enum class GameKind(val title:String,val emoji:String){GO("바둑","⚫"),JANGGI("장기","將"),CHESS("체스","♞"),OMOK("오목","●"),BLOCK("엄마의 블록 퍼즐","▦")}
private enum class Page{HOME,SETUP,GAME,BLOCK,MODEL}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ForestApp() }
    }
}

@Composable private fun ForestApp(){
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences("forest_friends",0)}
    var playerName by remember{mutableStateOf(prefs.getString("player_name","") ?: "")}
    var draft by remember{mutableStateOf("")}
    if(playerName.isBlank()){
        MaterialTheme(colorScheme=lightColorScheme(primary=Forest,background=Cream)){Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),color=Cream){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(Modifier.widthIn(max=620.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Image(painterResource(R.drawable.forest_friends),null,Modifier.fillMaxWidth().heightIn(max=220.dp),contentScale=ContentScale.Fit)
            Text("숲속 보드 친구들",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Bark)
            Spacer(Modifier.height(20.dp));Text("친구야, 이름을 알려줘!",fontSize=20.sp)
            OutlinedTextField(draft,{draft=it.take(12)},singleLine=true,label={Text("이름")},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done))
            Spacer(Modifier.height(12.dp));Button(onClick={val n=draft.trim();if(n.isNotEmpty()){prefs.edit().putString("player_name",n).apply();playerName=n}},enabled=draft.trim().isNotEmpty()){Text("숲속으로 들어가기")}
            Text("이름은 이 기기에만 저장돼요.",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=12.dp))
        }}}};return
    }
    MaterialTheme(colorScheme=lightColorScheme(primary=Forest,secondary=Color(0xFFD88B45),background=Cream,surface=Color.White)){AppContent(playerName,{n->prefs.edit().putString("player_name",n).apply();playerName=n},prefs)}
}

@Composable private fun AppContent(name:String,rename:(String)->Unit,prefs:android.content.SharedPreferences){
    var page by remember{mutableStateOf(Page.HOME)};var kind by remember{mutableStateOf(GameKind.OMOK)}
    var onePlayer by remember{mutableStateOf(true)};var difficulty by remember{mutableStateOf(Difficulty.EASY)};var boardSize by remember{mutableIntStateOf(9)}
    var confirmGameExit by remember{mutableStateOf(false)};var settings by remember{mutableStateOf(false)};var musicVolume by remember{mutableIntStateOf(prefs.getInt("music_volume",70))};var soundVolume by remember{mutableIntStateOf(prefs.getInt("sound_volume",70))};var lastBack by remember{mutableLongStateOf(0L)}
    val context=LocalContext.current;val coach=remember{CoachManager(context)};val sounds=remember{GameSounds()};DisposableEffect(Unit){onDispose{coach.close();sounds.close()}}
    LaunchedEffect(Unit){sounds.volumes(musicVolume,soundVolume)}
    LaunchedEffect(page){sounds.music(when(page){Page.GAME->false;Page.BLOCK->true;else->null})}
    DisposableEffect(page){val lifecycle=(context as ComponentActivity).lifecycle;val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_START->sounds.music(when(page){Page.GAME->false;Page.BLOCK->true;else->null});Lifecycle.Event.ON_STOP->sounds.music(null);else->{}}};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
    LaunchedEffect(name){coach.speak("$name, 숲속 보드 친구들에 온 걸 환영해! 오늘도 즐겁게 놀아 보자!")}
    fun leaveGame(){if(!confirmGameExit)sounds.menu();confirmGameExit=true}
    fun open(next:Page){sounds.menu();page=next}
    BackHandler{when(page){Page.GAME,Page.BLOCK->leaveGame();Page.HOME->{val now=SystemClock.elapsedRealtime();if(now-lastBack<2_000)(context as? Activity)?.finish()else{lastBack=now;sounds.menu();Toast.makeText(context,"한 번 더 누르면 종료돼요.",Toast.LENGTH_SHORT).show()}};else->open(Page.HOME)}}
    Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),color=Cream){when(page){
        Page.HOME->HomeScreen(name,onSelect={kind=it;open(if(it==GameKind.BLOCK)Page.BLOCK else Page.SETUP)},onModel={open(Page.MODEL)},onSettings={settings=true},onRename=rename)
        Page.SETUP->SetupScreen(kind,onePlayer,{onePlayer=it},difficulty,{difficulty=it},boardSize,{boardSize=it},{open(Page.HOME)},{open(Page.GAME)})
        Page.GAME->BoardGameScreen(name,kind,onePlayer,difficulty,boardSize,coach,sounds::move,sounds::finish,::leaveGame){open(Page.HOME)}
        Page.BLOCK->BlockScreen(prefs,sounds,::leaveGame){open(Page.HOME)}
        Page.MODEL->ModelScreen(coach){open(Page.HOME)}
    }}
    if(confirmGameExit)AlertDialog(onDismissRequest={confirmGameExit=false},title={Text("홈으로 갈까요?")},text={Text("지금 게임을 멈추고 홈으로 돌아갈 수 있어요.")},confirmButton={Button(onClick={confirmGameExit=false;page=Page.HOME}){Text("홈으로")}},dismissButton={TextButton(onClick={confirmGameExit=false}){Text("계속하기")}})
    if(settings){fun save(){prefs.edit().putInt("music_volume",musicVolume).putInt("sound_volume",soundVolume).apply();sounds.volumes(musicVolume,soundVolume);settings=false};AlertDialog(onDismissRequest=::save,title={Text("소리 설정")},text={Column{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("배경 음악: ${if(musicVolume==0)"끔" else "$musicVolume%"}",Modifier.weight(1f));TextButton(onClick={musicVolume=if(musicVolume==0)70 else 0;sounds.volumes(musicVolume,soundVolume)}){Text(if(musicVolume==0)"켜기" else "끄기")}};Slider(musicVolume.toFloat(),{musicVolume=it.roundToInt()},onValueChangeFinished={sounds.volumes(musicVolume,soundVolume)},valueRange=0f..100f);Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("효과음: ${if(soundVolume==0)"끔" else "$soundVolume%"}",Modifier.weight(1f));TextButton(onClick={soundVolume=if(soundVolume==0)70 else 0;sounds.volumes(musicVolume,soundVolume)}){Text(if(soundVolume==0)"켜기" else "끄기")}};Slider(soundVolume.toFloat(),{soundVolume=it.roundToInt()},onValueChangeFinished={sounds.volumes(musicVolume,soundVolume)},valueRange=0f..100f)}},confirmButton={Button(onClick=::save){Text("저장")}})}
}

@Composable private fun HomeScreen(name:String,onSelect:(GameKind)->Unit,onModel:()->Unit,onSettings:()->Unit,onRename:(String)->Unit){
    var editing by remember{mutableStateOf(false)};var about by remember{mutableStateOf(false)};var draft by remember(name){mutableStateOf(name)};val uri=LocalUriHandler.current
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){Column(Modifier.widthIn(max=720.dp).fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Image(painterResource(R.drawable.forest_friends),null,Modifier.fillMaxWidth().height(185.dp),contentScale=ContentScale.Fit)
        Text("$name, 오늘은 뭘 해볼까?",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Bark,textAlign=TextAlign.Center)
        Spacer(Modifier.height(16.dp));GameKind.entries.take(4).chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){row.forEach{g->Card(Modifier.weight(1f).height(120.dp).clickable{onSelect(g)},shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(g.emoji,fontSize=38.sp);Text(g.title,fontSize=21.sp,fontWeight=FontWeight.Bold,color=Forest)}}};if(row.size==1)Spacer(Modifier.weight(1f))};Spacer(Modifier.height(12.dp))}
        Card(Modifier.fillMaxWidth().height(92.dp).clickable{onSelect(GameKind.BLOCK)},colors=CardDefaults.cardColors(containerColor=Color(0xFFE9D9BC)),shape=RoundedCornerShape(22.dp)){Row(Modifier.fillMaxSize().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Text("▦",fontSize=40.sp);Spacer(Modifier.width(16.dp));Column{Text("엄마의 블록 퍼즐",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Bark);Text("숲속 레트로 무한 모드")}}}
        Row(Modifier.padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onModel){Text("🦊 AI 코치")};OutlinedButton(onClick=onSettings){Text("⚙ 소리 설정")}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={editing=true}){Text("이름 바꾸기")};OutlinedButton(onClick={about=true}){Text("앱 정보")}}
    }}
    if(editing)AlertDialog(onDismissRequest={editing=false},title={Text("이름 바꾸기")},text={OutlinedTextField(draft,{draft=it.take(12)},singleLine=true)},confirmButton={Button(onClick={if(draft.trim().isNotEmpty()){onRename(draft.trim());editing=false}}){Text("저장")}},dismissButton={TextButton(onClick={editing=false}){Text("취소")}})
    if(about)AlertDialog(onDismissRequest={about=false},title={Text("숲속 보드 친구들")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("현재 버전 ${BuildConfig.VERSION_NAME}",fontWeight=FontWeight.Bold);Text("만든이: 은태 아빠");Text("Copyright © 2026 은태 아빠\n소스 코드: Apache License 2.0",fontSize=13.sp,color=Color.Gray);Text("최신 버전은 GitHub 릴리스에서 확인하고 설치할 수 있어요.")}},confirmButton={Button(onClick={uri.openUri("https://github.com/BrainyJongmin/forest-board-friends/releases/latest")}){Text("최신 버전 확인")}},dismissButton={TextButton(onClick={about=false}){Text("닫기")}})
}

@Composable private fun SetupScreen(kind:GameKind,one:Boolean,setOne:(Boolean)->Unit,difficulty:Difficulty,setDifficulty:(Difficulty)->Unit,size:Int,setSize:(Int)->Unit,back:()->Unit,start:()->Unit){
    Box(Modifier.fillMaxSize().padding(horizontal=16.dp),contentAlignment=Alignment.TopCenter){Column(Modifier.widthIn(max=680.dp).fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("${kind.emoji} ${kind.title}",fontSize=32.sp,fontWeight=FontWeight.Bold,color=Bark);Spacer(Modifier.height(18.dp));Choice("함께 할 사람",listOf("동물 친구와 1인용","한 기기 2인용"),if(one)0 else 1){setOne(it==0)};if(one){Choice("난이도",listOf("쉬움","보통"),difficulty.ordinal){setDifficulty(Difficulty.entries[it])}};if(kind==GameKind.GO)Choice("바둑판",listOf("9×9","13×13","19×19"),listOf(9,13,19).indexOf(size)){setSize(listOf(9,13,19)[it])};Spacer(Modifier.height(16.dp));Button(start,Modifier.fillMaxWidth().height(58.dp)){Text("게임 시작",fontSize=20.sp)};TextButton(back){Text("돌아가기")}}}
}
@Composable private fun Choice(title:String,items:List<String>,selected:Int,onSelect:(Int)->Unit){Text(title,fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth().padding(vertical=8.dp));SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){items.forEachIndexed{i,s->SegmentedButton(selected==i,{onSelect(i)},SegmentedButtonDefaults.itemShape(i,items.size)){Text(s)}}};Spacer(Modifier.height(14.dp))}

@Composable private fun BoardGameScreen(name:String,kind:GameKind,one:Boolean,difficulty:Difficulty,size:Int,coach:CoachManager,moveSound:()->Unit,endSound:()->Unit,back:()->Unit,home:()->Unit){
    var round by remember{mutableIntStateOf(0)}
    val game=remember(kind,size,round){when(kind){GameKind.GO->GoGame(size);GameKind.JANGGI->JanggiGame();GameKind.CHESS->ChessGame();else->OmokGame()}}
    var selected by remember{mutableStateOf<Pos?>(null)};var revision by remember{mutableIntStateOf(0)};var hint by remember{mutableStateOf<Pos?>(null)};var speech by remember{mutableStateOf("$name, 천천히 생각해 보자!")};var question by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    fun maybeAi(){if(one&&game.winner==null&&game.currentPlayer==2)scope.launch{delay(350);val move=runCatching{game.hint(difficulty)}.getOrNull();val moved=if(move!=null)game.play(move)else game is GoGame&&game.pass();if(moved){moveSound();revision++}}}
    LaunchedEffect(game){val text="$name, ${kind.title} 시작해 볼까? 네 차례야!";speech=text;coach.speak(text);maybeAi()}
    val legalTargets=selected?.let{game.legalMoves(it).map(GameMove::to).toSet()}.orEmpty()
    LaunchedEffect(revision){if(coach.ready&&revision>0&&revision%5==0&&game.winner==null){delay(650);coach.comment(name,kind.title,"${game.status}, ${if(one)"동물 친구와 대국" else "둘이 대국"}"){speech=it}}}
    LaunchedEffect(game.winner){game.winner?.let{endSound();val text=if(!one||it==1)"와, $name 정말 멋졌어! 한 판 더 해 볼까?" else "$name, 아쉬웠지? 그래도 끝까지 잘했어. 다시 하면 더 잘할 수 있어!";speech=text;coach.speak(text)}}
    val tap:(Pos)->Unit={p->if(game.winner==null&&(!one||game.currentPlayer!=2)){val from=selected;val move=if(kind==GameKind.GO||kind==GameKind.OMOK)GameMove(to=p)else GameMove(from,p);if(game.play(move)){moveSound();selected=null;hint=null;revision++;maybeAi()}else if(game.cell(p)?.owner==game.currentPlayer){selected=p;hint=null;val targets=game.legalMoves(p);if(targets.isEmpty())speech=if(game is ChessGame&&game.cell(p)?.label in setOf("♕","♛"))"$name, 퀸은 직선과 대각선으로 가지만 앞의 말을 뛰어넘을 수 없어. 길을 먼저 열어 줘!" else "$name, 이 말은 지금 이동할 수 있는 곳이 없어."}else speech="$name, 그곳에는 둘 수 없어."}}
    val header: @Composable ()->Unit={Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(back){Text("‹ 홈")};Text(kind.title,fontSize=24.sp,fontWeight=FontWeight.Bold,color=Bark,modifier=Modifier.weight(1f),textAlign=TextAlign.Center);Spacer(Modifier.width(55.dp))}}
    val controls: @Composable ()->Unit={Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){OutlinedButton(onClick={if(game.undo()){if(one)game.undo();selected=null;hint=null;revision++}}){Text("무르기")};if(kind==GameKind.GO||kind==GameKind.JANGGI)OutlinedButton(onClick={if(game.pass()){revision++;maybeAi()}}){Text("한 수 쉼")};Button(onClick={val m=runCatching{game.hint(difficulty)}.getOrNull();hint=m?.to;selected=m?.from;speech=when{m!=null->"$name, 빛나는 칸을 살펴봐!";game is GoGame->"$name, 지금은 한 수 쉬는 것도 좋아.";else->"지금은 추천할 수가 없어."}}){Text("힌트")}}}
    val coachPanel: @Composable ()->Unit={Card(Modifier.fillMaxWidth().padding(top=8.dp),colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(12.dp)){Text("🦊 $speech",fontSize=16.sp);if(!coach.ready)Text("자유 대화는 홈의 AI 코치에서 모델을 받은 뒤 사용할 수 있어요.",fontSize=12.sp,color=Color.Gray);Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(question,{question=it.take(120)},Modifier.weight(1f),placeholder={Text("게임이나 오늘 일 물어보기")},singleLine=true);IconButton(onClick={val q=question.trim();if(q.isNotEmpty()){question="";speech="음, 잠깐 생각해 볼게!";coach.answer(name,kind.title,q,"현재 차례는 ${game.status}"){speech=it}}}){Text("➤",fontSize=22.sp)}}}}}
    BoxWithConstraints(Modifier.fillMaxSize()){
        if(maxWidth>maxHeight){Row(Modifier.fillMaxSize()){Column(Modifier.weight(1f).fillMaxHeight().padding(start=12.dp),horizontalAlignment=Alignment.CenterHorizontally){header();GameBoard(game,selected,hint,legalTargets,revision,tap)};Column(Modifier.widthIn(min=280.dp,max=420.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(game.status,fontSize=18.sp,fontWeight=FontWeight.Bold,color=Forest);Spacer(Modifier.height(12.dp));controls();coachPanel()}}}
        else Column(Modifier.fillMaxSize().padding(horizontal=12.dp),horizontalAlignment=Alignment.CenterHorizontally){header();Text(game.status,fontSize=18.sp,fontWeight=FontWeight.Bold,color=Forest);Spacer(Modifier.height(8.dp));GameBoard(game,selected,hint,legalTargets,revision,tap);controls();coachPanel()}
    }
    game.winner?.let{winner->val celebrate=!one||winner==1;val title=when{winner==0->"멋진 무승부!";!one->"${name(winner)} 승리!";winner==1->"$name 승리!";else->"이번엔 동물 친구 승리!"};GameOverDialog(title,if(celebrate)"동물 친구들이 박수치고 있어! 정말 멋진 경기였어." else "동물 친구들이 꼭 안아 줄게. 끝까지 해낸 것도 정말 멋져!",celebrate,onRestart={round++;selected=null;hint=null;speech="$name, 새 경기를 시작하자!"},onHome=home)}
}

@Composable private fun ColumnScope.GameBoard(game:BoardGame,selected:Pos?,hint:Pos?,legalTargets:Set<Pos>,revision:Int,onTap:(Pos)->Unit){
    val chess=game is ChessGame;val janggi=game is JanggiGame;val line=Color(0xFF5D4037);val context=LocalContext.current
    val choTypeface=remember(janggi){if(janggi)android.graphics.Typeface.createFromAsset(context.assets,"fonts/cho_caoshu.ttf")else android.graphics.Typeface.DEFAULT_BOLD}
    val hanTypeface=remember(janggi){if(janggi)android.graphics.Typeface.createFromAsset(context.assets,"fonts/han_kaishu.ttf")else android.graphics.Typeface.DEFAULT_BOLD}
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
    val ratio=game.cols.toFloat()/game.rows
    val boardSize=if(maxWidth/ratio<=maxHeight)Modifier.fillMaxWidth().aspectRatio(ratio) else Modifier.fillMaxHeight().aspectRatio(ratio)
    key(revision,selected,hint,legalTargets){Canvas(boardSize.padding(4.dp).pointerInput(game,revision){detectTapGestures{point->val margin=if(chess)0f else minOf(size.width.toFloat()/game.cols,size.height.toFloat()/game.rows)*.5f;onTap(Pos(boardIndex(point.y,size.height.toFloat(),game.rows,margin),boardIndex(point.x,size.width.toFloat(),game.cols,margin)))}}){
        drawRect(if(chess)Brush.linearGradient(listOf(Color(0xFFF2DFC0),Color(0xFFD7B27A)))else Brush.linearGradient(listOf(Color(0xFFE7C889),Color(0xFFC99652))))
        val margin=if(chess)0f else minOf(size.width/game.cols,size.height/game.rows)*.5f
        val cw=if(chess)size.width/game.cols else (size.width-margin*2)/(game.cols-1)
        val ch=if(chess)size.height/game.rows else (size.height-margin*2)/(game.rows-1)
        fun center(p:Pos)=Offset(if(chess)(p.col+.5f)*cw else margin+p.col*cw,if(chess)(p.row+.5f)*ch else margin+p.row*ch)
        if(chess){for(r in 0..game.rows)drawLine(line,Offset(0f,r*ch),Offset(size.width,r*ch),2f);for(c in 0..game.cols)drawLine(line,Offset(c*cw,0f),Offset(c*cw,size.height),2f)}
        else{for(r in 0 until game.rows)drawLine(line,Offset(margin,margin+r*ch),Offset(size.width-margin,margin+r*ch),2f);for(c in 0 until game.cols)drawLine(line,Offset(margin+c*cw,margin),Offset(margin+c*cw,size.height-margin),2f)}
        if(game is JanggiGame){drawLine(line,center(Pos(0,3)),center(Pos(2,5)),2f);drawLine(line,center(Pos(0,5)),center(Pos(2,3)),2f);drawLine(line,center(Pos(7,3)),center(Pos(9,5)),2f);drawLine(line,center(Pos(7,5)),center(Pos(9,3)),2f)}
        if(!chess){val stars=when(game.rows){9->listOf(2,4,6);13->listOf(3,6,9);15->listOf(3,7,11);19->listOf(3,9,15);else->emptyList()};for(r in stars)for(c in stars)drawCircle(line,minOf(cw,ch)*.07f,center(Pos(r,c)))}
        selected?.let{if(chess)drawRect(Color(0x663F6E52),Offset(it.col*cw,it.row*ch),Size(cw,ch))else drawCircle(Color(0x663F6E52),minOf(cw,ch)*.47f,center(it))}
        legalTargets.forEach{target->if(chess)drawRect(Color(0x554CAF50),Offset(target.col*cw,target.row*ch),Size(cw,ch))else drawCircle(Color(0x664CAF50),minOf(cw,ch)*.47f,center(target));if(game.cell(target)==null)drawCircle(Color(0xCC2E7D4F),minOf(cw,ch)*.11f,center(target))}
        hint?.let{drawCircle(Color(0xFFFFD54F),minOf(cw,ch)*.22f,center(it))}
        for(r in 0 until game.rows)for(c in 0 until game.cols)game.cell(Pos(r,c))?.let{cell->
            val p=center(Pos(r,c));val radius=minOf(cw,ch)*.38f
            val fill=if(janggi)Color(0xFFF1D39A)else if(cell.owner==1)Color(0xFFF7F3E8)else Color(0xFF343434)
            val ink=if(janggi&&cell.owner==1)Color(0xFF17613D)else if(janggi)Color(0xFFB52B2B)else if(cell.owner==1)Bark else Color.White
            drawCircle(Color.Black.copy(alpha=.22f),radius,Offset(p.x+2f,p.y+3f));drawCircle(fill,radius,p);drawCircle(ink,radius,p,style=Stroke(if(janggi)3f else 2f))
            if(cell.label.isNotEmpty())drawIntoCanvas{canvas->val paint=android.graphics.Paint().apply{color=ink.toArgb();textSize=radius*(if(janggi)1.45f else 1.3f);textAlign=android.graphics.Paint.Align.CENTER;isAntiAlias=true;typeface=if(janggi&&cell.owner==1)choTypeface else if(janggi)hanTypeface else android.graphics.Typeface.DEFAULT_BOLD};canvas.nativeCanvas.drawText(cell.label,p.x,p.y-(paint.ascent()+paint.descent())/2,paint)}
        }
    }}
    }
}

internal fun boardIndex(value:Float,length:Float,count:Int,margin:Float)=if(margin==0f)(value/length*count).toInt().coerceIn(0,count-1)else(((value-margin)/(length-margin*2)*(count-1)).roundToInt()).coerceIn(0,count-1)

@Composable private fun GameOverDialog(title:String,message:String,celebrate:Boolean=true,onRestart:()->Unit,onHome:()->Unit){
    val motion=rememberInfiniteTransition(label="friends")
    val bounce by motion.animateFloat(-7f,7f,infiniteRepeatable(tween(550),RepeatMode.Reverse),label="bounce")
    val tilt by motion.animateFloat(-2f,2f,infiniteRepeatable(tween(700),RepeatMode.Reverse),label="tilt")
    AlertDialog(onDismissRequest={},title={Text(title)},text={Column(horizontalAlignment=Alignment.CenterHorizontally){Text(if(celebrate)"✨  🎊  👏  🎊  ✨" else "💛  🌟  💛",fontSize=26.sp);Image(painterResource(R.drawable.forest_friends),if(celebrate)"축하하는 숲속 친구들" else "위로하는 숲속 친구들",Modifier.fillMaxWidth().height(120.dp).graphicsLayer{translationY=bounce;rotationZ=tilt},contentScale=ContentScale.Fit);Text(message,fontSize=17.sp,textAlign=TextAlign.Center)}},confirmButton={Button(onClick=onRestart){Text("다시 하기")}},dismissButton={TextButton(onClick=onHome){Text("그만하기")}})
}

@Composable private fun ModelScreen(coach:CoachManager,back:()->Unit){Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("🦊 AI 코치",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Bark);Spacer(Modifier.height(20.dp));Text(coach.status,fontSize=18.sp,textAlign=TextAlign.Center);Spacer(Modifier.height(12.dp));if(!coach.ready)Button(onClick={coach.download()}){Text("약 736MB 모델 받기")}else OutlinedButton(onClick={coach.deleteModel()}){Text("모델 지우기")};Text("모델을 받으면 앞 대화를 이어서 기억하고, 게임과 상관없는 질문에도 자유롭게 답하며 경기 중 가끔 먼저 말을 걸어요. 대화는 기기 밖으로 전송되지 않습니다.",fontSize=14.sp,color=Color.Gray,modifier=Modifier.padding(16.dp));Spacer(Modifier.weight(1f));TextButton(back){Text("돌아가기")}}
}

@Composable private fun BlockScreen(prefs:android.content.SharedPreferences,sounds:GameSounds,back:()->Unit,home:()->Unit){
    var itemMode by remember{mutableStateOf<Boolean?>(null)};val game=remember(itemMode){BlockPuzzleGame(itemsEnabled=itemMode==true)};var revision by remember{mutableIntStateOf(0)};var best by remember{mutableIntStateOf(prefs.getInt("block_best",0))}
    var blockCheer by remember(game){mutableStateOf("준비됐지? 신나게 시작해 보자!")};var showItem by remember(game){mutableStateOf(false)};val clearBurst=remember(game){Animatable(1f)};val itemPop=remember(game){Animatable(.7f)}
    val glowMotion=rememberInfiniteTransition(label="blockGlow");val activeGlow by glowMotion.animateFloat(.72f,1f,infiniteRepeatable(tween(420),RepeatMode.Reverse),label="activeGlow")
    LaunchedEffect(revision,game.paused,game.gameOver,itemMode){if(itemMode!=null&&!game.paused&&!game.gameOver){delay(game.dropDelay.toLong());val before=game.clearEvent;if(!game.tick()&&game.clearEvent==before)sounds.block();if(game.score>best){best=game.score;prefs.edit().putInt("block_best",best).apply()};revision++}}
    LaunchedEffect(game.gameOver){if(game.gameOver)sounds.finish()}
    LaunchedEffect(game.level){sounds.blockLevel(game.level)}
    LaunchedEffect(game.itemEvent){if(game.itemEvent>0){val wasPaused=game.paused;game.paused=true;showItem=true;sounds.item();itemPop.snapTo(.7f);itemPop.animateTo(1.08f,tween(220));itemPop.animateTo(1f,tween(120));delay(1_300);showItem=false;game.paused=wasPaused;revision++}}
    LaunchedEffect(game.clearEvent){if(game.clearEvent>0){sounds.clear(game.lastClearedRows.size);blockCheer=if(game.lastClearedRows.size>=4)"와우! 한 번에 네 줄! 완전 멋져!" else "팡! 줄을 지웠어. 리듬 좋은데!";clearBurst.snapTo(0f);clearBurst.animateTo(1f,tween(620,easing=LinearEasing))}}
    LaunchedEffect(game.levelEvent){if(game.levelEvent>0){sounds.menu();blockCheer="레벨 ${game.level}! 바닥 도전 블록 ${game.levelBlocksAdded}줄을 없애 보자!"}}
    LaunchedEffect(game.pieces){if(game.pieces>0&&game.pieces%5==0){delay(700);val cheers=listOf("와우, 착착 쌓이고 있어!","좋아! 지금 리듬 그대로 가자!","오, 빈칸을 잘 찾았네!","멋져! 다음 조각도 부탁해!");blockCheer=cheers[game.pieces/5%cheers.size]}}
    Box(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF284F3E),Color(0xFF10251D)))).padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(back){Text("‹ 홈",color=Color.White)};Text("엄마의 블록 퍼즐 · ${if(itemMode==true)"아이템전" else "노템전"}",modifier=Modifier.weight(1f),color=Color(0xFFFFE0A3),fontSize=20.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center);IconButton(onClick={game.paused=!game.paused;sounds.menu();revision++}){Text(if(game.paused)"▶" else "Ⅱ",color=Color.White,fontSize=22.sp)}}
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xAA244B3B)),shape=RoundedCornerShape(16.dp)){Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=7.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){listOf("점수" to game.score,"최고" to best,"레벨" to game.level).forEach{(label,value)->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text(label,color=Color(0xFFBBD5C5),fontSize=12.sp);Text("$value",color=Color.White,fontWeight=FontWeight.Bold)}};Column(horizontalAlignment=Alignment.CenterHorizontally){Text("다음",color=Color(0xFFBBD5C5),fontSize=12.sp);Canvas(Modifier.size(38.dp)){val unit=size.minDimension/4;game.cells(game.next,0,0,0).forEach{drawRoundRect(blockColors[game.next],Offset(it.col*unit,it.row*unit),Size(unit-2,unit-2),CornerRadius(4f,4f))}}}};Text("다음 레벨 ${game.level+1}까지 ${game.linesToNextLevel}줄",color=Color.White,fontSize=13.sp);LinearProgressIndicator({(game.lines%10)/10f},Modifier.fillMaxWidth().height(5.dp),color=Color(0xFFFFD166),trackColor=Color.White.copy(alpha=.15f));if(itemMode==true){game.itemMessage?.let{Text(it,color=Color(0xFFFFE0A3),fontSize=12.sp,maxLines=1)};Text("🎁 다음 아이템까지 ${game.linesToNextItem}줄",color=Color(0xFFFFE0A3),fontSize=12.sp)}}}
        Text("🦊 $blockCheer",color=Color(0xFFFFE0A3),fontSize=13.sp,maxLines=1,modifier=Modifier.padding(top=4.dp))
        Canvas(Modifier.weight(1f).aspectRatio(.5f).padding(10.dp)){
            val cw=size.width/game.width;val ch=size.height/game.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFF142D24),Color(0xFF091A14))))
            for(c in 0..game.width)drawLine(Color.White.copy(alpha=.07f),Offset(c*cw,0f),Offset(c*cw,size.height),1f)
            for(r in 0..game.height)drawLine(Color.White.copy(alpha=.07f),Offset(0f,r*ch),Offset(size.width,r*ch),1f)
            fun block(row:Int,col:Int,color:Color,active:Boolean=false){val p=Offset(col*cw+1,row*ch+1);val s=Size(cw-2,ch-2);val corner=CornerRadius(minOf(cw,ch)*.18f);drawRoundRect(Color.Black.copy(alpha=.45f),Offset(p.x+2,p.y+3),s,corner);drawRoundRect(Brush.linearGradient(listOf(color.copy(alpha=if(active)activeGlow else .9f),color.copy(alpha=.62f))),p,s,corner);drawLine(Color.White.copy(alpha=if(active)activeGlow else .5f),Offset(p.x+4,p.y+4),Offset(p.x+s.width-4,p.y+4),2f);drawRoundRect(Color.White.copy(alpha=.18f),p,s,corner,style=Stroke(1.5f))}
            for(r in 0 until game.height)for(c in 0 until game.width){val value=game.board[r*game.width+c];if(value!=0)block(r,c,blockColors[value-1])}
            game.cells().filter{it.row>=0}.forEach{block(it.row,it.col,blockColors[game.piece],true)}
            if(clearBurst.value<1f)for(r in game.lastClearedRows){val y=(r+.5f)*ch;val fade=1f-clearBurst.value;drawRect(Brush.horizontalGradient(listOf(Color.Transparent,Color.White.copy(alpha=fade),Color(0xFFFFD54F).copy(alpha=fade),Color.Transparent)),Offset(0f,y-ch*.45f),Size(size.width,ch*.9f));repeat(18){i->val x=(i*37%101)/100f*size.width;val dx=(if(i%2==0)-1 else 1)*clearBurst.value*cw*(1+i%4);val dy=(i%5-2)*clearBurst.value*ch*1.8f;drawCircle(blockColors[i%blockColors.size].copy(alpha=fade),2f+ch*.13f*fade,Offset(x+dx,y+dy))}}
        }
        Row(Modifier.padding(bottom=6.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){BlockButton("←",true){game.move(-1,0);revision++};BlockButton("⇊"){val before=game.clearEvent;game.hardDrop();if(game.clearEvent==before)sounds.block();revision++};BlockButton("↓",true){game.softDrop();revision++};BlockButton("↻"){game.rotate();revision++};BlockButton("→",true){game.move(1,0);revision++}}
        if(game.gameOver)GameOverDialog("도전 끝!","동물 친구들이 응원하러 왔어요!\n점수 ${game.score} · 최고 점수 $best",false,onRestart={game.reset();revision++},onHome=home)
    };if(showItem)Surface(Modifier.align(Alignment.Center).fillMaxWidth(.82f).graphicsLayer{scaleX=itemPop.value;scaleY=itemPop.value},shape=RoundedCornerShape(28.dp),color=Color(0xFFFDF1CC),shadowElevation=24.dp){Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("🎁 아이템 발동!",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Bark);Image(painterResource(R.drawable.forest_friends),"아이템을 사용하는 숲속 동물 친구들",Modifier.fillMaxWidth().height(130.dp),contentScale=ContentScale.Fit);Text(game.itemMessage.orEmpty(),fontSize=18.sp,fontWeight=FontWeight.Bold,color=Forest,textAlign=TextAlign.Center)}}}
    if(itemMode==null)AlertDialog(onDismissRequest=home,title={Text("게임 방식을 골라 주세요")},text={Text("아이템전은 4줄을 지울 때마다 숲속 아이템이 자동으로 나와요.")},confirmButton={Button(onClick={itemMode=true;revision++}){Text("🎁 아이템전")}},dismissButton={OutlinedButton(onClick={itemMode=false;revision++}){Text("노템전")}})
}
@Composable private fun BlockButton(text:String,repeat:Boolean=false,action:()->Unit){val currentAction by rememberUpdatedState(action);var pressed by remember{mutableStateOf(false)};Surface(Modifier.size(58.dp).graphicsLayer{scaleX=if(pressed).93f else 1f;scaleY=scaleX}.semantics{role=Role.Button;onClick{currentAction();true}}.pointerInput(repeat){detectTapGestures(onPress={coroutineScope{pressed=true;currentAction();val job=if(repeat)launch{delay(160);while(true){currentAction();delay(55)}}else null;try{tryAwaitRelease()}finally{job?.cancel();pressed=false}}})},shape=RoundedCornerShape(16.dp),color=if(pressed)Color(0xFFF0A45A)else Color(0xFFD88B45),shadowElevation=if(pressed)2.dp else 6.dp){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(text,fontSize=24.sp,color=Color.White)}}}
private val blockColors=listOf(Color.Cyan,Color.Yellow,Color(0xFFAB47BC),Color.Green,Color.Red,Color.Blue,Color(0xFFFF9800))
