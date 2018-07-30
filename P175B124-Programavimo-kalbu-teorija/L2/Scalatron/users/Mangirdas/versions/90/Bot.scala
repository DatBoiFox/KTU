// Example Bot #1: The Reference Bot


/** This bot builds a 'direction value map' that assigns an attractiveness score to
  * each of the eight available 45-degree directions. Additional behaviors:
  * - aggressive missiles: approach an enemy master, then explode
  * - defensive missiles: approach an enemy slave and annihilate it
  *
  * The master bot uses the following state parameters:
  *  - dontFireAggressiveMissileUntil
  *  - dontFireDefensiveMissileUntil
  *  - lastDirection
  * The mini-bots use the following state parameters:
  *  - mood = Aggressive | Defensive | Lurking
  *  - target = remaining offset to target location
  */
import util.Random;
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Queue
import scala.collection.mutable.Stack
import scala.util.control._

object ControlFunction
{
    val rand = new Random();
    def forMaster(bot: Bot) {
        val (directionValue, nearestEnemyMaster, nearestEnemySlave, nearestPlant) = analyzeViewAsMaster(bot.view)
        val dontFireAggressiveMissileUntil = bot.inputAsIntOrElse("dontFireAggressiveMissileUntil", -1)
        val dontFireDefensiveMissileUntil = bot.inputAsIntOrElse("dontFireDefensiveMissileUntil", -1)
        val dontSpawnGatheringBotUntil = bot.inputAsIntOrElse("dontSpawnGatheringBotUntil", -1)
        val dontThrowMinesUntil = bot.inputAsIntOrElse("dontThrowMinesUntil", -1)
        val dontFirePathFinderUntil = bot.inputAsIntOrElse("dontFirePathFinderUntil", -1)
        val lastDirection = bot.inputAsIntOrElse("lastDirection", 0)
        // determine movement direction
        directionValue(lastDirection) += 10 // try to break ties by favoring the last direction
        val bestDirection45 = directionValue.zipWithIndex.maxBy(_._1)._2
        val direction = XY.fromDirection45(bestDirection45)
        
        /*if (nearestPlant != -1){
            var bfs = BreadthFirstSearch(bot, bot.viewString)
            bfs.stringViewToMatrix()
            bfs.findAllAdjacentNodes()
            bfs.findPath(480, nearestPlant)
            var bfsPath = bfs.path.toString
            bot.log("bfsPath" + bfsPath)
            var s = bfsPath.split(";")
            var moves = new ArrayBuffer[XY]()
            for (i <- 0 to s.length-1) moves += XY(s(i))
            //bot.move(moves(bot.time))
        }
        else{
            //bot.move(direction)
        }*/
        if (nearestPlant != -1){
            var bfs = BFS(bot, bot.viewString)
            bfs.fillMatrix();
            bfs.findAllNeighbors();
            bfs.bfsPath(480,nearestPlant)
            var moves = bfs.path.toString ;
            bot.log("moves " + moves)
            bot.spawn(bot.view.center,"mood" -> "PathFinder", "path"->moves, "timeT"->bot.time.toString(), "target" -> "", "energy" -> 100)
            bot.set("dontFirePathFinderUntil" -> (bot.time + 100))
        }
        bot.move(direction)
        bot.set("lastDirection" -> bestDirection45)

        if(dontFireAggressiveMissileUntil < bot.time && bot.energy > 100) { // fire attack missile?
            nearestEnemyMaster match {
                case None =>            // no-on nearby
                case Some(relPos) =>    // a master is nearby
                    val unitDelta = relPos.signum
                    val remainder = relPos - unitDelta // we place slave nearer target, so subtract that from overall delta
                    bot.spawn(unitDelta, "mood" -> "Aggressive", "target" -> remainder)
                    bot.set("dontFireAggressiveMissileUntil" -> (bot.time + relPos.stepCount + 1))
            }
        }
        else
        if(dontFireDefensiveMissileUntil < bot.time && bot.energy > 100) { // fire defensive missile?
            nearestEnemySlave match {
                case None =>            // no-on nearby
                case Some(relPos) =>    // an enemy slave is nearby
                    if(relPos.stepCount < 8) {
                        // this one's getting too close!
                        val unitDelta = relPos.signum
                        val remainder = relPos - unitDelta // we place slave nearer target, so subtract that from overall delta
                        bot.spawn(unitDelta, "mood" -> "Defensive", "target" -> remainder)
                        bot.set("dontFireDefensiveMissileUntil" -> (bot.time + relPos.stepCount + 1))
                    }
            }
        }
        if(dontSpawnGatheringBotUntil < bot.time && bot.energy > 100){
            bot.spawn(bot.view.center, "mood" -> "Gathering", "target" -> "", "limit" -> 500 * (rand.nextInt(4) + 1))
            bot.set("dontSpawnGatheringBotUntil" -> (bot.time + rand.nextInt(2)))
        }
        /*if(dontThrowMinesUntil < bot.time && bot.energy > 100){
            nearestEnemyMaster match{
                case None => // do nothing (don't throw) 
                case Some(relPos) =>
                    bot.spawn()
            }
        }*/
    }


