@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.brainyjongmin.forestboardfriends

import android.app.Activity
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.roundToInt

private val Forest = Color(0xFF3F6E52)
private val Cream = Color(0xFFFFF8E8)
private val Bark = Color(0xFF6D4C41)
private class GameSounds{
    private val tone=runCatching{ToneGenerator(AudioManager.STREAM_MUSIC,45)}.getOrNull()
    fun move(){tone?.startTone(ToneGenerator.TONE_PROP_BEEP,65)}
    fun menu(){tone?.startTone(ToneGenerator.TONE_PROP_ACK,90)}
    fun block(){tone?.startTone(ToneGenerator.TONE_PROP_BEEP2,100)}
    fun close(){tone?.release()}
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
        MaterialTheme(colorScheme=lightColorScheme(primary=Forest,background=Cream)){Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),color=Cream){Column(Modifier.padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
            Image(painterResource(R.drawable.forest_friends),null,Modifier.fillMaxWidth().height(220.dp),contentScale=ContentScale.Fit)
            Text("숲속 보드 친구들",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Bark)
            Spacer(Modifier.height(20.dp));Text("친구야, 이름을 알려줘!",fontSize=20.sp)
            OutlinedTextField(draft,{draft=it.take(12)},singleLine=true,label={Text("이름")},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done))
            Spacer(Modifier.height(12.dp));Button(onClick={val n=draft.trim();if(n.isNotEmpty()){prefs.edit().putString("player_name",n).apply();playerName=n}},enabled=draft.trim().isNotEmpty()){Text("숲속으로 들어가기")}
            Text("이름은 이 기기에만 저장돼요.",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=12.dp))
        }}};return
    }
    MaterialTheme(colorScheme=lightColorScheme(primary=Forest,secondary=Color(0xFFD88B45),background=Cream,surface=Color.White)){AppContent(playerName,{n->prefs.edit().putString("player_name",n).apply();playerName=n},prefs)}
}

@Composable private fun AppContent(name:String,rename:(String)->Unit,prefs:android.content.SharedPreferences){
    var page by remember{mutableStateOf(Page.HOME)};var kind by remember{mutableStateOf(GameKind.OMOK)}
    var onePlayer by remember{mutableStateOf(true)};var difficulty by remember{mutableStateOf(Difficulty.EASY)};var boardSize by remember{mutableIntStateOf(9)}
    var confirmGameExit by remember{mutableStateOf(false)};var lastBack by remember{mutableLongStateOf(0L)}
    val context=LocalContext.current;val coach=remember{CoachManager(context)};val sounds=remember{GameSounds()};DisposableEffect(Unit){onDispose{coach.close();sounds.close()}}
    LaunchedEffect(name){coach.speak("$name, 숲속 보드 친구들에 온 걸 환영해! 오늘도 즐겁게 놀아 보자!")}
    fun leaveGame(){if(!confirmGameExit)sounds.menu();confirmGameExit=true}
    fun open(next:Page){sounds.menu();page=next}
    BackHandler{when(page){Page.GAME,Page.BLOCK->leaveGame();Page.HOME->{val now=SystemClock.elapsedRealtime();if(now-lastBack<2_000)(context as? Activity)?.finish()else{lastBack=now;sounds.menu();Toast.makeText(context,"한 번 더 누르면 종료돼요.",Toast.LENGTH_SHORT).show()}};else->open(Page.HOME)}}
    Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),color=Cream){when(page){
        Page.HOME->HomeScreen(name,onSelect={kind=it;open(if(it==GameKind.BLOCK)Page.BLOCK else Page.SETUP)},onModel={open(Page.MODEL)},onRename=rename)
        Page.SETUP->SetupScreen(kind,onePlayer,{onePlayer=it},difficulty,{difficulty=it},boardSize,{boardSize=it},{open(Page.HOME)},{open(Page.GAME)})
        Page.GAME->BoardGameScreen(name,kind,onePlayer,difficulty,boardSize,coach,sounds::move,::leaveGame)
        Page.BLOCK->BlockScreen(prefs,sounds,::leaveGame)
        Page.MODEL->ModelScreen(coach){open(Page.HOME)}
    }}
    if(confirmGameExit)AlertDialog(onDismissRequest={confirmGameExit=false},title={Text("게임을 종료할까요?")},text={Text("지금 게임을 끝내고 홈으로 돌아갈 수 있어요.")},confirmButton={Button(onClick={confirmGameExit=false;page=Page.HOME}){Text("종료하기")}},dismissButton={TextButton(onClick={confirmGameExit=false}){Text("돌아가기")}})
}

