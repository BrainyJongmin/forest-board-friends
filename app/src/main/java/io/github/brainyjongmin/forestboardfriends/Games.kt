package io.github.brainyjongmin.forestboardfriends

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move as ChessMove
import kotlin.math.abs
import kotlin.random.Random

data class Pos(val row: Int, val col: Int)
data class GameMove(val from: Pos? = null, val to: Pos)
data class Cell(val label: String, val owner: Int)
enum class Difficulty { EASY, NORMAL }

interface BoardGame {
    val rows: Int
    val cols: Int
    val currentPlayer: Int
    val winner: Int? // 0 is draw
    val status: String
    fun cell(pos: Pos): Cell?
    fun legalMoves(from: Pos? = null): List<GameMove>
    fun play(move: GameMove): Boolean
    fun undo(): Boolean
    fun pass(): Boolean = false
    fun hint(difficulty: Difficulty): GameMove? = legalMoves().randomOrNull()
}

class OmokGame(override val rows: Int = 15) : BoardGame {
    override val cols = rows
    private val board = IntArray(rows * cols)
    private val history = mutableListOf<Int>()
    override var currentPlayer = 1; private set
    override var winner: Int? = null; private set
    override val status get() = winner?.let { if (it == 0) "무승부!" else "${name(it)} 승리!" } ?: "${name(currentPlayer)} 차례"
    override fun cell(pos: Pos) = board[index(pos)].takeIf { it != 0 }?.let { Cell("", it) }
    override fun legalMoves(from: Pos?) = if (winner != null) emptyList() else board.indices.filter { board[it] == 0 }.map { GameMove(to = Pos(it / cols, it % cols)) }
    override fun play(move: GameMove): Boolean {
        if (winner != null || move.from != null || move.to !in this || board[index(move.to)] != 0) return false
        val i = index(move.to); board[i] = currentPlayer; history += i
        winner = if (won(move.to, currentPlayer)) currentPlayer else if (history.size == board.size) 0 else null
        if (winner == null) currentPlayer = 3 - currentPlayer
        return true
    }
    override fun undo(): Boolean {
        val i = history.removeLastOrNull() ?: return false
        board[i] = 0; winner = null; currentPlayer = 3 - currentPlayer; return true
    }
    override fun hint(difficulty: Difficulty): GameMove? {
        val moves = legalMoves(); if (moves.isEmpty()) return null
        fun score(move: GameMove): Int {
            val p = move.to
            fun patterns(owner: Int) = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1).sumOf { (dr, dc) ->
                val forward=count(p,dr,dc,owner);val backward=count(p,-dr,-dc,owner)
                val length=1+forward+backward
                val open=listOf(Pos(p.row+(forward+1)*dr,p.col+(forward+1)*dc),Pos(p.row-(backward+1)*dr,p.col-(backward+1)*dc)).count{it in this&&board[index(it)]==0}
                when { length>=5->1_000_000;length==4&&open==2->80_000;length==4&&open==1->18_000;length==3&&open==2->7_000;length==3&&open==1->900;length==2&&open==2->350;else->length*10 }
            }
            val attack=patterns(currentPlayer);val defence=patterns(3-currentPlayer)
            return when{attack>=1_000_000->2_000_000;defence>=1_000_000->1_500_000;else->attack+defence*11/10-abs(p.row-rows/2)-abs(p.col-cols/2)}
        }
        val sorted = moves.sortedByDescending(::score)
        return if (difficulty == Difficulty.EASY) sorted.take(5).random() else sorted.first()
    }
    private fun count(p: Pos, dr: Int, dc: Int, owner: Int): Int { var n=0; var q=Pos(p.row+dr,p.col+dc); while(q in this && board[index(q)]==owner){ n++; q=Pos(q.row+dr,q.col+dc) }; return n }
    private fun won(p: Pos, owner: Int) = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1).any { (dr,dc) -> 1+count(p,dr,dc,owner)+count(p,-dr,-dc,owner)>=5 }
    private fun index(p: Pos) = p.row * cols + p.col
    private operator fun contains(p: Pos) = p.row in 0 until rows && p.col in 0 until cols
}