    def forSlave(bot: MiniBot) {
        bot.inputOrElse("mood", "Gathering") match {
            case "Aggressive" => reactAsAggressiveMissile(bot)
            case "Defensive" => reactAsDefensiveMissile(bot)
            case "Gathering" => reactAsGatheringBot(bot)
            case "Returning" => reactAsReturningBot(bot)
            case "PathFinder" => reactPathFinder(bot,bot.inputOrElse("path","NO"),bot.inputOrElse("timeT","NO"))
            case s: String => bot.log("unknown mood: " + s)
        }
    }

    def reactPathFinder(bot: MiniBot, path: String, time : String){
          var s = path.split(";")
          var i = 0;
          var moves = new ArrayBuffer[XY]()
          bot.log(s.length.toString())
          for(i <- 0 to s.length -1){
              moves += XY(s(i))
          }
          var a = moves.toArray
          var b = XY(0,0)
          bot.move(moves(bot.time - time.toInt))
          bot.log(bot.time.toString()+ " " + time)
    }
    def reactAsAggressiveMissile(bot: MiniBot) {
        bot.view.offsetToNearest('m') match {
            case Some(delta: XY) =>
                // another master is visible at the given relative position (i.e. position delta)

                // close enough to blow it up?
                if(delta.length <= 2) {
                    // yes -- blow it up!
                    bot.explode(4)

                } else {
                    // no -- move closer!
                    bot.move(delta.signum)
                    bot.set("rx" -> delta.x, "ry" -> delta.y)
                }
            case None =>
                // no target visible -- follow our targeting strategy
                val target = bot.inputAsXYOrElse("target", XY.Zero)

                // did we arrive at the target?
                if(target.isNonZero) {
                    // no -- keep going
                    val unitDelta = target.signum // e.g. CellPos(-8,6) => CellPos(-1,1)
                    bot.move(unitDelta)

                    // compute the remaining delta and encode it into a new 'target' property
                    val remainder = target - unitDelta // e.g. = CellPos(-7,5)
                    bot.set("target" -> remainder)
                } else {
                    // yes -- but we did not detonate yet, and are not pursuing anything?!? => switch purpose
                    bot.set("mood" -> "Lurking", "target" -> "")
                    bot.say("Lurking")
                }
        }
    }


    def reactAsDefensiveMissile(bot: MiniBot) {
        bot.view.offsetToNearest('s') match {
            case Some(delta: XY) =>
                // another slave is visible at the given relative position (i.e. position delta)
                // move closer!
                bot.move(delta.signum)
                bot.set("rx" -> delta.x, "ry" -> delta.y)

            case None =>
                // no target visible -- follow our targeting strategy
                val target = bot.inputAsXYOrElse("target", XY.Zero)

                // did we arrive at the target?
                if(target.isNonZero) {
                    // no -- keep going
                    val unitDelta = target.signum // e.g. CellPos(-8,6) => CellPos(-1,1)
                    bot.move(unitDelta)

                    // compute the remaining delta and encode it into a new 'target' property
                    val remainder = target - unitDelta // e.g. = CellPos(-7,5)
                    bot.set("target" -> remainder)
                } else {
                    // yes -- but we did not annihilate yet, and are not pursuing anything?!? => switch purpose
                    bot.set("mood" -> "Lurking", "target" -> "")
                    bot.say("Lurking")
                }
        }
    }
    
