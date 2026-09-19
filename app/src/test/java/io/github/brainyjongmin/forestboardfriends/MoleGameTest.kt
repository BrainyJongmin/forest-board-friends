package io.github.brainyjongmin.forestboardfriends

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MoleGameTest {
    @Test fun countdownPauseAndDuplicateHit() {
        val g=MoleGame(1,Random(3));assertNull(g.hit(0))
        g.advance(3000);val m=g.moles.single();assertEquals(MolePhase.PLAYING,g.phase)
        assertTrue(g.hit(m.hole)!!.caught);assertEquals(100,g.score);assertNull(g.hit(m.hole));assertEquals(1,g.caught)
        g.paused=true;g.advance(20_000);assertEquals(3000L,g.time);assertNull(g.hit(8))
        g.paused=false;g.advance(30_000);assertEquals(MolePhase.FINISHED,g.phase);assertNull(g.hit(0));assertEquals(0,g.stars)
    }
    @Test fun helmetNeedsTwoDistinctStrikes() {
        val g=(0..100).map{MoleGame(5,Random(it)).apply{advance(3000)}}.first{it.moles.single().type==MoleType.HELMET}
        val hole=g.moles.single().hole
        assertEquals(MoleHit(MoleType.HELMET,false,0),g.hit(hole));assertEquals(0,g.caught)
        assertNull(g.hit(hole));g.advance(80)
        assertEquals(MoleHit(MoleType.HELMET,true,150),g.hit(hole));assertEquals(1,g.caught);assertNull(g.hit(hole))
    }
    @Test fun allStagesAreWinnableAndPerfectPlayGetsThreeStars() {
        for(level in 1..12){
            val g=MoleGame(level,Random(level))
            while(g.phase!=MolePhase.FINISHED){g.advance(80);assertTrue(g.moles.size<=3);assertEquals(g.moles.size,g.moles.map{it.hole}.toSet().size);g.moles.filter{it.caughtAt==null&&it.type!=MoleType.RABBIT}.forEach{g.hit(it.hole)}}
            assertTrue("Level $level",g.cleared);assertEquals(3,g.stars);assertEquals(g.spawned,g.caught);assertTrue(g.maxCombo>=g.target)
        }
    }
    @Test fun rabbitIsCuteButCostsPointsAndNotProgress() {
        val g=(0..500).map{MoleGame(2,Random(it)).apply{advance(3000);moles.singleOrNull()?.takeIf{m->m.type==MoleType.NORMAL}?.let{m->hit(m.hole)};advance(interval.toLong())}}.first{it.score==100&&it.moles.any{m->m.type==MoleType.RABBIT}}
        val rabbit=g.moles.first{it.type==MoleType.RABBIT}
        assertEquals(MoleHit(MoleType.RABBIT,true,-150),g.hit(rabbit.hole));assertEquals(0,g.score);assertEquals(1,g.caught);assertEquals(1,g.spawned);assertEquals(0,g.combo)
    }
    @Test fun missesResetComboAndNoNegativeScores() {
        val g=MoleGame(1,Random(2));g.advance(3000);val hole=g.moles.single().hole;g.hit(hole)
        assertEquals(MoleHit(null),g.hit((hole+1)%9));assertEquals(80,g.score);assertEquals(0,g.combo)
        repeat(8){g.hit((hole+1)%9)};assertEquals(0,g.score)
    }
    @Test fun slowFramesKeepTheSameSpawnSchedule() {
        val a=MoleGame(8,Random(4));val b=MoleGame(8,Random(4));a.advance(30_000);repeat(300){b.advance(100)}
        assertEquals(a.spawned,b.spawned);assertEquals(a.moles,b.moles)
    }
    @Test fun generatedAudioIsValidPcm() {
        val samples=MolePcm.music();assertTrue(samples.any{it.toInt()!=0});assertTrue(samples.size>22_050)
        (0..6).forEach{val fx=MolePcm.effect(it);val wav=MolePcm.wav(fx);assertEquals(44+fx.size*2,wav.size);assertEquals("RIFF",String(wav,0,4));assertTrue(fx.any{value->value.toInt()!=0})}
    }
}