class GoGame(override val rows: Int = 9) : BoardGame {
    override val cols = rows
    private var board = IntArray(rows * cols)
    private data class Snapshot(val board:IntArray,val previous:IntArray?,val passes:Int,val captures:IntArray,val current:Int)
    private val history = mutableListOf<Snapshot>()
    private var previous: IntArray? = null
    private var passes = 0
    private var captures = intArrayOf(0,0,0)
    override var currentPlayer = 1; private set
    override var winner: Int? = null; private set
    override val status get() = winner?.let { if(it==0) "무승부!" else "${name(it)} 승리!" } ?: "${name(currentPlayer)} 차례 · 잡은 돌 ${captures[1]}:${captures[2]}"
    override fun cell(pos: Pos) = board[idx(pos)].takeIf { it != 0 }?.let { Cell("", it) }
    override fun legalMoves(from: Pos?): List<GameMove> = if (winner != null) emptyList() else board.indices.map { Pos(it/cols,it%cols) }.filter(::legal).map { GameMove(to=it) }
    override fun play(move: GameMove): Boolean {
        if (move.from != null || !legal(move.to)) return false
        history += Snapshot(board.copyOf(),previous?.copyOf(),passes,captures.copyOf(),currentPlayer)
        previous = board.copyOf(); captures[currentPlayer]+=place(move.to,currentPlayer); passes=0; currentPlayer=3-currentPlayer; return true
    }
    override fun pass(): Boolean {
        if(winner!=null) return false
        history += Snapshot(board.copyOf(),previous?.copyOf(),passes,captures.copyOf(),currentPlayer)
        previous=board.copyOf(); passes++; currentPlayer=3-currentPlayer
        if(passes==2) score(); return true
    }
    override fun undo(): Boolean { val old=history.removeLastOrNull()?:return false; board=old.board;previous=old.previous;passes=old.passes;captures=old.captures;currentPlayer=old.current;winner=null;return true }
    override fun hint(difficulty: Difficulty): GameMove? {
        val ranked=legalMoves().map{it to moveScore(it)}.sortedByDescending{it.second};if(ranked.isEmpty())return null
        if(difficulty==Difficulty.NORMAL&&passes>0&&history.size>rows*rows/3&&ranked.first().second<80)return null
        return if(difficulty==Difficulty.EASY)ranked.take(minOf(10,ranked.size)).random().first else ranked.first().first
    }
    private fun legal(p:Pos):Boolean {
        if(p.row !in 0 until rows || p.col !in 0 until cols || board[idx(p)]!=0 || winner!=null) return false
        val copy=board.copyOf(); place(p,currentPlayer); val ok=group(idx(p)).second>0 && (previous==null || !board.contentEquals(previous)); board=copy; return ok
    }
    private fun place(p:Pos,owner:Int):Int{board[idx(p)]=owner;var taken=0;val checked=mutableSetOf<Int>();for(start in neighbors(p))if(board[start]==3-owner&&start !in checked){val(g,l)=group(start);checked+=g;if(l==0){g.forEach{board[it]=0};taken+=g.size}};return taken}
    private fun group(start:Int):Pair<Set<Int>,Int>{ val seen=mutableSetOf<Int>();val liberties=mutableSetOf<Int>();val todo=ArrayDeque<Int>();todo+=start;val owner=board[start];while(todo.isNotEmpty()){val i=todo.removeFirst();if(!seen.add(i))continue;neighbors(Pos(i/cols,i%cols)).forEach{when(board[it]){0->liberties+=it;owner->todo+=it}}};return seen to liberties.size }
    private fun moveScore(move:GameMove):Int{
        val p=move.to;val around=neighbors(p);val copy=board.copyOf();val ownGroups=around.filter{board[it]==currentPlayer}.map(::group).distinctBy{it.first.minOrNull()};val saved=ownGroups.filter{it.second==1}.sumOf{it.first.size};val eye=around.isNotEmpty()&&around.all{board[it]==currentPlayer};val stones=board.count{it!=0}
        val gain=place(p,currentPlayer);val own=group(idx(p));val attack=neighbors(p).filter{board[it]==3-currentPlayer}.map(::group).distinctBy{it.first.minOrNull()}.filter{it.second==1}.sumOf{it.first.size};board=copy
        val stars=when(rows){9->listOf(2,4,6);13->listOf(3,6,9);19->listOf(3,9,15);else->listOf(rows/2)};val opening=if(stones<12)120-stars.minOf{r->stars.minOf{c->abs(p.row-r)+abs(p.col-c)}}*22 else 0
        return gain*1_000+saved*220+attack*110+ownGroups.size*40+own.first.size*4+own.second*18+opening-(if(own.second==1&&gain==0)450 else 0)-(if(eye&&gain==0)700 else 0)
    }
    private fun neighbors(p:Pos)=listOf(Pos(p.row-1,p.col),Pos(p.row+1,p.col),Pos(p.row,p.col-1),Pos(p.row,p.col+1)).filter{it.row in 0 until rows&&it.col in 0 until cols}.map(::idx)
    private fun score(){ val seen=mutableSetOf<Int>(); val total=intArrayOf(0,0,0); board.indices.filter{board[it]!=0}.forEach{total[board[it]]++}; for(i in board.indices) if(board[i]==0&&!seen.contains(i)){val area=mutableSetOf<Int>();val edge=mutableSetOf<Int>();val q=ArrayDeque<Int>();q+=i;while(q.isNotEmpty()){val x=q.removeFirst();if(!area.add(x))continue;neighbors(Pos(x/cols,x%cols)).forEach{if(board[it]==0)q+=it else edge+=board[it]}};seen+=area;if(edge.size==1)total[edge.first()]+=area.size}; winner=when{total[1]>total[2]+6.5->1;total[1]<total[2]+6.5->2;else->0} }
    private fun idx(p:Pos)=p.row*cols+p.col
}

