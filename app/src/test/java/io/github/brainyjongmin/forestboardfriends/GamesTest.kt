package io.github.brainyjongmin.forestboardfriends

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GamesTest {
    @Test fun omokDetectsFiveAndUndo(){
        val g=OmokGame()
        for(c in 0..3){assertTrue(g.play(GameMove(to=Pos(7,c))));assertTrue(g.play(GameMove(to=Pos(8,c))))}
        assertTrue(g.play(GameMove(to=Pos(7,4))))
        assertEquals(1,g.winner);assertTrue(g.undo());assertNull(g.winner)
    }

    @Test fun goCapturesAndRejectsOccupied(){
        val g=GoGame(9)
        assertTrue(g.play(GameMove(to=Pos(0,1))))
        assertTrue(g.play(GameMove(to=Pos(0,0))))
        assertTrue(g.play(GameMove(to=Pos(1,0))))
        assertNull(g.cell(Pos(0,0)))
        assertFalse(g.play(GameMove(to=Pos(0,1))))
    }

    @Test fun chessAndJanggiOnlyOfferLegalBoardMoves(){
        val chess=ChessGame();assertEquals(20,chess.legalMoves().size)
        assertTrue(chess.legalMoves().all{it.from!!.row in 0..7&&it.to.row in 0..7})
        val janggi=JanggiGame();assertTrue(janggi.legalMoves().isNotEmpty())
        assertTrue(janggi.legalMoves().all{it.to.row in 0..9&&it.to.col in 0..8})
    }

    @Test fun blockPuzzleMovesAndResets(){
        val g=BlockPuzzleGame(Random(1));val start=g.cells();assertTrue(g.move(-1,0));assertNotEquals(start,g.cells());g.hardDrop();assertTrue(g.board.any{it!=0});g.reset();assertTrue(g.board.all{it==0});assertEquals(0,g.score)
    }

    @Test fun lineBoardsSnapTouchesToIntersections(){
        assertEquals(0,boardIndex(10f,100f,9,10f));assertEquals(4,boardIndex(50f,100f,9,10f));assertEquals(8,boardIndex(90f,100f,9,10f))
        assertEquals(0,boardIndex(1f,80f,8,0f));assertEquals(7,boardIndex(79f,80f,8,0f))
    }
}