    def reactAsGatheringBot(bot: MiniBot){
        val (directionValue, nearestEnemyMaster, _, master) = analyzeViewAsGatheringBot(bot.view)
        if (nearestEnemyMaster.isDefined && nearestEnemyMaster.get.stepCount <= 2){
            bot.explode(4)
            return
        }
        
        val energyLimit = bot.inputAsIntOrElse("limit", 0)
        if (bot.energy > energyLimit && !master.isEmpty){
            bot.set("mood" -> "Returning", "target" -> "")
            reactAsReturningBot(bot)
        }
        else{
            val lastDirection = bot.inputAsIntOrElse("lastDirection", 0)
            directionValue(lastDirection) += 10
            val bestDirection45 = directionValue.zipWithIndex.maxBy(_._1)._2
            val direction = XY.fromDirection45(bestDirection45)
            bot.move(direction)
            bot.set("lastDirection" -> bestDirection45)
        }
    }
    
    def reactAsReturningBot(bot: MiniBot){
        val (directionValue, nearestEnemyMaster) = analyzeViewAsReturningBot(bot, bot.view)
        if (nearestEnemyMaster.isDefined && nearestEnemyMaster.get.stepCount <= 2){
            bot.explode(4)
            return
        }
        
        val lastDirection = bot.inputAsIntOrElse("lastDirection", 0)
        directionValue(lastDirection) += 10
        val bestDirection45 = directionValue.zipWithIndex.maxBy(_._1)._2
        val direction = XY.fromDirection45(bestDirection45)
        bot.move(direction)
        bot.set("lastDirection" -> bestDirection45)
    }

    /** Analyze the view, building a map of attractiveness for the 45-degree directions and
      * recording other relevant data, such as the nearest elements of various kinds.
      */
    def analyzeViewAsMaster(view: View) = {
        val directionValue = Array.ofDim[Double](8)
        var nearestEnemyMaster: Option[XY] = None
        var nearestEnemySlave: Option[XY] = None
        var nearestPlant: Int = -1
        val cells = view.cells
        val cellCount = cells.length
        for(i <- 0 until cellCount) {
            val cellRelPos = view.relPosFromIndex(i)
            if(cellRelPos.isNonZero) {
                val stepDistance = cellRelPos.stepCount
                val value: Double = cells(i) match {
                    case 'm' => // another master: not dangerous, but an obstacle
                        nearestEnemyMaster = Some(cellRelPos)
                        if(stepDistance < 2) -1000 else 0

                    case 's' => // another slave: potentially dangerous?
                        nearestEnemySlave = Some(cellRelPos)
                        -100 / stepDistance

                    case 'S' => // out own slave
                        0.0

                    case 'B' => // good beast: valuable, but runs away
                        if(stepDistance == 1) 600
                        else if(stepDistance == 2) 300
                        else (150 - stepDistance * 15).max(10)

                    case 'P' => // good plant: less valuable, but does not run
                        nearestPlant = i
                        if(stepDistance == 1) 500
                        else if(stepDistance == 2) 300
                        else (150 - stepDistance * 10).max(10)

                    case 'b' => // bad beast: dangerous, but only if very close
                        if(stepDistance < 4) -400 / stepDistance else -50 / stepDistance

                    case 'p' => // bad plant: bad, but only if I step on it
                        if(stepDistance < 2) -1000 else 0

                    case 'W' => // wall: harmless, just don't walk into it
                        if(stepDistance < 2) -1000 else 0

                    case _ => 0.0
                }
                val direction45 = cellRelPos.toDirection45
                directionValue(direction45) += value
            }
        }
        (directionValue, nearestEnemyMaster, nearestEnemySlave, nearestPlant)
    }
    
