package io.github.brainyjongmin.forestboardfriends

import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.*

private val themes=listOf(listOf(Color(0xFFCBEED8),Color(0xFF518765)),listOf(Color(0xFFF7D9CF),Color(0xFF9A6576)),listOf(Color(0xFF394A76),Color(0xFF152340)))
private val themeNames=listOf("햇살 정원","버섯 숲","별빛 숲")

@Composable internal fun MoleScreen(prefs:SharedPreferences,externalPaused:Boolean,back:()->Unit,home:()->Unit) {
    var level by remember { mutableStateOf<Int?>(null) }
    var round by remember { mutableIntStateOf(0) }
    var unlocked by remember { mutableIntStateOf(prefs.getInt("mole_unlocked",1).coerceIn(1,12)) }
    if(level==null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            TextButton(home,Modifier.align(Alignment.Start)){Text("‹ 홈")}
            Text("숲속 두더지 팡!",fontSize=30.sp,fontWeight=FontWeight.Black,color=Color(0xFF3C654B))
            MoleBoard(emptyList(),0,0,Modifier.height(180.dp).widthIn(max=450.dp).fillMaxWidth(),preview=true){}
            Text("나오면 톡! 두 손으로 팡!",fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text("30초 안에 목표만큼 잡아 봐!\n황금 친구는 보너스 · 헬멧 친구는 두 번 톡",textAlign=TextAlign.Center,modifier=Modifier.padding(12.dp))
            (0..2).forEach{theme->
                Text(themeNames[theme],fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=14.dp,bottom=6.dp))
                Row(Modifier.widthIn(max=650.dp).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach{offset->val stage=theme*4+offset
                        FilledTonalButton(onClick={level=stage;round++},enabled=stage<=unlocked,modifier=Modifier.weight(1f).height(70.dp).testTag("mole-stage-$stage"),contentPadding=PaddingValues(2.dp)) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally){Text(if(stage<=unlocked)"$stage" else "잠김");Text("★".repeat(prefs.getInt("mole_stars_$stage",0)),fontSize=12.sp)}
                        }
                    }
                }
            }
        }
        return
    }
    val game=remember(level,round){MoleGame(level!!)}
    val context=LocalContext.current;val view=LocalView.current
    val audio=remember { MoleAudio(context,prefs.getInt("music_volume",70)/100f,prefs.getInt("sound_volume",70)/100f) }
    var foreground by remember { mutableStateOf((context as ComponentActivity).lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var paused by remember(game){mutableStateOf(false)}
    var time by remember(game){mutableLongStateOf(0)}
    var revision by remember(game){mutableIntStateOf(0)}
    val phase=remember(time,game){game.phase}
    val moles=remember(time,revision,game){game.moles}
    val running=foreground&&!paused&&!externalPaused&&phase!=MolePhase.FINISHED
    DisposableEffect(audio) {onDispose{audio.close()}}
    DisposableEffect(Unit) {
        val lifecycle=(context as ComponentActivity).lifecycle
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE){foreground=false;paused=true}else if(event==Lifecycle.Event.ON_RESUME)foreground=true}
        lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(game,running) {
        game.paused=!running;audio.running(running)
        if(running) {
            var previous=withFrameNanos{it}
            while(game.phase!=MolePhase.FINISHED) {
                val stamp=withFrameNanos{it};val delta=(stamp-previous)/1_000_000
                previous+=delta*1_000_000
                val before=game.spawned;game.advance(delta);time=game.time
                if(game.spawned>before)audio.play("pop",.9f+(game.level%4)*.04f)
            }
        }
    }
    LaunchedEffect(phase) {
        if(phase==MolePhase.FINISHED) {
            audio.running(false);audio.play(if(game.cleared)"win" else "end")
            val edit=prefs.edit().putInt("mole_best_${game.level}",maxOf(game.score,prefs.getInt("mole_best_${game.level}",0)))
            if(game.cleared){unlocked=maxOf(unlocked,(game.level+1).coerceAtMost(12));edit.putInt("mole_unlocked",unlocked).putInt("mole_stars_${game.level}",maxOf(game.stars,prefs.getInt("mole_stars_${game.level}",0)))}
            edit.apply()
        }
    }
    val hit:(Int)->Unit={hole->if(running){val event=game.hit(hole);if(event!=null){revision++;if(event.type!=null){audio.play(when(event.type){MoleType.NORMAL->"hit";MoleType.HELMET->"helmet";MoleType.GOLD->"gold"},1f+(game.combo.coerceAtMost(20)*.008f));moleHaptic(view,event.type,prefs.getBoolean("game_vibration",true))}else audio.play("miss")}}}
    val theme=(game.level-1)/4
    val panel:@Composable ()->Unit={Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp).background(Color(0xDD193D31),RoundedCornerShape(24.dp)).padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Text("${themeNames[theme]} · ${game.level}단계",color=Color.White,fontWeight=FontWeight.Bold,fontSize=20.sp)
        Text("${(game.remaining+999)/1000}초",color=Color(0xFFFFE299),fontSize=32.sp,fontWeight=FontWeight.Black,modifier=Modifier.testTag("mole-time"))
        Text("잡은 친구 ${game.caught} / ${game.target}",color=Color.White,fontSize=19.sp,modifier=Modifier.testTag("mole-progress"))
        LinearProgressIndicator(progress={game.caught.toFloat()/game.target},modifier=Modifier.fillMaxWidth().padding(vertical=8.dp),color=Color(0xFFFFD66F))
        Text("${game.score}점  ·  ${game.combo} 콤보",color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.testTag("mole-score"))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(back){Text("‹ 홈",color=Color.White)};OutlinedButton(onClick={paused=true},enabled=phase!=MolePhase.FINISHED){Text("잠깐 쉬기",color=Color.White)}}
    }}
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(themes[theme]))) {
        if(maxWidth>maxHeight)Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){
            MoleBoard(moles,time,theme,Modifier.weight(1f).fillMaxHeight(),onHit=hit)
            Box(Modifier.width(240.dp).fillMaxHeight(),contentAlignment=Alignment.Center){panel()}
        }else Column(Modifier.fillMaxSize()){panel();MoleBoard(moles,time,theme,Modifier.weight(1f).fillMaxWidth(),onHit=hit)}
        if(phase==MolePhase.READY)Surface(Modifier.align(Alignment.Center),shape=RoundedCornerShape(30.dp),color=Color(0xEEFFF5D8)){Column(Modifier.padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("준비됐지?",fontSize=24.sp);Text("${game.countdown}",fontSize=62.sp,fontWeight=FontWeight.Black,color=Color(0xFFCC7953))}}
    }
    if(paused&&!externalPaused&&phase!=MolePhase.FINISHED)AlertDialog(onDismissRequest={paused=false},title={Text("잠깐 쉬는 중")},text={Text("준비되면 다시 같이 놀자!")},confirmButton={Button(onClick={paused=false}){Text("계속하기")}},dismissButton={TextButton(back){Text("홈으로")}})
    if(phase==MolePhase.FINISHED)AlertDialog(onDismissRequest={},title={Text(if(game.cleared)"참 잘했어! 단계 성공!" else "조금만 더! 다시 해 볼까?")},text={Column(horizontalAlignment=Alignment.CenterHorizontally){
        MoleBoard(emptyList(),time,theme,Modifier.fillMaxWidth().height(140.dp),preview=true){}
        Text(if(game.cleared)"★".repeat(game.stars) else "끝까지 도전해서 멋져!",fontSize=28.sp,color=Color(0xFFE5A12F))
        Text("${game.score}점 · 최대 ${game.maxCombo}콤보\n잡은 친구 ${game.caught}/${game.target}\n최고 ${maxOf(game.score,prefs.getInt("mole_best_${game.level}",0))}점",textAlign=TextAlign.Center)
        TextButton(onClick={level=null}){Text("스테이지 선택")}
    }},confirmButton={Row{if(game.cleared&&game.level<12)Button(onClick={level=game.level+1;round++}){Text("다음 레벨")};TextButton(onClick={round++}){Text("다시 하기")}}},dismissButton={TextButton(home){Text("홈으로")}})
}

