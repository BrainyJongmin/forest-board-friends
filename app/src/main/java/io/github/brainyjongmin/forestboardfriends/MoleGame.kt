package io.github.brainyjongmin.forestboardfriends

import kotlin.random.Random

enum class MoleType { NORMAL, GOLD, HELMET, RABBIT }
enum class MolePhase { READY, PLAYING, FINISHED }
data class Mole(val id:Int, val hole:Int, val type:MoleType, val born:Long, val expires:Long,
    val hits:Int=0, val hitAt:Long=-1, val caughtAt:Long?=null, val points:Int=0)
data class MoleHit(val type:MoleType?, val caught:Boolean=false, val points:Int=0)

/** Pure game clock: only active foreground time is supplied by the screen. */
class MoleGame(val level:Int, private val random:Random=Random.Default) {
    init { require(level in 1..12) }
    val interval=(1100-55*(level-1)).coerceAtLeast(450)
    val lifetime=(1600-85*(level-1)).coerceAtLeast(650)
    val target=(30_000.0/interval*.55).toInt()
    var time=0L; private set
    var phase=MolePhase.READY; private set
    var paused=false
    var score=0; private set
    var caught=0; private set
    var spawned=0; private set
    var combo=0; private set
    var maxCombo=0; private set
    var moles:List<Mole> = emptyList(); private set
    private var nextSpawn=3000L
    private var nextId=0
    val remaining get()=(33_000-time).coerceIn(0,30_000)
    val countdown get()=((3000-time+999)/1000).toInt().coerceIn(1,3)
    val cleared get()=phase==MolePhase.FINISHED && caught>=target
    val stars get()=if(!cleared)0 else if(caught*100>=spawned*95)3 else if(caught*100>=spawned*80)2 else 1

    fun advance(deltaMs:Long) {
        require(deltaMs>=0)
        if(paused || phase==MolePhase.FINISHED)return
        val until=(time+deltaMs).coerceAtMost(33_000)
        // Advance through spawn boundaries so frame delays cannot change the level schedule.
        while(nextSpawn<=until && nextSpawn<33_000) {
            time=nextSpawn;expire()
            if(time+lifetime<=33_000 && moles.size<3) {
                val free=(0..8).filter{hole->moles.none{it.hole==hole}}
                val roll=random.nextInt(100)
                val type=when {level>=2 && roll<12->MoleType.RABBIT;level>=5 && roll<27->MoleType.HELMET;level>=3 && roll in 27..41->MoleType.GOLD;else->MoleType.NORMAL}
                moles=moles+Mole(nextId++,free.random(random),type,time,time+lifetime)
                if(type!=MoleType.RABBIT)spawned++
            }
            nextSpawn+=interval
        }
        time=until;expire()
        phase=when{time>=33_000->MolePhase.FINISHED;time>=3000->MolePhase.PLAYING;else->MolePhase.READY}
    }

    private fun expire() {
        if(moles.any{it.type!=MoleType.RABBIT && it.caughtAt==null && time>=it.expires})combo=0
        moles=moles.filter{time<(it.caughtAt?.plus(260)?:it.expires)}
    }

    fun hit(hole:Int):MoleHit? {
        if(paused || phase!=MolePhase.PLAYING || hole !in 0..8)return null
        val mole=moles.firstOrNull{it.hole==hole}
        if(mole?.caughtAt!=null)return null
        if(mole==null) {combo=0;score=(score-20).coerceAtLeast(0);return MoleHit(null)}
        if(mole.type==MoleType.RABBIT) {
            combo=0;score=(score-150).coerceAtLeast(0)
            moles=moles.map{if(it.id==mole.id)it.copy(hits=1,hitAt=time,caughtAt=time,points=-150)else it}
            return MoleHit(MoleType.RABBIT,true,-150)
        }
        // One helmet strike per physical press, with a tiny guard against simultaneous duplicate pointers.
        if(mole.hitAt>=0 && time-mole.hitAt<70)return null
        val finished=mole.type!=MoleType.HELMET || mole.hits==1
        var points=0
        if(finished) {
            caught++;combo++;maxCombo=maxOf(combo,maxCombo)
            val base=when(mole.type){MoleType.NORMAL->100;MoleType.HELMET->150;MoleType.GOLD->300;MoleType.RABBIT->error("handled above")}
            points=base*(2+(combo/5).coerceAtMost(4))/2;score+=points
        }
        moles=moles.map{if(it.id==mole.id)it.copy(hits=it.hits+1,hitAt=time,caughtAt=if(finished)time else null,points=points)else it}
        return MoleHit(mole.type,finished,points)
    }
}