@Composable private fun HomeScreen(name:String,onSelect:(GameKind)->Unit,onModel:()->Unit,onRename:(String)->Unit){
    var editing by remember{mutableStateOf(false)};var draft by remember(name){mutableStateOf(name)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Image(painterResource(R.drawable.forest_friends),null,Modifier.fillMaxWidth().height(185.dp),contentScale=ContentScale.Fit)
        Text("$name, 오늘은 뭘 해볼까?",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Bark,textAlign=TextAlign.Center)
        Spacer(Modifier.height(16.dp));GameKind.entries.take(4).chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){row.forEach{g->Card(Modifier.weight(1f).height(120.dp).clickable{onSelect(g)},shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(g.emoji,fontSize=38.sp);Text(g.title,fontSize=21.sp,fontWeight=FontWeight.Bold,color=Forest)}}};if(row.size==1)Spacer(Modifier.weight(1f))};Spacer(Modifier.height(12.dp))}
        Card(Modifier.fillMaxWidth().height(92.dp).clickable{onSelect(GameKind.BLOCK)},colors=CardDefaults.cardColors(containerColor=Color(0xFFE9D9BC)),shape=RoundedCornerShape(22.dp)){Row(Modifier.fillMaxSize().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Text("▦",fontSize=40.sp);Spacer(Modifier.width(16.dp));Column{Text("엄마의 블록 퍼즐",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Bark);Text("숲속 레트로 무한 모드")}}}
        Row(Modifier.padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onModel){Text("🦊 AI 코치")};OutlinedButton(onClick={editing=true}){Text("이름 바꾸기")}}
    }
    if(editing)AlertDialog(onDismissRequest={editing=false},title={Text("이름 바꾸기")},text={OutlinedTextField(draft,{draft=it.take(12)},singleLine=true)},confirmButton={Button(onClick={if(draft.trim().isNotEmpty()){onRename(draft.trim());editing=false}}){Text("저장")}},dismissButton={TextButton(onClick={editing=false}){Text("취소")}})
}

@Composable private fun SetupScreen(kind:GameKind,one:Boolean,setOne:(Boolean)->Unit,difficulty:Difficulty,setDifficulty:(Difficulty)->Unit,size:Int,setSize:(Int)->Unit,back:()->Unit,start:()->Unit){
    Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("${kind.emoji} ${kind.title}",fontSize=32.sp,fontWeight=FontWeight.Bold,color=Bark);Spacer(Modifier.height(28.dp));Choice("함께 할 사람",listOf("동물 친구와 1인용","한 기기 2인용"),if(one)0 else 1){setOne(it==0)};if(one){Choice("난이도",listOf("쉬움","보통"),difficulty.ordinal){setDifficulty(Difficulty.entries[it])}};if(kind==GameKind.GO)Choice("바둑판",listOf("9×9","13×13","19×19"),listOf(9,13,19).indexOf(size)){setSize(listOf(9,13,19)[it])};Spacer(Modifier.weight(1f));Button(start,Modifier.fillMaxWidth().height(58.dp)){Text("게임 시작",fontSize=20.sp)};TextButton(back){Text("돌아가기")}}
}
@Composable private fun Choice(title:String,items:List<String>,selected:Int,onSelect:(Int)->Unit){Text(title,fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth().padding(vertical=8.dp));SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){items.forEachIndexed{i,s->SegmentedButton(selected==i,{onSelect(i)},SegmentedButtonDefaults.itemShape(i,items.size)){Text(s)}}};Spacer(Modifier.height(14.dp))}