@Composable internal fun MoleBoard(moles:List<Mole>,time:Long,theme:Int,modifier:Modifier=Modifier,preview:Boolean=false,onHit:(Int)->Unit) {
    val latestHit by rememberUpdatedState(onHit)
    BoxWithConstraints(modifier.padding(8.dp),contentAlignment=Alignment.Center) {
        val side=minOf(maxWidth,maxHeight)
        Box(Modifier.size(side).testTag("mole-board")) {
            Canvas(Modifier.fillMaxSize().pointerInput(preview){if(!preview)awaitPointerEventScope{while(true){val event=awaitPointerEvent();event.changes.forEach{change->if(change.pressed&&!change.previousPressed){val x=change.position.x/size.width;val y=change.position.y/size.height;if(x in 0f..1f&&y in 0f..1f){latestHit((y*3).toInt().coerceAtMost(2)*3+(x*3).toInt().coerceAtMost(2));change.consume()}}}}}}) {
                val unit=size.width/3
                drawRoundRect(Brush.verticalGradient(listOf(themes[theme][0].copy(alpha=.9f),themes[theme][1])),cornerRadius=CornerRadius(unit*.18f))
                repeat(22){i->val p=Offset(((i*71+25)%300)/300f*size.width,((i*43+17)%300)/300f*size.height);drawCircle(if(theme==2)Color(0xAAFFE8A1)else Color.White.copy(alpha=.2f),unit*.018f,p)}
                repeat(9){hole->val x=(hole%3+.5f)*unit;val y=(hole/3+.74f)*unit
                    val mole=if(preview)Mole(hole,hole,if(hole==4)MoleType.GOLD else MoleType.NORMAL,0,Long.MAX_VALUE)else moles.firstOrNull{it.hole==hole}
                    drawOval(Color(0x33412B25),Offset(x-unit*.39f,y-unit*.04f),Size(unit*.78f,unit*.24f))
                    drawOval(Color(0xFFB39168),Offset(x-unit*.38f,y-unit*.11f),Size(unit*.76f,unit*.27f))
                    drawOval(Color(0xFF3E2C2D),Offset(x-unit*.32f,y-unit*.09f),Size(unit*.64f,unit*.18f))
                    if(mole!=null){
                        val age=time-mole.born;val struck=mole.hitAt>=0&&time-mole.hitAt<230
                        val hitProgress=if(struck)(time-mole.hitAt)/230f else 1f
                        val appear=if(preview)1f else (age/150f).coerceIn(0f,1f)
                        val disappear=if(preview)1f else if(mole.caughtAt!=null)(1-(time-mole.caughtAt)/260f).coerceIn(0f,1f)else ((mole.expires-time)/180f).coerceIn(0f,1f)
                        val emerge=appear*disappear;val bob=sin(time/260.0+hole).toFloat()*unit*.014f
                        clipRect(x-unit*.45f,y-unit*.74f,x+unit*.45f,y+unit*.03f){
                            val cy=y-unit*.30f*emerge+bob
                            scale(1f+if(struck).12f*(1-hitProgress)else 0f,1f-if(struck).18f*(1-hitProgress)else 0f,Offset(x,y)){
                                moleCharacter(Offset(x,cy),unit,mole.type,mole.hits,((time/90+hole*13)%43==0L),struck,theme)
                            }
                        }
                        drawArc(Color(0xFFD2B486),0f,180f,false,Offset(x-unit*.34f,y-unit*.105f),Size(unit*.68f,unit*.20f),style=Stroke(unit*.032f))
                        if(struck){
                            repeat(7){i->val a=i*2*PI/7;val distance=unit*(.18f+hitProgress*.35f);val p=Offset(x+cos(a).toFloat()*distance,y-unit*.35f+sin(a).toFloat()*distance);star(p,unit*.038f*(1-hitProgress),Color(0xFFFFDA66).copy(alpha=1-hitProgress))}
                            rotate(-28f+hitProgress*45f,Offset(x+unit*.18f,y-unit*.38f)){
                                drawRoundRect(Color(0xFFE9BD80),Offset(x+unit*.14f,y-unit*.40f),Size(unit*.07f,unit*.28f),CornerRadius(unit*.03f))
                                drawRoundRect(Color(0xFFEA8F91),Offset(x-unit*.02f,y-unit*.48f),Size(unit*.36f,unit*.15f),CornerRadius(unit*.055f))
                                drawRoundRect(Color(0xFFFFC3B0),Offset(x-unit*.02f,y-unit*.48f),Size(unit*.06f,unit*.15f),CornerRadius(unit*.025f))
                            }
                            if(mole.points>0)drawIntoCanvas{canvas->val paint=android.graphics.Paint().apply{isAntiAlias=true;color=android.graphics.Color.WHITE;textSize=unit*.15f;typeface=android.graphics.Typeface.DEFAULT_BOLD;textAlign=android.graphics.Paint.Align.CENTER;setShadowLayer(2f,1f,2f,android.graphics.Color.DKGRAY)};canvas.nativeCanvas.drawText("+${mole.points}",x,y-unit*(.53f+hitProgress*.22f),paint)}
                        }
                    }
                    if(theme==1){drawLine(Color(0xFFF8E5CB),Offset(x+unit*.38f,y-unit*.04f),Offset(x+unit*.38f,y-unit*.17f),unit*.045f);drawOval(Color(0xFFCE788D),Offset(x+unit*.29f,y-unit*.23f),Size(unit*.18f,unit*.12f))}
                }
            }
            if(!preview)Column(Modifier.fillMaxSize()){repeat(3){r->Row(Modifier.weight(1f)){repeat(3){c->val hole=r*3+c;Box(Modifier.weight(1f).fillMaxHeight().testTag("mole-hole-$hole").semantics{contentDescription="${hole+1}번 두더지 구멍";stateDescription=if(moles.any{it.hole==hole&&it.caughtAt==null})"등장" else "빈 구멍";role=Role.Button;onClick{latestHit(hole);true}})}}}}
        }
    }
}