class ChessGame : BoardGame {
    private val board = Board()
    override val rows=8; override val cols=8
    override val currentPlayer get() = if(board.sideToMove==Side.WHITE) 1 else 2
    override val winner:Int? get() = when { board.isMated -> 3-currentPlayer; board.isDraw -> 0; else -> null }
    override val status get() = winner?.let{if(it==0)"무승부!" else "${name(it)} 승리!"} ?: "${if(currentPlayer==1)"흰색" else "검은색"} 차례${if(board.isKingAttacked)" · 체크!" else ""}"
    override fun cell(pos:Pos):Cell? { val p=board.getPiece(square(pos)); if(p==Piece.NONE)return null; return Cell(chessLabel(p),if(p.pieceSide==Side.WHITE)1 else 2) }
    override fun legalMoves(from:Pos?)=board.legalMoves().filter{from==null||pos(it.from)==from}.map{GameMove(pos(it.from),pos(it.to))}
    override fun play(move:GameMove):Boolean { val found=board.legalMoves().filter{pos(it.from)==move.from&&pos(it.to)==move.to}.maxByOrNull{pieceValue(it.promotion)}?:return false; return board.doMove(found) }
    override fun undo()=board.undoMove()!=null
    override fun hint(difficulty:Difficulty):GameMove? {
        val analysis=board.clone();val moves=analysis.legalMoves();if(moves.isEmpty())return null
        val side=analysis.sideToMove
        fun search(depth:Int,low:Int,high:Int):Int{
            if(depth==0||analysis.isMated||analysis.isDraw)return evaluate(analysis,side)
            var alpha=low;var beta=high;val maximizing=analysis.sideToMove==side;var best=if(maximizing)-1_000_000 else 1_000_000
            val ordered=analysis.legalMoves().sortedByDescending{pieceValue(analysis.getPiece(it.to))*10+pieceValue(it.promotion)}
            for(move in ordered){analysis.doMove(move);val value=search(depth-1,alpha,beta);analysis.undoMove();if(maximizing){best=maxOf(best,value);alpha=maxOf(alpha,best)}else{best=minOf(best,value);beta=minOf(beta,best)};if(alpha>=beta)break}
            return best
        }
        val depth=if(difficulty==Difficulty.NORMAL)2 else 0
        val ranked=moves.sortedByDescending{move->analysis.doMove(move);val value=search(depth,-1_000_000,1_000_000);analysis.undoMove();value}
        val move=if(difficulty==Difficulty.EASY)ranked.take(minOf(5,ranked.size)).random()else ranked.first()
        return GameMove(pos(move.from),pos(move.to))
    }
    private fun evaluate(position:Board,side:Side):Int{
        if(position.isMated)return if(position.sideToMove==side)-100_000 else 100_000
        if(position.isDraw)return 0
        return position.boardToArray().mapIndexed{index,piece->
            if(piece==Piece.NONE)0 else {
                val row=index/8;val col=index%8
                val center=(6-abs(row*2-7)-abs(col*2-7)).coerceAtLeast(0)
                val advance=if(piece.pieceType==PieceType.PAWN)if(piece.pieceSide==Side.WHITE)row else 7-row else 0
                (pieceValue(piece)*100+center*3+advance)*(if(piece.pieceSide==side)1 else -1)
            }
        }.sum()
    }
    private fun pieceValue(piece:Piece)=if(piece==Piece.NONE)0 else when(piece.pieceType){PieceType.PAWN->1;PieceType.KNIGHT,PieceType.BISHOP->3;PieceType.ROOK->5;PieceType.QUEEN->9;PieceType.KING->100;else->0}
    private fun square(p:Pos)=Square.squareAt((7-p.row)*8+p.col)
    private fun pos(s:Square)=Pos(7-s.ordinal/8,s.ordinal%8)
    private fun chessLabel(p:Piece)=mapOf("WHITE_KING" to "♔","WHITE_QUEEN" to "♕","WHITE_ROOK" to "♖","WHITE_BISHOP" to "♗","WHITE_KNIGHT" to "♘","WHITE_PAWN" to "♙","BLACK_KING" to "♚","BLACK_QUEEN" to "♛","BLACK_ROOK" to "♜","BLACK_BISHOP" to "♝","BLACK_KNIGHT" to "♞","BLACK_PAWN" to "♟")[p.name]?:"?"
}