@Composable private fun BoardGameScreen(name:String,kind:GameKind,one:Boolean,difficulty:Difficulty,size:Int,coach:CoachManager,moveSound:()->Unit,back:()->Unit){
    val game=remember(kind,size){when(kind){GameKind.GO->GoGame(size);GameKind.JANGGI->JanggiGame();GameKind.CHESS->ChessGame();else->OmokGame()}}
    var selected by remember{mutableStateOf<Pos?>(null)};var revision by remember{mutableIntStateOf(0)};var hint by remember{mutableStateOf<Pos?>(null)};var speech by remember{mutableStateOf("$name, 천천히 생각해 보자!")};var question by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    fun maybeAi(){if(one&&game.winner==null&&game.currentPlayer==2)scope.launch{delay(350);game.hint(difficulty)?.let{game.play(it);moveSound();speech="$name, 내 차례가 끝났어. 이제 네 차례야!";revision++}}}
    LaunchedEffect(Unit){maybeAi()}
    Column(Modifier.fillMaxSize().padding(horizontal=12.dp),horizontalAlignment=Alignment.CenterHorizontally){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(back){Text("‹ 홈")};Text(kind.title,fontSize=24.sp,fontWeight=FontWeight.Bold,color=Bark,modifier=Modifier.weight(1f),textAlign=TextAlign.Center);Spacer(Modifier.width(55.dp))};Text(game.status,fontSize=18.sp,fontWeight=FontWeight.Bold,color=Forest);Spacer(Modifier.height(8.dp))
        GameBoard(game,selected,hint,revision){p->if(game.winner!=null||one&&game.currentPlayer==2)return@GameBoard;val from=selected;val move=if(kind==GameKind.GO||kind==GameKind.OMOK)GameMove(to=p)else GameMove(from,p);if(game.play(move)){moveSound();selected=null;hint=null;speech="$name, 좋은 수야!";revision++;maybeAi()}else if(game.cell(p)?.owner==game.currentPlayer)selected=p else speech="$name, 그곳에는 둘 수 없어."}
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedButton(onClick={if(game.undo()){if(one)game.undo();selected=null;hint=null;revision++}}){Text("무르기")};if(kind==GameKind.GO||kind==GameKind.JANGGI)OutlinedButton(onClick={if(game.pass()){revision++;maybeAi()}}){Text("한 수 쉼")};Button(onClick={val m=game.hint(difficulty);hint=m?.to;speech=if(m==null)"지금은 추천할 수가 없어." else "$name, 빛나는 칸을 살펴봐!"}){Text("힌트")}}
        Card(Modifier.fillMaxWidth().padding(top=8.dp),colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(12.dp)){Text("🦊 $speech",fontSize=16.sp);Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(question,{question=it.take(120)},Modifier.weight(1f),placeholder={Text("게임이나 오늘 일 물어보기")},singleLine=true);IconButton(onClick={val q=question.trim();if(q.isNotEmpty()){speech="생각 중...";coach.answer(name,kind.title,q,"현재 차례는 ${game.status}"){speech=it}}}){Text("➤",fontSize=22.sp)}}}}
    }
}

