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

    @Test fun goRejectsSuicideAndImmediateKoRecapture(){
        val suicide=GoGame(9)
        listOf(Pos(0,1),Pos(8,8),Pos(1,0)).forEach{assertTrue(suicide.play(GameMove(to=it)))}
        assertFalse(suicide.play(GameMove(to=Pos(0,0))))

        val ko=GoGame(9)
        listOf(Pos(0,1),Pos(1,1),Pos(1,0),Pos(0,2),Pos(2,1),Pos(2,2),Pos(8,8),Pos(1,3),Pos(1,2)).forEach{assertTrue(ko.play(GameMove(to=it)))}
        assertNull(ko.cell(Pos(1,1)));assertFalse(ko.play(GameMove(to=Pos(1,1))))
    }

    @Test fun goEndsAndScoresAfterTwoPasses(){val g=GoGame(9);assertTrue(g.pass());assertTrue(g.pass());assertEquals(2,g.winner)}

    @Test fun chessAndJanggiOnlyOfferLegalBoardMoves(){
        val chess=ChessGame();assertEquals(20,chess.legalMoves().size)
        assertTrue(chess.legalMoves().all{it.from!!.row in 0..7&&it.to.row in 0..7})
        val janggi=JanggiGame();assertTrue(janggi.legalMoves().isNotEmpty())
        assertTrue(janggi.legalMoves().all{it.to.row in 0..9&&it.to.col in 0..8})
    }

    @Test fun hintsAreLegalAndDoNotCrashOnEmptyChessSquares(){
        listOf<BoardGame>(ChessGame(),JanggiGame(),GoGame(),OmokGame()).forEach{game->
            val before=game.currentPlayer
            val hint=game.hint(Difficulty.NORMAL)
            assertNotNull(hint);assertTrue(hint in game.legalMoves());assertEquals(before,game.currentPlayer)
        }
    }

    @Test fun normalOmokBlocksAnImmediateLoss(){
        val g=OmokGame()
        listOf(Pos(7,7),Pos(0,0),Pos(7,8),Pos(0,1),Pos(7,9),Pos(0,2),Pos(1,0),Pos(0,3)).forEach{assertTrue(g.play(GameMove(to=it)))}
        assertEquals(Pos(0,4),g.hint(Difficulty.NORMAL)?.to)
    }

    @Test fun normalOmokTakesAWinBeforeBlocking(){
        val g=OmokGame()
        listOf(Pos(7,7),Pos(0,0),Pos(7,8),Pos(0,1),Pos(7,9),Pos(0,2),Pos(7,10),Pos(0,3)).forEach{assertTrue(g.play(GameMove(to=it)))}
        assertTrue(g.hint(Difficulty.NORMAL)?.to in setOf(Pos(7,6),Pos(7,11)))
    }

    @Test fun selectingChessAndJanggiPiecesFiltersDestinations(){
        val chess=ChessGame();assertEquals(2,chess.legalMoves(Pos(6,4)).size)
        val janggi=JanggiGame();val source=janggi.legalMoves().first().from!!
        assertTrue(janggi.legalMoves(source).all{it.from==source})
    }

    @Test fun chessQueenMovesDiagonallyAfterHerPawnClears(){
        val chess=ChessGame()
        assertTrue(chess.play(GameMove(Pos(6,4),Pos(4,4))));assertTrue(chess.play(GameMove(Pos(1,0),Pos(2,0))))
        assertTrue(GameMove(Pos(7,3),Pos(3,7)) in chess.legalMoves(Pos(7,3)))
    }

    @Test fun blockPuzzleMovesAndResets(){
        val g=BlockPuzzleGame(Random(1));val start=g.cells();assertTrue(g.move(-1,0));assertNotEquals(start,g.cells());g.hardDrop();assertTrue(g.board.any{it!=0});assertEquals(1,g.pieces);g.reset();assertTrue(g.board.all{it==0});assertEquals(0,g.score);assertEquals(0,g.pieces)
    }

    @Test fun blockPuzzleItemModeAwardsAnItemAndReportsGoals(){
        val g=BlockPuzzleGame(Random(2),true);assertEquals(10,g.linesToNextLevel);assertEquals(4,g.linesToNextItem)
        g.board.fill(1,(g.height-4)*g.width,g.height*g.width);g.hardDrop()
        assertEquals(4,g.lines);assertNotNull(g.itemMessage);assertEquals(1,g.itemEvent);assertTrue(g.clearEvent>0);assertTrue(g.lastClearedRows.isNotEmpty());assertEquals(6,g.linesToNextLevel);assertEquals(4,g.linesToNextItem)
    }

    @Test fun blockPuzzleAddsGroundedChallengeAtNewLevel(){
        val g=BlockPuzzleGame(Random(3));listOf(4,4,2).forEach{rows->g.board.fill(1,(g.height-rows)*g.width,g.height*g.width);g.hardDrop()}
        assertEquals(2,g.level);assertEquals(1,g.levelEvent);assertEquals(1,g.levelBlocksAdded)
        val gaps=(0 until g.width).filter{g.board[(g.height-1)*g.width+it]==0};assertEquals(2,gaps.size);assertEquals(gaps[0]+1,gaps[1])
    }

    @Test fun lineBoardsSnapTouchesToIntersections(){
        assertEquals(0,boardIndex(10f,100f,9,10f));assertEquals(4,boardIndex(50f,100f,9,10f));assertEquals(8,boardIndex(90f,100f,9,10f))
        assertEquals(0,boardIndex(1f,80f,8,0f));assertEquals(7,boardIndex(79f,80f,8,0f))
    }
}