    def analyzeViewAsGatheringBot(view: View) = {
        val directionValue = Array.ofDim[Double](8)
        var nearestEnemyMaster: Option[XY] = None
        var nearestEnemyBot: Option[XY] = None
        var master: Option[XY] = None
        
        view.cells.zipWithIndex foreach {case (c, i) =>
            val cellRelPos = view.relPosFromIndex(i)
            if (cellRelPos.isNonZero){
                val stepDistance = cellRelPos.stepCount
                val value: Double = c match{
                    case 'W' =>
                        if (stepDistance < 2) -1000 else 0
                    case 'm' =>
                        nearestEnemyMaster = Some(cellRelPos)
                        -100 / stepDistance
                    case 's' =>
                        nearestEnemyBot = Some(cellRelPos)
                        -100 / stepDistance
                    case 'S' =>
                        -50 / stepDistance
                    case 'M' =>
                        master = Some(cellRelPos)
                        0.0
                    case 'B' =>
                        if (stepDistance == 1) 600
                        else if (stepDistance == 2) 400
                        else (150 - stepDistance * 15).max(10)
                    case 'P' =>
                        if (stepDistance == 1) 500
                        else if (stepDistance == 2) 300
                        else (150 - stepDistance * 10).max(10)
                    case 'b' =>
                        if (stepDistance < 4) -400 / stepDistance else -50 / stepDistance
                    case 'p' =>
                        if (stepDistance < 2) -1000 else 0
                    case _ =>
                        0.0
                }
                val direction45 = cellRelPos.toDirection45
                directionValue(direction45) += value
            }
        }
        (directionValue, nearestEnemyMaster, nearestEnemyBot, master)
    }
    
    def analyzeViewAsReturningBot(bot: MiniBot, view: View) = {
        val directionValue = Array.ofDim[Double](8)
        var nearestEnemyMaster: Option[XY] = None
        
        view.cells.zipWithIndex foreach {case (c, i) =>
            val cellRelPos = view.relPosFromIndex(i)
            if (cellRelPos.isNonZero){
                val stepDistance = cellRelPos.stepCount
                val value: Double = c match{
                    case 'W' =>
                        if (stepDistance < 2) -1000 else 0
                    case 'm' =>
                        nearestEnemyMaster = Some(cellRelPos)
                        -100 / stepDistance
                    case 's' =>
                        -100 / stepDistance
                    case 'S' =>
                        -100 / stepDistance
                    case 'M' =>
                        1000
                    case 'B' =>
                        if (stepDistance == 1) 600 else 0
                    case 'P' =>
                        if (stepDistance == 1) 500 else 0
                    case 'b' =>
                        if (stepDistance < 4) -400 / stepDistance else -50 / stepDistance
                    case 'p' =>
                        if (stepDistance < 2) -1000 else 0
                    case _ =>
                        0.0
                }
                val direction45 = cellRelPos.toDirection45
                directionValue(direction45) += value
            }
        }
        (directionValue, nearestEnemyMaster)
    }
}

// -------------------------------------------------------------------------------------------------
// Framework
// -------------------------------------------------------------------------------------------------

class ControlFunctionFactory {
    def create = (input: String) => {
        val (opcode, params) = CommandParser(input)
        opcode match {
            case "React" =>
                val bot = new BotImpl(params)
                if( bot.generation == 0 ) {
                    ControlFunction.forMaster(bot)
                } else {
                    ControlFunction.forSlave(bot)
                }
                bot.toString
            case _ => "" // OK
        }
    }
}


// -------------------------------------------------------------------------------------------------


trait Bot {
    // inputs
    def inputOrElse(key: String, fallback: String): String
    def inputAsIntOrElse(key: String, fallback: Int): Int
    def inputAsXYOrElse(keyPrefix: String, fallback: XY): XY
    def view: View
    def energy: Int
    def time: Int
    def generation: Int
    def viewString: String

    // outputs
    def move(delta: XY) : Bot
    def say(text: String) : Bot
    def status(text: String) : Bot
    def spawn(offset: XY, params: (String,Any)*) : Bot
    def set(params: (String,Any)*) : Bot
    def log(text: String) : Bot
}

trait MiniBot extends Bot {
    // inputs
    def offsetToMaster: XY

    // outputs
    def explode(blastRadius: Int) : Bot
}