@Composable private fun ColumnScope.GameBoard(game:BoardGame,selected:Pos?,hint:Pos?,revision:Int,onTap:(Pos)->Unit){
    val chess=game is ChessGame;val line=Color(0xFF5D4037)
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
    val ratio=game.cols.toFloat()/game.rows
    val boardSize=if(maxWidth/ratio<=maxHeight)Modifier.fillMaxWidth().aspectRatio(ratio) else Modifier.fillMaxHeight().aspectRatio(ratio)
    Canvas(boardSize.padding(4.dp).pointerInput(game,revision){detectTapGestures{point->val margin=if(chess)0f else minOf(size.width.toFloat()/game.cols,size.height.toFloat()/game.rows)*.5f;onTap(Pos(boardIndex(point.y,size.height.toFloat(),game.rows,margin),boardIndex(point.x,size.width.toFloat(),game.cols,margin)))}}){
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
        hint?.let{drawCircle(Color(0xFFFFD54F),minOf(cw,ch)*.22f,center(it))}
        for(r in 0 until game.rows)for(c in 0 until game.cols)game.cell(Pos(r,c))?.let{cell->val p=center(Pos(r,c));val radius=minOf(cw,ch)*.38f;drawCircle(Color.Black.copy(alpha=.22f),radius,Offset(p.x+2f,p.y+3f));drawCircle(if(cell.owner==1)Color(0xFFF7F3E8)else Color(0xFF343434),radius,p);drawCircle(if(cell.owner==1)Bark else Color.White,radius,p,style=Stroke(2f));if(cell.label.isNotEmpty())drawIntoCanvas{canvas->val paint=android.graphics.Paint().apply{color=if(cell.owner==1)android.graphics.Color.rgb(100,55,40)else android.graphics.Color.WHITE;textSize=radius*1.3f;textAlign=android.graphics.Paint.Align.CENTER;isAntiAlias=true;typeface=android.graphics.Typeface.DEFAULT_BOLD};canvas.nativeCanvas.drawText(cell.label,p.x,p.y-(paint.ascent()+paint.descent())/2,paint)}}
    }
    }
}

internal fun boardIndex(value:Float,length:Float,count:Int,margin:Float)=if(margin==0f)(value/length*count).toInt().coerceIn(0,count-1)else(((value-margin)/(length-margin*2)*(count-1)).roundToInt()).coerceIn(0,count-1)

@Composable private fun ModelScreen(coach:CoachManager,back:()->Unit){Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("🦊 AI 코치",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Bark);Spacer(Modifier.height(20.dp));Text(coach.status,fontSize=18.sp,textAlign=TextAlign.Center);Spacer(Modifier.height(12.dp));if(!coach.ready)Button(onClick={coach.download()}){Text("약 736MB 모델 받기")}else OutlinedButton(onClick={coach.deleteModel()}){Text("모델 지우기")};Text("게임뿐 아니라 학교, 취미, 기분 같은 가벼운 일상대화도 할 수 있습니다. 대화는 기기 밖으로 전송되지 않습니다.",fontSize=14.sp,color=Color.Gray,modifier=Modifier.padding(16.dp));Spacer(Modifier.weight(1f));TextButton(back){Text("돌아가기")}}
}