class JanggiGame : BoardGame {
    override val rows=10; override val cols=9
    private var board=IntArray(rows*cols)
    private val history=mutableListOf<IntArray>();private val positionKeys=mutableListOf<String>()
    override var currentPlayer=1; private set
    override var winner:Int?=null; private set
    override val status get()=winner?.let{if(it==0)"무승부!" else "${name(it)} 승리!"}?:"${if(currentPlayer==1)"초" else "한"} 차례"
    init { setup();positionKeys+=positionKey() }
    override fun cell(pos:Pos)=board[idx(pos)].takeIf{it!=0}?.let{Cell((if(it>0)choLabels else hanLabels)[abs(it)]?:"?",if(it>0)1 else 2)}
    override fun legalMoves(from:Pos?):List<GameMove>{ if(winner!=null)return emptyList(); val all=mutableListOf<GameMove>(); for(i in board.indices)if(owner(board[i])==currentPlayer){val p=Pos(i/cols,i%cols); if(from==null||from==p) raw(p).filter{safe(GameMove(p,it))}.forEach{all+=GameMove(p,it)}};return all }
    override fun play(move:GameMove):Boolean { if(move !in legalMoves(move.from))return false;history+=board.copyOf();val f=idx(move.from!!);val captured=board[idx(move.to)];board[idx(move.to)]=board[f];board[f]=0;if(abs(captured)==1)winner=currentPlayer;currentPlayer=3-currentPlayer;positionKeys+=positionKey();if(positionKeys.count{it==positionKeys.last()}>=3)winner=0;if(winner==null&&inCheck(currentPlayer)&&legalMoves().isEmpty())winner=3-currentPlayer;return true }
    override fun undo():Boolean{val old=history.removeLastOrNull()?:return false;board=old;positionKeys.removeAt(positionKeys.lastIndex);winner=null;currentPlayer=3-currentPlayer;return true}
    override fun pass():Boolean{if(winner!=null||inCheck(currentPlayer))return false;history+=board.copyOf();currentPlayer=3-currentPlayer;positionKeys+=positionKey();if(positionKeys.count{it==positionKeys.last()}>=3)winner=0;return true}
    override fun hint(difficulty:Difficulty):GameMove?{val moves=legalMoves();if(moves.isEmpty())return null;val sorted=moves.sortedByDescending(::moveScore);return if(difficulty==Difficulty.EASY)sorted.take(minOf(6,sorted.size)).random()else sorted.first()}
    private fun setup(){val back=intArrayOf(3,6,5,2,0,2,5,6,3);for(c in 0..8){board[c]=-back[c];board[9*cols+c]=back[c]};board[idx(Pos(1,4))]=-1;board[idx(Pos(8,4))]=1;board[idx(Pos(2,1))]=-4;board[idx(Pos(2,7))]=-4;board[idx(Pos(7,1))]=4;board[idx(Pos(7,7))]=4;for(c in listOf(0,2,4,6,8)){board[idx(Pos(3,c))]=-7;board[idx(Pos(6,c))]=7}}
    private fun raw(p:Pos):List<Pos>{val piece=abs(board[idx(p)]);val owner=owner(board[idx(p)]);val out=mutableListOf<Pos>();fun add(q:Pos){if(q.inBoard()&&owner(board[idx(q)])!=owner)out+=q};when(piece){1,2->for(dr in -1..1)for(dc in -1..1)if(abs(dr)+abs(dc)>0){val q=Pos(p.row+dr,p.col+dc);if(inPalace(q,owner)&&(abs(dr)+abs(dc)==1||palaceLine(p,q)))add(q)};3->slides(p,listOf(-1 to 0,1 to 0,0 to -1,0 to 1),out,owner).also{palaceRook(p,out,owner)};4->cannon(p,out,owner);5->{val steps=listOf(Triple(-1,0,listOf(-2 to -1,-2 to 1)),Triple(1,0,listOf(2 to -1,2 to 1)),Triple(0,-1,listOf(-1 to -2,1 to -2)),Triple(0,1,listOf(-1 to 2,1 to 2)));for((br,bc,ends)in steps)if(empty(Pos(p.row+br,p.col+bc)))ends.forEach{(dr,dc)->add(Pos(p.row+dr,p.col+dc))}};6->{val paths=listOf(listOf(-1 to 0,-2 to -1,-3 to -2),listOf(-1 to 0,-2 to 1,-3 to 2),listOf(1 to 0,2 to -1,3 to -2),listOf(1 to 0,2 to 1,3 to 2),listOf(0 to -1,-1 to -2,-2 to -3),listOf(0 to -1,1 to -2,2 to -3),listOf(0 to 1,-1 to 2,-2 to 3),listOf(0 to 1,1 to 2,2 to 3));paths.forEach{path->if(empty(Pos(p.row+path[0].first,p.col+path[0].second))&&empty(Pos(p.row+path[1].first,p.col+path[1].second)))add(Pos(p.row+path[2].first,p.col+path[2].second))}};7->{val f=if(owner==1)-1 else 1;add(Pos(p.row+f,p.col));add(Pos(p.row,p.col-1));add(Pos(p.row,p.col+1));for(dc in listOf(-1,1)){val q=Pos(p.row+f,p.col+dc);if(inPalace(q,3-owner)&&palaceLine(p,q))add(q)}}};return out}
    private fun slides(p:Pos,dirs:List<Pair<Int,Int>>,out:MutableList<Pos>,me:Int){for((dr,dc)in dirs){var q=Pos(p.row+dr,p.col+dc);while(q.inBoard()){val o=owner(board[idx(q)]);if(o==me)break;out+=q;if(o!=0)break;q=Pos(q.row+dr,q.col+dc)}}}
    private fun palaceRook(p:Pos,out:MutableList<Pos>,me:Int){palaceLines.filter{p in it}.forEach{line->val start=line.indexOf(p);for(step in listOf(-1,1)){var i=start+step;while(i in line.indices){val q=line[i];val o=owner(board[idx(q)]);if(o==me)break;out+=q;if(o!=0)break;i+=step}}}}
    private fun cannon(p:Pos,out:MutableList<Pos>,me:Int){for((dr,dc)in listOf(-1 to 0,1 to 0,0 to -1,0 to 1)){var q=Pos(p.row+dr,p.col+dc);var screen=false;while(q.inBoard()){val v=board[idx(q)];if(!screen){if(v!=0){if(abs(v)==4)break;screen=true}}else if(v==0)out+=q else{if(owner(v)!=me&&abs(v)!=4)out+=q;break};q=Pos(q.row+dr,q.col+dc)}};palaceLines.filter{p in it}.forEach{line->val start=line.indexOf(p);for(step in listOf(-1,1)){var i=start+step;var screen=false;while(i in line.indices){val q=line[i];val v=board[idx(q)];if(!screen){if(v!=0){if(abs(v)==4)break;screen=true}}else if(v==0)out+=q else{if(owner(v)!=me&&abs(v)!=4)out+=q;break};i+=step}}}}
    private fun moveScore(move:GameMove):Int{val copy=board.copyOf();val player=currentPlayer;val from=idx(move.from!!);val piece=abs(board[from]);val captured=pieceValues[abs(board[idx(move.to)])];board[idx(move.to)]=board[from];board[from]=0;if(captured>=pieceValues[1]){board=copy;return 1_000_000};currentPlayer=3-player;val checked=inCheck(currentPlayer);val replies=legalMoves();val danger=replies.maxOfOrNull{pieceValues[abs(board[idx(it.to)])]}?:0;val activity=raw(move.to).size+(if(piece==7)if(player==1)9-move.to.row else move.to.row else 0);currentPlayer=player;board=copy;return if(checked&&replies.isEmpty())500_000 else captured*10-danger*8+(if(checked)80 else 0)+activity}
    private fun safe(m:GameMove):Boolean{val copy=board.copyOf();val f=idx(m.from!!);board[idx(m.to)]=board[f];board[f]=0;val ok=!inCheck(owner(board[idx(m.to)]));board=copy;return ok}
    private fun inCheck(player:Int):Boolean{val king=board.indices.firstOrNull{abs(board[it])==1&&owner(board[it])==player}?:return true;val kp=Pos(king/cols,king%cols);return board.indices.any{owner(board[it])==3-player&&raw(Pos(it/cols,it%cols)).contains(kp)}}
    private fun inPalace(p:Pos,owner:Int)=p.col in 3..5&&p.row in if(owner==2)0..2 else 7..9
    private fun palaceLine(a:Pos,b:Pos):Boolean{if(abs(a.row-b.row)!=abs(a.col-b.col)||abs(a.row-b.row)!=1)return false;return listOf(Pos(0,3),Pos(0,5),Pos(1,4),Pos(2,3),Pos(2,5),Pos(7,3),Pos(7,5),Pos(8,4),Pos(9,3),Pos(9,5)).contains(a)&&listOf(Pos(0,3),Pos(0,5),Pos(1,4),Pos(2,3),Pos(2,5),Pos(7,3),Pos(7,5),Pos(8,4),Pos(9,3),Pos(9,5)).contains(b)}
    private fun empty(p:Pos)=p.inBoard()&&board[idx(p)]==0
    private fun Pos.inBoard()=row in 0 until rows&&col in 0 until cols
    private fun idx(p:Pos)=p.row*cols+p.col
    private fun positionKey()=board.joinToString(",")+":$currentPlayer"
    private fun owner(v:Int)=when{v>0->1;v<0->2;else->0}
    companion object{
        val choLabels=mapOf(1 to "楚",2 to "士",3 to "車",4 to "包",5 to "馬",6 to "象",7 to "卒")
        val hanLabels=mapOf(1 to "漢",2 to "士",3 to "車",4 to "包",5 to "馬",6 to "象",7 to "兵")
        val pieceValues=intArrayOf(0,1_000,15,90,45,40,35,20)
        val palaceLines=listOf(listOf(Pos(0,3),Pos(1,4),Pos(2,5)),listOf(Pos(0,5),Pos(1,4),Pos(2,3)),listOf(Pos(7,3),Pos(8,4),Pos(9,5)),listOf(Pos(7,5),Pos(8,4),Pos(9,3)))
    }
}

fun name(player:Int)=if(player==1)"첫째" else "둘째"