case class BotImpl(inputParams: Map[String, String]) extends MiniBot {
    // input
    def inputOrElse(key: String, fallback: String) = inputParams.getOrElse(key, fallback)
    def inputAsIntOrElse(key: String, fallback: Int) = inputParams.get(key).map(_.toInt).getOrElse(fallback)
    def inputAsXYOrElse(key: String, fallback: XY) = inputParams.get(key).map(s => XY(s)).getOrElse(fallback)

    val view = View(inputParams("view"))
    val energy = inputParams("energy").toInt
    val time = inputParams("time").toInt
    val generation = inputParams("generation").toInt
    def offsetToMaster = inputAsXYOrElse("master", XY.Zero)
    val viewString = inputParams("view")


    // output

    private var stateParams = Map.empty[String,Any]     // holds "Set()" commands
    private var commands = ""                           // holds all other commands
    private var debugOutput = ""                        // holds all "Log()" output

    /** Appends a new command to the command string; returns 'this' for fluent API. */
    private def append(s: String) : Bot = { commands += (if(commands.isEmpty) s else "|" + s); this }

    /** Renders commands and stateParams into a control function return string. */
    override def toString = {
        var result = commands
        if(!stateParams.isEmpty) {
            if(!result.isEmpty) result += "|"
            result += stateParams.map(e => e._1 + "=" + e._2).mkString("Set(",",",")")
        }
        if(!debugOutput.isEmpty) {
            if(!result.isEmpty) result += "|"
            result += "Log(text=" + debugOutput + ")"
        }
        result
    }

    def log(text: String) = { debugOutput += text + "\n"; this }
    def move(direction: XY) = append("Move(direction=" + direction + ")")
    def say(text: String) = append("Say(text=" + text + ")")
    def status(text: String) = append("Status(text=" + text + ")")
    def explode(blastRadius: Int) = append("Explode(size=" + blastRadius + ")")
    def spawn(offset: XY, params: (String,Any)*) =
        append("Spawn(direction=" + offset +
            (if(params.isEmpty) "" else "," + params.map(e => e._1 + "=" + e._2).mkString(",")) +
            ")")
    def set(params: (String,Any)*) = { stateParams ++= params; this }
    def set(keyPrefix: String, xy: XY) = { stateParams ++= List(keyPrefix+"x" -> xy.x, keyPrefix+"y" -> xy.y); this }
}


// -------------------------------------------------------------------------------------------------


/** Utility methods for parsing strings containing a single command of the format
  * "Command(key=value,key=value,...)"
  */
object CommandParser {
    /** "Command(..)" => ("Command", Map( ("key" -> "value"), ("key" -> "value"), ..}) */
    def apply(command: String): (String, Map[String, String]) = {
        /** "key=value" => ("key","value") */
        def splitParameterIntoKeyValue(param: String): (String, String) = {
            val segments = param.split('=')
            (segments(0), if(segments.length>=2) segments(1) else "")
        }

        val segments = command.split('(')
        if( segments.length != 2 )
            throw new IllegalStateException("invalid command: " + command)
        val opcode = segments(0)
        val params = segments(1).dropRight(1).split(',')
        val keyValuePairs = params.map(splitParameterIntoKeyValue).toMap
        (opcode, keyValuePairs)
    }
}


// -------------------------------------------------------------------------------------------------


/** Utility class for managing 2D cell coordinates.
  * The coordinate (0,0) corresponds to the top-left corner of the arena on screen.
  * The direction (1,-1) points right and up.
  */
