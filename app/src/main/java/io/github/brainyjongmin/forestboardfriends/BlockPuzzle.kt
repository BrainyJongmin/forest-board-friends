package io.github.brainyjongmin.forestboardfriends

import kotlin.random.Random

class BlockPuzzleGame(private val random: Random = Random.Default, val itemsEnabled: Boolean = false) {
    val width = 10
    val height = 20
    val board = IntArray(width * height)
    var score = 0; private set
    var lines = 0; private set
    val level get() = lines / 10 + 1
    val linesToNextLevel get() = 10 - lines % 10
    val linesToNextItem get() = 4 - lines % 4
    val dropDelay get() = (750-(level-1)*55).coerceAtLeast(100)+(if(slowPieces>0)250 else 0)
    var itemMessage: String? = null; private set
    var itemEvent = 0; private set
    var pieces = 0; private set
    var clearEvent = 0; private set
    var lastClearedRows: List<Int> = emptyList(); private set
    var levelEvent = 0; private set
    var levelBlocksAdded = 0; private set
    var gameOver = false; private set
    var paused = false
    private val bag = mutableListOf<Int>()
    private var slowPieces = 0
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
        board.fill(0); score=0; lines=0; gameOver=false; paused=false; bag.clear();slowPieces=0;itemMessage=null;itemEvent=0;pieces=0;clearEvent=0;lastClearedRows=emptyList();levelEvent=0;levelBlocksAdded=0
        next=draw(); piece=draw(); rotation=0; row=0; col=3
    }

    private fun lock() {
        if(slowPieces>0)slowPieces--
        cells().filter { it.row >= 0 }.forEach { board[it.row * width + it.col] = piece + 1 }
        pieces++;val oldLines=lines;val oldLevel=level;val fullRows=(0 until height).filter{r->(0 until width).all{board[r*width+it]!=0}}
        var cleared=0;var r=height-1
        while(r>=0)if((0 until width).all{board[r*width+it]!=0}){for(y in r downTo 1)for(x in 0 until width)board[y*width+x]=board[(y-1)*width+x];for(x in 0 until width)board[x]=0;cleared++}else r--
        if(cleared>0){score+=intArrayOf(0,100,300,500,800)[cleared]*level;lines+=cleared;lastClearedRows=fullRows;clearEvent++}
        if(itemsEnabled&&oldLines/4<lines/4)useRandomItem()
        if(level>oldLevel)addLevelBlocks()
        piece=next;next=draw();rotation=0;row=0;col=3;gameOver=gameOver||!fits(rotation,row,col)
    }

    private fun useRandomItem(){when(random.nextInt(3)){
        0->{val target=(height-1 downTo 0).firstOrNull{r->(0 until width).any{board[r*width+it]!=0}};if(target!=null){for(y in target downTo 1)for(x in 0 until width)board[y*width+x]=board[(y-1)*width+x];for(x in 0 until width)board[x]=0;lastClearedRows=listOf(target);clearEvent++};itemMessage="🧹 다람쥐 빗자루! 아랫줄을 정리했어요"}
        1->{slowPieces=5;itemMessage="🐢 거북이 시간! 5조각 동안 천천히 내려와요"}
        else->{score+=500*level;itemMessage="⭐ 별 보너스! ${500*level}점을 받았어요"}
    };itemEvent++}

    private fun addLevelBlocks(){val rows=(1+(level-2)/3).coerceAtMost(3);if((0 until rows*width).any{board[it]!=0}){gameOver=true;return};for(y in 0 until height-rows)for(x in 0 until width)board[y*width+x]=board[(y+rows)*width+x];val gap=(level*3+pieces)%(width-1);for(y in height-rows until height)for(x in 0 until width)board[y*width+x]=if(x==gap||x==gap+1)0 else 1+(x+y+level)%7;levelBlocksAdded=rows;levelEvent++}

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
