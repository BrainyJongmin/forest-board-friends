package io.github.brainyjongmin.forestboardfriends

import kotlin.random.Random

class BlockPuzzleGame(private val random: Random = Random.Default) {
    val width = 10
    val height = 20
    val board = IntArray(width * height)
    var score = 0; private set
    var lines = 0; private set
    val level get() = lines / 10 + 1
    var gameOver = false; private set
    var paused = false
    private val bag = mutableListOf<Int>()
    var next = draw(); private set
    var piece = draw(); private set
    var rotation = 0; private set
    var row = 0; private set
    var col = 3; private set

    fun cells(type: Int = piece, rot: Int = rotation, r: Int = row, c: Int = col): List<Pos> =
        SHAPES[type][rot % SHAPES[type].size].map { Pos(r + it.row, c + it.col) }

    fun move(dx: Int, dy: Int): Boolean {
        if (paused || gameOver || !fits(rotation, row + dy, col + dx)) return false
        row += dy; col += dx; return true
    }

    fun rotate(): Boolean {
        if (paused || gameOver) return false
        val nr = (rotation + 1) % SHAPES[piece].size
        for (kick in listOf(0, -1, 1, -2, 2)) if (fits(nr, row, col + kick)) { rotation = nr; col += kick; return true }
        return false
    }

    fun tick(): Boolean { if (move(0, 1)) return true; lock(); return false }
    fun softDrop() { if (move(0, 1)) score++ else lock() }
    fun hardDrop() { var n=0; while(move(0,1))n++;score+=n*2;lock() }

    fun reset() {
        board.fill(0); score=0; lines=0; gameOver=false; paused=false; bag.clear()
        next=draw(); piece=draw(); rotation=0; row=0; col=3
    }

    private fun lock() {
        cells().filter { it.row >= 0 }.forEach { board[it.row * width + it.col] = piece + 1 }
        var cleared=0;var r=height-1
        while(r>=0)if((0 until width).all{board[r*width+it]!=0}){for(y in r downTo 1)for(x in 0 until width)board[y*width+x]=board[(y-1)*width+x];for(x in 0 until width)board[x]=0;cleared++}else r--
        if(cleared>0){score+=intArrayOf(0,100,300,500,800)[cleared]*level;lines+=cleared}
        piece=next;next=draw();rotation=0;row=0;col=3;gameOver=!fits(rotation,row,col)
    }

    private fun fits(rot:Int,r:Int,c:Int)=cells(piece,rot,r,c).all{it.col in 0 until width&&it.row<height&&(it.row<0||board[it.row*width+it.col]==0)}
    private fun draw():Int{if(bag.isEmpty())bag+=(0..6).shuffled(random);return bag.removeAt(bag.lastIndex)}

    companion object {
        private fun s(vararg p:Pair<Int,Int>)=p.map{Pos(it.first,it.second)}
        val SHAPES=listOf(
            listOf(s(1 to 0,1 to 1,1 to 2,1 to 3),s(0 to 2,1 to 2,2 to 2,3 to 2)),
            listOf(s(0 to 0,0 to 1,1 to 0,1 to 1)),
            listOf(s(0 to 1,1 to 0,1 to 1,1 to 2),s(0 to 1,1 to 1,1 to 2,2 to 1),s(1 to 0,1 to 1,1 to 2,2 to 1),s(0 to 1,1 to 0,1 to 1,2 to 1)),
            listOf(s(0 to 1,0 to 2,1 to 0,1 to 1),s(0 to 1,1 to 1,1 to 2,2 to 2)),
            listOf(s(0 to 0,0 to 1,1 to 1,1 to 2),s(0 to 2,1 to 1,1 to 2,2 to 1)),
            listOf(s(0 to 0,1 to 0,1 to 1,1 to 2),s(0 to 1,0 to 2,1 to 1,2 to 1),s(1 to 0,1 to 1,1 to 2,2 to 2),s(0 to 1,1 to 1,2 to 0,2 to 1)),
            listOf(s(0 to 2,1 to 0,1 to 1,1 to 2),s(0 to 1,1 to 1,2 to 1,2 to 2),s(1 to 0,1 to 1,1 to 2,2 to 0),s(0 to 0,0 to 1,1 to 1,2 to 1))
        )
    }
}