case class XY(x: Int, y: Int) {
    override def toString = x + ":" + y

    def isNonZero = x != 0 || y != 0
    def isZero = x == 0 && y == 0
    def isNonNegative = x >= 0 && y >= 0

    def updateX(newX: Int) = XY(newX, y)
    def updateY(newY: Int) = XY(x, newY)

    def addToX(dx: Int) = XY(x + dx, y)
    def addToY(dy: Int) = XY(x, y + dy)

    def +(pos: XY) = XY(x + pos.x, y + pos.y)
    def -(pos: XY) = XY(x - pos.x, y - pos.y)
    def *(factor: Double) = XY((x * factor).intValue, (y * factor).intValue)

    def distanceTo(pos: XY): Double = (this - pos).length // Phythagorean
    def length: Double = math.sqrt(x * x + y * y) // Phythagorean

    def stepsTo(pos: XY): Int = (this - pos).stepCount // steps to reach pos: max delta X or Y
    def stepCount: Int = x.abs.max(y.abs) // steps from (0,0) to get here: max X or Y

    def signum = XY(x.signum, y.signum)

    def negate = XY(-x, -y)
    def negateX = XY(-x, y)
    def negateY = XY(x, -y)

    /** Returns the direction index with 'Right' being index 0, then clockwise in 45 degree steps. */
    def toDirection45: Int = {
        val unit = signum
        unit.x match {
            case -1 =>
                unit.y match {
                    case -1 =>
                        if(x < y * 3) Direction45.Left
                        else if(y < x * 3) Direction45.Up
                        else Direction45.UpLeft
                    case 0 =>
                        Direction45.Left
                    case 1 =>
                        if(-x > y * 3) Direction45.Left
                        else if(y > -x * 3) Direction45.Down
                        else Direction45.LeftDown
                }
            case 0 =>
                unit.y match {
                    case 1 => Direction45.Down
                    case 0 => throw new IllegalArgumentException("cannot compute direction index for (0,0)")
                    case -1 => Direction45.Up
                }
            case 1 =>
                unit.y match {
                    case -1 =>
                        if(x > -y * 3) Direction45.Right
                        else if(-y > x * 3) Direction45.Up
                        else Direction45.RightUp
                    case 0 =>
                        Direction45.Right
                    case 1 =>
                        if(x > y * 3) Direction45.Right
                        else if(y > x * 3) Direction45.Down
                        else Direction45.DownRight
                }
        }
    }

    def rotateCounterClockwise45 = XY.fromDirection45((signum.toDirection45 + 1) % 8)
    def rotateCounterClockwise90 = XY.fromDirection45((signum.toDirection45 + 2) % 8)
    def rotateClockwise45 = XY.fromDirection45((signum.toDirection45 + 7) % 8)
    def rotateClockwise90 = XY.fromDirection45((signum.toDirection45 + 6) % 8)


    def wrap(boardSize: XY) = {
        val fixedX = if(x < 0) boardSize.x + x else if(x >= boardSize.x) x - boardSize.x else x
        val fixedY = if(y < 0) boardSize.y + y else if(y >= boardSize.y) y - boardSize.y else y
        if(fixedX != x || fixedY != y) XY(fixedX, fixedY) else this
    }
}


object XY {
    /** Parse an XY value from XY.toString format, e.g. "2:3". */
    def apply(s: String) : XY = { val a = s.split(':'); XY(a(0).toInt,a(1).toInt) }

    val Zero = XY(0, 0)
    val One = XY(1, 1)

    val Right     = XY( 1,  0)
    val RightUp   = XY( 1, -1)
    val Up        = XY( 0, -1)
    val UpLeft    = XY(-1, -1)
    val Left      = XY(-1,  0)
    val LeftDown  = XY(-1,  1)
    val Down      = XY( 0,  1)
    val DownRight = XY( 1,  1)

    def fromDirection45(index: Int): XY = index match {
        case Direction45.Right => Right
        case Direction45.RightUp => RightUp
        case Direction45.Up => Up
        case Direction45.UpLeft => UpLeft
        case Direction45.Left => Left
        case Direction45.LeftDown => LeftDown
        case Direction45.Down => Down
        case Direction45.DownRight => DownRight
    }

    def fromDirection90(index: Int): XY = index match {
        case Direction90.Right => Right
        case Direction90.Up => Up
        case Direction90.Left => Left
        case Direction90.Down => Down
    }

    def apply(array: Array[Int]): XY = XY(array(0), array(1))
}


object Direction45 {
    val Right = 0
    val RightUp = 1
    val Up = 2
    val UpLeft = 3
    val Left = 4
    val LeftDown = 5
    val Down = 6
    val DownRight = 7
}


object Direction90 {
    val Right = 0
    val Up = 1
    val Left = 2
    val Down = 3
}


// -------------------------------------------------------------------------------------------------