private fun DrawScope.moleCharacter(center:Offset,u:Float,type:MoleType,hits:Int,blink:Boolean,struck:Boolean,theme:Int){
    val body=if(type==MoleType.GOLD)Color(0xFFFFCC69)else if(theme==2)Color(0xFFB899CD)else Color(0xFFC99978)
    drawCircle(Color(0xFF865E56),u*.093f,center+Offset(-u*.20f,-u*.16f));drawCircle(Color(0xFF865E56),u*.093f,center+Offset(u*.20f,-u*.16f))
    drawCircle(Color(0xFFE6AAA0),u*.054f,center+Offset(-u*.20f,-u*.16f));drawCircle(Color(0xFFE6AAA0),u*.054f,center+Offset(u*.20f,-u*.16f))
    drawOval(Brush.radialGradient(listOf(body,body.copy(red=body.red*.8f,green=body.green*.8f,blue=body.blue*.8f)),center,u*.4f),center-Offset(u*.255f,u*.24f),Size(u*.51f,u*.6f))
    drawOval(Color(0xFFF7DBB7),center+Offset(-u*.15f,u*.07f),Size(u*.30f,u*.27f))
    val ink=Color(0xFF423040)
    for(sign in listOf(-1,1)){val eye=center+Offset(sign*u*.095f,-u*.045f)
        if(blink||struck)drawArc(ink,15f,150f,false,eye-Offset(u*.042f,u*.03f),Size(u*.084f,u*.07f),style=Stroke(u*.017f))else {drawOval(ink,eye-Offset(u*.024f,u*.035f),Size(u*.048f,u*.07f));drawCircle(Color.White,u*.009f,eye+Offset(u*.006f,-u*.014f))}
        drawOval(Color(0xFFEB9D99).copy(alpha=.65f),center+Offset(sign*u*.17f-u*.04f,u*.023f),Size(u*.08f,u*.04f))
        drawOval(body,center+Offset(sign*u*.23f-u*.058f,u*.20f),Size(u*.116f,u*.083f))
    }
    drawOval(Color(0xFF8C5662),center+Offset(-u*.047f,u*.006f),Size(u*.094f,u*.06f))
    drawArc(ink,10f,160f,false,center+Offset(-u*.053f,u*.04f),Size(u*.106f,u*.09f),style=Stroke(u*.012f))
    if(type==MoleType.GOLD){star(center+Offset(0f,-u*.26f),u*.105f,Color(0xFFFFF1B2))}
    if(type==MoleType.HELMET&&hits==0){drawArc(Color(0xFF75BCCB),180f,180f,true,center-Offset(u*.265f,u*.33f),Size(u*.53f,u*.29f));drawRoundRect(Color(0xFF4A94A7),center-Offset(u*.29f,u*.19f),Size(u*.58f,u*.05f),CornerRadius(u*.025f));drawCircle(Color(0xFFFFEBA1),u*.05f,center+Offset(0f,-u*.24f))}
}
private fun DrawScope.star(center:Offset,radius:Float,color:Color){val path=Path();repeat(10){i->val a=-PI/2+i*PI/5;val r=if(i%2==0)radius else radius*.45f;val p=center+Offset(cos(a).toFloat()*r,sin(a).toFloat()*r);if(i==0)path.moveTo(p.x,p.y)else path.lineTo(p.x,p.y)};path.close();drawPath(path,color)}