@Composable private fun BlockScreen(prefs:android.content.SharedPreferences,sounds:GameSounds,back:()->Unit){
    val game=remember{BlockPuzzleGame()};var revision by remember{mutableIntStateOf(0)};var best by remember{mutableIntStateOf(prefs.getInt("block_best",0))}
    LaunchedEffect(revision,game.paused,game.gameOver){if(!game.paused&&!game.gameOver){delay((750-(game.level-1)*55).coerceAtLeast(100).toLong());if(!game.tick())sounds.block();if(game.score>best){best=game.score;prefs.edit().putInt("block_best",best).apply()};revision++}}
    LaunchedEffect(game.gameOver){if(game.gameOver)sounds.menu()}
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF284F3E),Color(0xFF10251D)))).padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(back){Text("‹ 홈",color=Color.White)};Text("엄마의 블록 퍼즐",modifier=Modifier.weight(1f),color=Color(0xFFFFE0A3),fontSize=22.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center);IconButton(onClick={game.paused=!game.paused;sounds.menu();revision++}){Text(if(game.paused)"▶" else "Ⅱ",color=Color.White,fontSize=22.sp)}}
        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color(0xAA244B3B)),shape=RoundedCornerShape(16.dp)){Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){listOf("점수" to game.score,"최고" to best,"레벨" to game.level).forEach{(label,value)->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text(label,color=Color(0xFFBBD5C5),fontSize=12.sp);Text("$value",color=Color.White,fontWeight=FontWeight.Bold)}};Column(horizontalAlignment=Alignment.CenterHorizontally){Text("다음",color=Color(0xFFBBD5C5),fontSize=12.sp);Canvas(Modifier.size(42.dp)){val unit=size.minDimension/4;game.cells(game.next,0,0,0).forEach{drawRoundRect(blockColors[game.next],Offset(it.col*unit,it.row*unit),Size(unit-2,unit-2),CornerRadius(4f,4f))}}}}}
        Canvas(Modifier.weight(1f).aspectRatio(.5f).padding(10.dp)){
            val cw=size.width/game.width;val ch=size.height/game.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFF142D24),Color(0xFF091A14))))
            for(c in 0..game.width)drawLine(Color.White.copy(alpha=.07f),Offset(c*cw,0f),Offset(c*cw,size.height),1f)
            for(r in 0..game.height)drawLine(Color.White.copy(alpha=.07f),Offset(0f,r*ch),Offset(size.width,r*ch),1f)
            fun block(row:Int,col:Int,color:Color,active:Boolean=false){val p=Offset(col*cw+1,row*ch+1);val s=Size(cw-2,ch-2);val corner=CornerRadius(minOf(cw,ch)*.18f);drawRoundRect(Color.Black.copy(alpha=.45f),Offset(p.x+2,p.y+3),s,corner);drawRoundRect(Brush.linearGradient(listOf(color.copy(alpha=if(active)1f else .9f),color.copy(alpha=.62f))),p,s,corner);drawLine(Color.White.copy(alpha=.5f),Offset(p.x+4,p.y+4),Offset(p.x+s.width-4,p.y+4),2f);drawRoundRect(Color.White.copy(alpha=.16f),p,s,corner,style=Stroke(1.5f))}
            for(r in 0 until game.height)for(c in 0 until game.width){val value=game.board[r*game.width+c];if(value!=0)block(r,c,blockColors[value-1])}
            game.cells().filter{it.row>=0}.forEach{block(it.row,it.col,blockColors[game.piece],true)}
        }
        Row(Modifier.padding(bottom=6.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){BlockButton("←"){game.move(-1,0);revision++};BlockButton("↻"){game.rotate();revision++};RepeatBlockButton("↓"){game.softDrop();revision++};BlockButton("⇊"){game.hardDrop();sounds.block();revision++};BlockButton("→"){game.move(1,0);revision++}}
        if(game.gameOver)AlertDialog(onDismissRequest={},title={Text("게임 끝!")},text={Text("점수 ${game.score}\n최고 점수 $best")},confirmButton={Button(onClick={game.reset();revision++}){Text("다시 하기")}},dismissButton={TextButton(back){Text("홈")}})
    }
}
@Composable private fun BlockButton(text:String,action:()->Unit)=Button(action,contentPadding=PaddingValues(0.dp),modifier=Modifier.size(58.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFD88B45)),elevation=ButtonDefaults.buttonElevation(defaultElevation=6.dp)){Text(text,fontSize=24.sp)}
@Composable private fun RepeatBlockButton(text:String,action:()->Unit){val currentAction by rememberUpdatedState(action);Surface(Modifier.size(58.dp).semantics{role=Role.Button;onClick{currentAction();true}}.pointerInput(Unit){detectTapGestures(onPress={currentAction();coroutineScope{val repeat=launch{delay(250);while(true){currentAction();delay(75)}};tryAwaitRelease();repeat.cancel()}})},shape=RoundedCornerShape(16.dp),color=Color(0xFFD88B45),shadowElevation=6.dp){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(text,fontSize=24.sp,color=Color.White)}}}
private val blockColors=listOf(Color.Cyan,Color.Yellow,Color(0xFFAB47BC),Color.Green,Color.Red,Color.Blue,Color(0xFFFF9800))