case class View(cells: String) {
    val size = math.sqrt(cells.length).toInt
    val center = XY(size / 2, size / 2)

    def apply(relPos: XY) = cellAtRelPos(relPos)

    def indexFromAbsPos(absPos: XY) = absPos.x + absPos.y * size
    def absPosFromIndex(index: Int) = XY(index % size, index / size)
    def absPosFromRelPos(relPos: XY) = relPos + center
    def cellAtAbsPos(absPos: XY) = cells.charAt(indexFromAbsPos(absPos))

    def indexFromRelPos(relPos: XY) = indexFromAbsPos(absPosFromRelPos(relPos))
    def relPosFromAbsPos(absPos: XY) = absPos - center
    def relPosFromIndex(index: Int) = relPosFromAbsPos(absPosFromIndex(index))
    def cellAtRelPos(relPos: XY) = cells.charAt(indexFromRelPos(relPos))

    def offsetToNearest(c: Char) = {
        val matchingXY = cells.view.zipWithIndex.filter(_._1 == c)
        if( matchingXY.isEmpty )
            None
        else {
            val nearest = matchingXY.map(p => relPosFromIndex(p._2)).minBy(_.length)
            Some(nearest)
        }
    }
}
case class KeyValue(key: Int, value: XY) extends (Int, XY)(key, value)

case class BFS(bot: Bot ,view: String){

    private val matrix = Array.ofDim[Char](31, 31);
    private var neighbors = new Array[ListBuffer[KeyValue]](961)
    private var v = View(view);
    private var center = 480;
    private var dirs: List[XY] = List(XY.RightUp, XY.Up, XY.UpLeft, XY.Right,XY.Left,XY.DownRight,XY.Down,XY.LeftDown)
    var path = new StringBuilder
    def isGood(c: Char) = (c == "_" || c == "P"  || c == "B")
    def fillMatrix(){
        for(i <- 0 to 30 ){
            var str = bot.viewString.substring(i*31, (i+1) * 31);
            matrix(i) = str.toArray
            var bb = KeyValue(i,XY.Up)
         }
    }
    def findNeighborsFor(i: Int, j: Int){
        //---
        //-E-
        //---
        var ii = i - 1;
        var jj = j - 1;
        var dir = 0;
        neighbors(i*31 + j) = new  ListBuffer[KeyValue]();
        var list = neighbors(i*31 + j);
        for(ii <- i - 1 to i + 1; jj <- j -1 to j + 1 ){
            if(ii == i && jj == j){
            }else{
                if(ii >= 0 && jj >= 0 && ii < 31 && jj < 31  ){
                  if(isGood(matrix(ii)(jj))){
                      bot.log("is gud")
                      var kk = KeyValue(ii*31 + jj,dirs(dir))
                      list += kk
                      list.toList
                     
                  }
                }
                dir +=1
            }
        }
        neighbors(i*31 + j) = list
    }
    
    def findAllNeighbors(){
        var i = 0;
        var j = 0;
        for(i <- 0 to 30; j <- 0 to 30){
            findNeighborsFor(i,j);
        }
    }
    
    
    def bfsPath(source:Int, dest:Int){
        var shortestPathList: ArrayBuffer[KeyValue] = new ArrayBuffer[KeyValue]();
        var visited: Array[Boolean] = new Array[Boolean](961)
        var i= 0
        for(i <- 0 to 960){
            visited(i) = false
        }
        if (source == dest){
            return ;
        }
        var queue  = Queue[KeyValue]()
    	var pathStack  = Stack[KeyValue]()
        var sss =KeyValue(source,XY(0,0)) 
        queue.enqueue(sss)
        pathStack.push(sss)
        visited(source) = true
        val outer = new Breaks;
        val inner = new Breaks;
        val outer1 = new Breaks;
        val inner1 = new Breaks;
        outer.breakable{
            while(!queue.isEmpty){
            val temp = queue.dequeue
            var list = neighbors(temp.key).toList
            var ppp: KeyValue= KeyValue(-1,XY(0,0))
            inner.breakable{
                for(ppp <- list){
                if(visited(ppp.key) == false){
                    visited(ppp.key) = true
                    queue.enqueue(ppp)
                    pathStack.push(ppp)
                    if(ppp.key == dest){
                        inner.break;
                    }
                }
            }}
    	   }
        }
        // end of BFS, now make the path
        var node: KeyValue = KeyValue(0,XY(0,0))
        var currentSrc = KeyValue(dest,XY(0,0))
        shortestPathList += currentSrc;
        outer1.breakable{
            while(!pathStack.isEmpty){
            node = pathStack.pop()
            var list = neighbors(currentSrc.key).toList
            inner1.breakable{
                for(ppp <- list){
                if(ppp.key == node.key){
                    shortestPathList += node
                    currentSrc = node;
                    if(node.key == source){
                        inner1.break
                        outer1.break
                    }
                }
            }}
           }
        }
        var abc = shortestPathList.toArray.reverse
        var vvv : KeyValue = KeyValue(0,XY(0,0))
        for(vvv <- abc){
            path ++= vvv.value.toString() + ";"
        }
        path.toString
    }
    
    def printMatrixToLog() = bot.log(matrix.map(_.mkString).mkString("\n"))
    
}
/*case class Node(key: Int, value: XY) extends (Int, XY)(key, value)

case class BreadthFirstSearch(bot: Bot, view: String){
    val viewMatrix = Array.ofDim[Char](31,31)
    var adjacentNodes = new Array[ListBuffer[Node]](961)
    var v = View(view)
    var center = 480
    var directions: List[XY] = List(XY.RightUp, XY.Up, XY.UpLeft, XY.Right,XY.Left,XY.DownRight,XY.Down,XY.LeftDown)
    var path = new StringBuilder
    def goodTarget(c: Char) = (c == "_" || c == "P" || c == "B")
    def stringViewToMatrix(){
        for (i <- 0 to 30){
            viewMatrix(i) = bot.viewString.substring(i*31, (i+1) * 31).toArray
            var bb = Node(i,XY.Up)
        }
    }
    def findAdjacentNodes(i: Int, j: Int){
        var dir = 0
        adjacentNodes(i*31 + j) = new ListBuffer[Node]()
        var list = adjacentNodes(i * 31 + j)
        for (ii <- i-1 to i+1; jj <- j-1 to j+1){
            if(!(ii == i && jj == j)){
                if(ii >= 0 && jj >= 0 && ii < 31 && jj < 31 && goodTarget(viewMatrix(ii)(jj))){
                    list += Node(ii*31 + jj, directions(dir))
                    list.toList
                }
                dir += 1
            }
        }
        adjacentNodes(i*31 + j) = list
    }
    def findAllAdjacentNodes() = for(i <- 0 to 30; j <- 0 to 30) findAdjacentNodes(i, j)
    
    def findPath(source: Int, destination: Int){
        if (source == destination) return
        var shortestPath: ArrayBuffer[Node] = new ArrayBuffer[Node]()
        var visited: Array[Boolean] = new Array[Boolean](961)
        for (i <- 0 to 960) visited(i) = false
        var queue = Queue[Node]()
        var pathStack = Stack[Node]()
        var sss = Node(source, XY(0,0))
        queue.enqueue(sss)
        pathStack.push(sss)
        visited(source) = true
        val inner = new Breaks;
        val outer1 = new Breaks;
        val inner1 = new Breaks;
        while(!queue.isEmpty){
            val temp = queue.dequeue
            var list = adjacentNodes(temp.key).toList
            var ppp: Node = Node(-1, XY(0,0))
            inner.breakable{
                for(ppp <- list){
                    if(visited(ppp.key) == false){
                        visited(ppp.key) = true
                        queue.enqueue(ppp)
                        pathStack.push(ppp)
                        if(ppp.key == destination){
                            inner.break;
                        }
                    }
                }
            }
        }
        var node: Node = Node(0, XY(0,0))
        var currentSource = Node(destination, XY(0,0))
        shortestPath += currentSource
        outer1.breakable{
            while(!pathStack.isEmpty){
                node = pathStack.pop()
                var list = adjacentNodes(currentSource.key).toList
                inner1.breakable{
                    for(ppp <- list){
                        if(ppp.key == node.key){
                            shortestPath += node
                            currentSource = node
                            if (node.key == source){
                                inner1.break
                                outer1.break
                            }
                        }
                    }
                }
            }
        }
        var abc = shortestPath.toArray.reverse
        for (vvv <- abc) path ++= vvv.value.toString() + ";"
        path.toString
    }
}*/
