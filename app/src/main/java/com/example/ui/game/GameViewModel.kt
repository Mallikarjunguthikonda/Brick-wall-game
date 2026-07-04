package com.example.ui.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.HighScoreEntity
import com.example.data.repository.HighScoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class GameState {
    MENU,
    PLAYING,
    PAUSED,
    LEVEL_COMPLETED,
    GAME_OVER,
    HIGH_SCORES
}

enum class PowerUpType(
    val displayName: String,
    val color: Long, // Hex color for neon glow
    val iconString: String
) {
    EXTRA_BALL("Multi-Ball", 0xFFD0BCFF, "🍒"),     // Sophisticated Lavender
    EXPAND_PADDLE("Expand Paddle", 0xFFB1D18A, "↔️"), // Soft Olive Green
    SHIELD("Safety Barrier", 0xFF4FD8EB, "🛡️"),    // Soft Cyan
    STICKY_PADDLE("Sticky Pad", 0xFFE8DEF8, "🕸️"),   // Pale Lavender Yellow
    LASER_PADDLE("Laser Blaster", 0xFFFFB4AB, "⚡"), // Soft Coral Red
    SLOW_BALL("Time Warp", 0xFFCAC4D0, "❄️")        // Sophisticated Grey
}

data class Ball(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float = 14f,
    var isStuck: Boolean = false,
    var stuckOffset: Float = 0f // Offset relative to paddle center
)

data class Paddle(
    var x: Float, // Center X coordinate
    val y: Float = 1100f,
    var width: Float = 140f,
    val height: Float = 24f
)

data class FloatRange(val min: Float, val max: Float)

data class Brick(
    val id: Int,
    val x: Float, // Left X
    val y: Float, // Top Y
    val width: Float,
    val height: Float,
    var hp: Int,
    val maxHp: Int,
    val colorHex: Long,
    val isMoving: Boolean = false,
    val moveRange: FloatRange = FloatRange(0f, 0f),
    var moveDir: Int = 1 // 1 for right, -1 for left
)

data class PowerUp(
    val x: Float,
    val y: Float,
    val type: PowerUpType,
    val radius: Float = 16f,
    val vy: Float = 5f
)

data class ActivePowerUp(
    val type: PowerUpType,
    val expirationTime: Long // Epoch timestamp
)

data class LaserBeam(
    val x: Float,
    val y: Float,
    val vy: Float = -14f,
    val width: Float = 6f,
    val height: Float = 18f
)

data class Obstacle(
    var x: Float, // Center X
    val y: Float, // Center Y
    val width: Float,
    val height: Float,
    var vx: Float,
    val minX: Float,
    val maxX: Float
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Long,
    var alpha: Float = 1.0f,
    val decay: Float = 0.03f,
    val radius: Float = 6f
)

data class GameUiState(
    val gameState: GameState = GameState.MENU,
    val score: Int = 0,
    val lives: Int = 3,
    val level: Int = 1,
    val balls: List<Ball> = emptyList(),
    val paddle: Paddle = Paddle(x = 400f),
    val bricks: List<Brick> = emptyList(),
    val powerUps: List<PowerUp> = emptyList(),
    val activePowerUps: List<ActivePowerUp> = emptyList(),
    val laserBeams: List<LaserBeam> = emptyList(),
    val obstacles: List<Obstacle> = emptyList(),
    val particles: List<Particle> = emptyList(),
    val topScores: List<HighScoreEntity> = emptyList(),
    val currentHighScore: Int = 0,
    val shieldActive: Boolean = false,
    val isPaused: Boolean = false
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = HighScoreRepository(database.highScoreDao())

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    val topScores: StateFlow<List<HighScoreEntity>> = repository.topScores
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var gameJob: Job? = null
    private var lastLaserFireTime = 0L
    private val pendingParticles = mutableListOf<Particle>()

    init {
        // Observe top scores to update local highscore
        viewModelScope.launch {
            topScores.collect { scores ->
                val maxScore = scores.maxOfOrNull { it.score } ?: 0
                _uiState.value = _uiState.value.copy(
                    topScores = scores,
                    currentHighScore = maxScore
                )
            }
        }
        startGameLoop()
    }

    fun changeState(newState: GameState) {
        _uiState.value = _uiState.value.copy(gameState = newState)
        if (newState == GameState.MENU) {
            resetGameSession()
        }
    }

    private fun resetGameSession() {
        _uiState.value = _uiState.value.copy(
            score = 0,
            lives = 3,
            level = 1,
            balls = emptyList(),
            paddle = Paddle(x = 400f),
            bricks = emptyList(),
            powerUps = emptyList(),
            activePowerUps = emptyList(),
            laserBeams = emptyList(),
            obstacles = emptyList(),
            particles = emptyList(),
            shieldActive = false,
            isPaused = false
        )
    }

    fun startNewGame() {
        resetGameSession()
        loadLevel(1)
        _uiState.value = _uiState.value.copy(gameState = GameState.PLAYING)
    }

    fun pauseGame() {
        _uiState.value = _uiState.value.copy(isPaused = true)
    }

    fun resumeGame() {
        _uiState.value = _uiState.value.copy(isPaused = false)
    }

    fun togglePause() {
        _uiState.value = _uiState.value.copy(isPaused = !_uiState.value.isPaused)
    }

    private fun loadLevel(levelNum: Int) {
        val bricks = generateBricksForLevel(levelNum)
        val obstacles = generateObstaclesForLevel(levelNum)
        val initialPaddle = Paddle(x = 400f)
        val initialBall = Ball(
            x = 400f,
            y = initialPaddle.y - 14f,
            vx = 0f,
            vy = 0f,
            isStuck = true
        )

        _uiState.value = _uiState.value.copy(
            level = levelNum,
            bricks = bricks,
            obstacles = obstacles,
            balls = listOf(initialBall),
            paddle = initialPaddle,
            powerUps = emptyList(),
            activePowerUps = emptyList(),
            laserBeams = emptyList(),
            particles = emptyList(),
            shieldActive = false,
            isPaused = false
        )
    }

    fun nextLevel() {
        val nextLvl = _uiState.value.level + 1
        if (nextLvl > 5) {
            // Completed all levels! Win screen can be simulated as Game Over or loop back.
            // Let's loop back with extra speed or mark game over. Let's loop back and add score bonus!
            val bonus = _uiState.value.score + 5000
            _uiState.value = _uiState.value.copy(score = bonus)
            loadLevel(1)
            _uiState.value = _uiState.value.copy(gameState = GameState.PLAYING)
        } else {
            loadLevel(nextLvl)
            _uiState.value = _uiState.value.copy(gameState = GameState.PLAYING)
        }
    }

    fun restartLevel() {
        loadLevel(_uiState.value.level)
        _uiState.value = _uiState.value.copy(gameState = GameState.PLAYING)
    }

    fun launchBall() {
        val state = _uiState.value
        if (state.gameState != GameState.PLAYING || state.isPaused) return

        val baseSpeed = when (state.level) {
            1 -> 8f
            2 -> 9f
            3 -> 10f
            4 -> 11f
            else -> 12f
        }

        val updatedBalls = state.balls.map { ball ->
            if (ball.isStuck) {
                // Launch ball at slight upward angle (between -30 to 30 degrees)
                val angleRad = (Random.nextFloat() * 40f - 20f) * Math.PI / 180f
                ball.copy(
                    vx = (baseSpeed * sin(angleRad)).toFloat(),
                    vy = -(baseSpeed * cos(angleRad)).toFloat(),
                    isStuck = false
                )
            } else {
                ball
            }
        }

        _uiState.value = state.copy(balls = updatedBalls)
        SoundPlayer.playPaddleHit()
    }

    fun movePaddleRelative(dx: Float) {
        val state = _uiState.value
        if (state.gameState != GameState.PLAYING || state.isPaused) return

        val currentPaddle = state.paddle
        val halfWidth = currentPaddle.width / 2f
        val newX = (currentPaddle.x + dx).coerceIn(halfWidth, 800f - halfWidth)

        val updatedPaddle = currentPaddle.copy(x = newX)

        // Move stuck balls along with the paddle
        val updatedBalls = state.balls.map { ball ->
            if (ball.isStuck) {
                ball.copy(x = newX + ball.stuckOffset)
            } else {
                ball
            }
        }

        _uiState.value = state.copy(paddle = updatedPaddle, balls = updatedBalls)
    }

    fun movePaddleAbsolute(targetX: Float) {
        val state = _uiState.value
        if (state.gameState != GameState.PLAYING || state.isPaused) return

        val currentPaddle = state.paddle
        val halfWidth = currentPaddle.width / 2f
        val newX = targetX.coerceIn(halfWidth, 800f - halfWidth)

        val updatedPaddle = currentPaddle.copy(x = newX)

        val updatedBalls = state.balls.map { ball ->
            if (ball.isStuck) {
                ball.copy(x = newX + ball.stuckOffset)
            } else {
                ball
            }
        }

        _uiState.value = state.copy(paddle = updatedPaddle, balls = updatedBalls)
    }

    private fun startGameLoop() {
        gameJob?.cancel()
        gameJob = viewModelScope.launch {
            var lastTime = System.currentTimeMillis()
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - lastTime
                if (elapsed >= 16) { // ~60 FPS update tick
                    if (_uiState.value.gameState == GameState.PLAYING && !_uiState.value.isPaused) {
                        tickGame()
                    }
                    lastTime = currentTime
                }
                delay(16)
            }
        }
    }

    private fun tickGame() {
        val state = _uiState.value
        val currentLevel = state.level
        val now = System.currentTimeMillis()

        // 1. Filter out expired active power-ups
        val nonExpiredPowerUps = state.activePowerUps.filter { it.expirationTime > now }
        val isSticky = nonExpiredPowerUps.any { it.type == PowerUpType.STICKY_PADDLE }
        val isLaser = nonExpiredPowerUps.any { it.type == PowerUpType.LASER_PADDLE }
        val isExpanded = nonExpiredPowerUps.any { it.type == PowerUpType.EXPAND_PADDLE }
        val isSlow = nonExpiredPowerUps.any { it.type == PowerUpType.SLOW_BALL }
        val shieldActive = state.shieldActive || nonExpiredPowerUps.any { it.type == PowerUpType.SHIELD }

        // Update paddle width based on power-up status
        val targetPaddleWidth = if (isExpanded) 220f else 140f
        val currentPaddle = state.paddle.copy(width = targetPaddleWidth)

        // Determine base speed
        val baseSpeed = when (currentLevel) {
            1 -> 8f
            2 -> 9f
            3 -> 10f
            4 -> 11f
            else -> 12f
        }
        val ballSpeedMultiplier = if (isSlow) 0.6f else 1.0f
        val speedLimit = baseSpeed * ballSpeedMultiplier

        // Update balls
        val updatedBalls = state.balls.map { ball ->
            if (ball.isStuck) {
                ball.copy(
                    x = currentPaddle.x + ball.stuckOffset,
                    y = currentPaddle.y - ball.radius
                )
            } else {
                var nx = ball.x + ball.vx * ballSpeedMultiplier
                var ny = ball.y + ball.vy * ballSpeedMultiplier
                var nvx = ball.vx
                var nvy = ball.vy

                // Wall collisions
                if (nx - ball.radius <= 0f) {
                    nvx = abs(nvx)
                    nx = ball.radius
                    SoundPlayer.playPaddleHit()
                    spawnBounceParticles(nx, ny, 0xFF00E5FF)
                } else if (nx + ball.radius >= 800f) {
                    nvx = -abs(nvx)
                    nx = 800f - ball.radius
                    SoundPlayer.playPaddleHit()
                    spawnBounceParticles(nx, ny, 0xFF00E5FF)
                }

                if (ny - ball.radius <= 0f) {
                    nvy = abs(nvy)
                    ny = ball.radius
                    SoundPlayer.playPaddleHit()
                    spawnBounceParticles(nx, ny, 0xFF00E5FF)
                }

                ball.copy(x = nx, y = ny, vx = nvx, vy = nvy)
            }
        }.toMutableList()

        // 2. Obstacles Movement & Collision (Level 3+)
        val updatedObstacles = state.obstacles.map { obs ->
            var nx = obs.x + obs.vx
            var nvx = obs.vx
            if (nx - obs.width / 2 <= obs.minX) {
                nx = obs.minX + obs.width / 2
                nvx = abs(nvx)
            } else if (nx + obs.width / 2 >= obs.maxX) {
                nx = obs.maxX - obs.width / 2
                nvx = -abs(nvx)
            }
            obs.copy(x = nx, vx = nvx)
        }

        // Bounce balls off obstacles
        updatedBalls.forEachIndexed { idx, ball ->
            if (!ball.isStuck) {
                updatedObstacles.forEach { obs ->
                    val left = obs.x - obs.width / 2
                    val right = obs.x + obs.width / 2
                    val top = obs.y - obs.height / 2
                    val bottom = obs.y + obs.height / 2

                    val closestX = ball.x.coerceIn(left, right)
                    val closestY = ball.y.coerceIn(top, bottom)

                    val dx = ball.x - closestX
                    val dy = ball.y - closestY
                    val distSq = dx * dx + dy * dy

                    if (distSq < ball.radius * ball.radius) {
                        val fromLeft = ball.x < left
                        val fromRight = ball.x > right
                        val fromTop = ball.y < top
                        val fromBottom = ball.y > bottom

                        if (fromLeft || fromRight) {
                            ball.vx = if (fromLeft) -abs(ball.vx) else abs(ball.vx)
                            ball.x = if (fromLeft) left - ball.radius else right + ball.radius
                        } else if (fromTop || fromBottom) {
                            ball.vy = if (fromTop) -abs(ball.vy) else abs(ball.vy)
                            ball.y = if (fromTop) top - ball.radius else bottom + ball.radius
                        } else {
                            ball.vy = -ball.vy
                        }
                        SoundPlayer.playPaddleHit()
                        spawnBounceParticles(ball.x, ball.y, 0xFFCCCCCC)
                        updatedBalls[idx] = ball
                    }
                }
            }
        }

        // 3. Paddle collision
        updatedBalls.forEachIndexed { idx, ball ->
            if (!ball.isStuck && ball.vy > 0f) {
                val pLeft = currentPaddle.x - currentPaddle.width / 2
                val pRight = currentPaddle.x + currentPaddle.width / 2
                val pTop = currentPaddle.y
                val pBottom = currentPaddle.y + currentPaddle.height

                if (ball.x + ball.radius >= pLeft &&
                    ball.x - ball.radius <= pRight &&
                    ball.y + ball.radius >= pTop &&
                    ball.y - ball.radius <= pBottom
                ) {
                    val hitOffset = (ball.x - currentPaddle.x) / (currentPaddle.width / 2f)
                    val clampedOffset = hitOffset.coerceIn(-0.95f, 0.95f)

                    val totalSpeed = sqrt(ball.vx * ball.vx + ball.vy * ball.vy)
                    val actualSpeed = if (totalSpeed < 4f) speedLimit else totalSpeed

                    val maxAngle = 60.0 * Math.PI / 180.0
                    val angle = clampedOffset * maxAngle

                    ball.vx = (actualSpeed * sin(angle)).toFloat()
                    ball.vy = (-actualSpeed * cos(angle)).toFloat()
                    ball.y = pTop - ball.radius

                    if (isSticky) {
                        ball.isStuck = true
                        ball.stuckOffset = ball.x - currentPaddle.x
                    }

                    SoundPlayer.playPaddleHit()
                    spawnBounceParticles(ball.x, ball.y, 0xFF00FFCC)
                    updatedBalls[idx] = ball
                }
            }
        }

        // 4. Laser blasts & collisions
        val updatedLasers = state.laserBeams.map { laser ->
            laser.copy(y = laser.y + laser.vy)
        }.filter { it.y > -20f }.toMutableList()

        if (isLaser && now - lastLaserFireTime >= 600L) {
            lastLaserFireTime = now
            val pLeft = currentPaddle.x - currentPaddle.width / 2 + 12f
            val pRight = currentPaddle.x + currentPaddle.width / 2 - 12f
            updatedLasers.add(LaserBeam(x = pLeft, y = currentPaddle.y - 10f))
            updatedLasers.add(LaserBeam(x = pRight, y = currentPaddle.y - 10f))
            SoundPlayer.playPaddleHit()
        }

        // 5. Bricks collision with balls & lasers
        val remainingBricks = state.bricks.map { it.copy() }.toMutableList()
        val spawnedPowerUps = state.powerUps.toMutableList()
        var addedScore = 0

        // Collision with balls
        updatedBalls.forEachIndexed { bIdx, ball ->
            if (!ball.isStuck) {
                val iterator = remainingBricks.iterator()
                while (iterator.hasNext()) {
                    val brick = iterator.next()
                    if (brick.hp <= 0) continue

                    val bLeft = brick.x
                    val bRight = brick.x + brick.width
                    val bTop = brick.y
                    val bBottom = brick.y + brick.height

                    val closestX = ball.x.coerceIn(bLeft, bRight)
                    val closestY = ball.y.coerceIn(bTop, bBottom)

                    val dx = ball.x - closestX
                    val dy = ball.y - closestY
                    val distSq = dx * dx + dy * dy

                    if (distSq < ball.radius * ball.radius) {
                        brick.hp--

                        val fromLeft = ball.x < bLeft
                        val fromRight = ball.x > bRight
                        val fromTop = ball.y < bTop
                        val fromBottom = ball.y > bBottom

                        if (fromLeft || fromRight) {
                            ball.vx = if (fromLeft) -abs(ball.vx) else abs(ball.vx)
                            ball.x = if (fromLeft) bLeft - ball.radius else bRight + ball.radius
                        } else if (fromTop || fromBottom) {
                            ball.vy = if (fromTop) -abs(ball.vy) else abs(ball.vy)
                            ball.y = if (fromTop) bTop - ball.radius else bBottom + ball.radius
                        } else {
                            ball.vy = -ball.vy
                        }

                        if (brick.hp <= 0) {
                            addedScore += brick.maxHp * 20
                            iterator.remove()
                            spawnExplosionParticles(brick.x + brick.width / 2f, brick.y + brick.height / 2f, brick.colorHex)
                            SoundPlayer.playBrickHit(brick.maxHp)

                            if (Random.nextFloat() < 0.22f) {
                                val type = PowerUpType.values().random()
                                spawnedPowerUps.add(PowerUp(x = brick.x + brick.width / 2f, y = brick.y + brick.height / 2f, type = type))
                            }
                        } else {
                            addedScore += 5
                            spawnBounceParticles(closestX, closestY, brick.colorHex)
                            SoundPlayer.playBrickHit(brick.maxHp)
                        }
                        updatedBalls[bIdx] = ball
                        break // Prevent double collision in a single tick
                    }
                }
            }
        }

        // Collision with lasers
        val laserIterator = updatedLasers.iterator()
        while (laserIterator.hasNext()) {
            val laser = laserIterator.next()
            var laserConsumed = false
            val brickIterator = remainingBricks.iterator()
            while (brickIterator.hasNext()) {
                val brick = brickIterator.next()
                if (laser.x >= brick.x && laser.x <= brick.x + brick.width &&
                    laser.y >= brick.y && laser.y <= brick.y + brick.height
                ) {
                    brick.hp--
                    laserConsumed = true
                    if (brick.hp <= 0) {
                        addedScore += brick.maxHp * 20
                        brickIterator.remove()
                        spawnExplosionParticles(brick.x + brick.width / 2f, brick.y + brick.height / 2f, brick.colorHex)
                        SoundPlayer.playBrickHit(brick.maxHp)

                        if (Random.nextFloat() < 0.22f) {
                            val type = PowerUpType.values().random()
                            spawnedPowerUps.add(PowerUp(x = brick.x + brick.width / 2f, y = brick.y + brick.height / 2f, type = type))
                        }
                    } else {
                        addedScore += 5
                        spawnBounceParticles(laser.x, brick.y + brick.height, brick.colorHex)
                        SoundPlayer.playBrickHit(brick.maxHp)
                    }
                    break
                }
            }
            if (laserConsumed) {
                laserIterator.remove()
            }
        }

        // 6. Update moving bricks (Level 5+)
        val updatedBricks = remainingBricks.map { brick ->
            if (brick.isMoving) {
                var nx = brick.x + brick.moveDir * 2.5f
                var nDir = brick.moveDir
                if (nx < brick.moveRange.min) {
                    nx = brick.moveRange.min
                    nDir = 1
                } else if (nx + brick.width > brick.moveRange.max) {
                    nx = brick.moveRange.max - brick.width
                    nDir = -1
                }
                brick.copy(x = nx, moveDir = nDir)
            } else {
                brick
            }
        }

        // 7. Handle balls off screen
        val survivingBalls = updatedBalls.filter { ball ->
            ball.y - ball.radius <= 1200f
        }.toMutableList()

        var nextLives = state.lives
        var nextGameState = state.gameState
        var currentShieldActive = shieldActive

        if (survivingBalls.isEmpty() && state.gameState == GameState.PLAYING) {
            if (currentShieldActive) {
                currentShieldActive = false
                // Bounce ball back off the safety shield
                val rescuedBall = Ball(
                    x = currentPaddle.x,
                    y = currentPaddle.y - 40f,
                    vx = (Random.nextFloat() * 6f - 3f),
                    vy = -speedLimit,
                    isStuck = false
                )
                survivingBalls.add(rescuedBall)
                SoundPlayer.playPaddleHit()
                // Clear active shield powerup from the list
                val cleanActive = nonExpiredPowerUps.filter { it.type != PowerUpType.SHIELD }
                _uiState.value = state.copy(
                    balls = survivingBalls,
                    activePowerUps = cleanActive,
                    shieldActive = false
                )
                return
            } else {
                nextLives--
                SoundPlayer.playLoseLife()
                if (nextLives <= 0) {
                    nextGameState = GameState.GAME_OVER
                    saveHighScoreToDatabase()
                } else {
                    // Spawn a new ball stuck to the paddle
                    survivingBalls.add(
                        Ball(
                            x = currentPaddle.x,
                            y = currentPaddle.y - 14f,
                            vx = 0f,
                            vy = 0f,
                            isStuck = true
                        )
                    )
                }
            }
        }

        // 8. Powerup collision with paddle
        val remainingPowerUps = mutableListOf<PowerUp>()
        val newlyActivatedPowerUps = nonExpiredPowerUps.toMutableList()

        spawnedPowerUps.forEach { powerUp ->
            val ny = powerUp.y + powerUp.vy
            val pLeft = currentPaddle.x - currentPaddle.width / 2f
            val pRight = currentPaddle.x + currentPaddle.width / 2f
            val pTop = currentPaddle.y
            val pBottom = currentPaddle.y + currentPaddle.height

            if (powerUp.x + powerUp.radius >= pLeft &&
                powerUp.x - powerUp.radius <= pRight &&
                ny + powerUp.radius >= pTop &&
                ny - powerUp.radius <= pBottom
            ) {
                SoundPlayer.playPowerUp()
                when (powerUp.type) {
                    PowerUpType.EXTRA_BALL -> {
                        val sourceBall = survivingBalls.firstOrNull() ?: Ball(x = currentPaddle.x, y = 1000f, vx = 4f, vy = -speedLimit)
                        survivingBalls.add(
                            Ball(
                                x = sourceBall.x,
                                y = sourceBall.y,
                                vx = -sourceBall.vx + (Random.nextFloat() * 3f - 1.5f),
                                vy = -abs(sourceBall.vy),
                                isStuck = false
                            )
                        )
                    }
                    PowerUpType.SHIELD -> {
                        currentShieldActive = true
                    }
                    else -> {
                        newlyActivatedPowerUps.add(ActivePowerUp(type = powerUp.type, expirationTime = now + 10000L))
                    }
                }
                spawnExplosionParticles(powerUp.x, powerUp.y, powerUp.type.color, count = 12)
            } else if (ny < 1250f) {
                remainingPowerUps.add(powerUp.copy(y = ny))
            }
        }

        // 9. Update particles
        val updatedParticles = state.particles.map { p ->
            p.copy(
                x = p.x + p.vx,
                y = p.y + p.vy,
                vy = p.vy + 0.08f, // light gravity
                alpha = p.alpha - p.decay
            )
        }.filter { it.alpha > 0f }

        // 10. Check Level completed (only check if there are breakable bricks)
        val levelClear = updatedBricks.none { it.hp > 0 }
        if (levelClear && nextGameState == GameState.PLAYING) {
            nextGameState = GameState.LEVEL_COMPLETED
            SoundPlayer.playLevelComplete()
            spawnExplosionParticles(400f, 600f, 0xFF00FFCC, count = 40)
        }

        _uiState.value = state.copy(
            gameState = nextGameState,
            score = state.score + addedScore,
            lives = nextLives,
            balls = survivingBalls,
            paddle = currentPaddle,
            bricks = updatedBricks,
            powerUps = remainingPowerUps,
            activePowerUps = newlyActivatedPowerUps,
            laserBeams = updatedLasers,
            particles = (updatedParticles + pendingParticles).take(150),
            shieldActive = currentShieldActive
        )
        pendingParticles.clear()
    }

    private fun spawnExplosionParticles(x: Float, y: Float, color: Long, count: Int = 18) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2f * Math.PI
            val speed = 2f + Random.nextFloat() * 6f
            pendingParticles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (speed * cos(angle)).toFloat(),
                    vy = (speed * sin(angle)).toFloat(),
                    color = color,
                    alpha = 1.0f,
                    decay = 0.02f + Random.nextFloat() * 0.02f,
                    radius = 3f + Random.nextFloat() * 4f
                )
            )
        }
    }

    private fun spawnBounceParticles(x: Float, y: Float, color: Long, count: Int = 6) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * Math.PI
            val speed = 1f + Random.nextFloat() * 3f
            pendingParticles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (speed * cos(angle)).toFloat(),
                    vy = -(speed * sin(angle)).toFloat(),
                    color = color,
                    alpha = 1.0f,
                    decay = 0.04f + Random.nextFloat() * 0.04f,
                    radius = 2f + Random.nextFloat() * 2f
                )
            )
        }
    }

    fun saveHighScore(playerName: String) {
        val finalName = if (playerName.isBlank()) "Anonymous" else playerName
        viewModelScope.launch {
            repository.insertScore(
                HighScoreEntity(
                    playerName = finalName,
                    score = _uiState.value.score,
                    levelReached = _uiState.value.level
                )
            )
            changeState(GameState.HIGH_SCORES)
        }
    }

    fun clearHighScores() {
        viewModelScope.launch {
            repository.clearAllScores()
        }
    }

    private fun saveHighScoreToDatabase() {
        // Handled upon explicit input by the user on game over screen.
    }

    private fun generateBricksForLevel(level: Int): List<Brick> {
        val bricks = mutableListOf<Brick>()
        val cols = 8
        val rows = when (level) {
            1 -> 4
            2 -> 5
            3 -> 6
            4 -> 7
            else -> 8
        }

        val sidePadding = 35f
        val topPadding = 160f
        val availableWidth = 800f - 2 * sidePadding
        val brickWidth = (availableWidth / cols) - 4f
        val brickHeight = 32f

        val neonColors = listOf(
            0xFFD0BCFF, // Sophisticated Lavender
            0xFFFFB4AB, // Soft Coral Pink
            0xFFB1D18A, // Soft Olive Green
            0xFF4FD8EB, // Soft Cyan
            0xFFF2B8B5, // Soft Rose Red
            0xFFE8DEF8, // Soft Blue Lavender
            0xFFCCC2DC, // Soft Mauve Grey
            0xFFE6E1E5  // Soft Light Grey
        )

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val bx = sidePadding + c * (brickWidth + 4f) + 2f
                val by = topPadding + r * (brickHeight + 6f)

                // Strength based on row and level
                val maxHp = when (level) {
                    1 -> 1
                    2 -> if (r % 2 == 0) 2 else 1
                    3 -> when (r) {
                        0, 1 -> 3
                        2, 3 -> 2
                        else -> 1
                    }
                    4 -> when (r) {
                        0 -> 4
                        1, 2 -> 3
                        3, 4 -> 2
                        else -> 1
                    }
                    else -> when (r) {
                        0, 1 -> 5
                        2, 3 -> 4
                        4 -> 3
                        5 -> 2
                        else -> 1
                    }
                }

                val colorHex = neonColors[r % neonColors.size]

                // Moving bricks in Level 5
                val isMoving = level >= 5 && (r % 2 == 0)
                val moveRange = if (isMoving) {
                    val min = bx - 40f
                    val max = bx + brickWidth + 40f
                    FloatRange(min.coerceAtLeast(10f), max.coerceAtMost(790f))
                } else {
                    FloatRange(0f, 0f)
                }

                bricks.add(
                    Brick(
                        id = r * cols + c,
                        x = bx,
                        y = by,
                        width = brickWidth,
                        height = brickHeight,
                        hp = maxHp,
                        maxHp = maxHp,
                        colorHex = colorHex,
                        isMoving = isMoving,
                        moveRange = moveRange,
                        moveDir = if (c % 2 == 0) 1 else -1
                    )
                )
            }
        }
        return bricks
    }

    private fun generateObstaclesForLevel(level: Int): List<Obstacle> {
        val obstacles = mutableListOf<Obstacle>()
        when (level) {
            3 -> {
                obstacles.add(
                    Obstacle(
                        x = 400f,
                        y = 650f,
                        width = 200f,
                        height = 24f,
                        vx = 4f,
                        minX = 50f,
                        maxX = 750f
                    )
                )
            }
            4 -> {
                obstacles.add(
                    Obstacle(
                        x = 250f,
                        y = 620f,
                        width = 160f,
                        height = 24f,
                        vx = -5f,
                        minX = 50f,
                        maxX = 750f
                    )
                )
                obstacles.add(
                    Obstacle(
                        x = 550f,
                        y = 700f,
                        width = 160f,
                        height = 24f,
                        vx = 5f,
                        minX = 50f,
                        maxX = 750f
                    )
                )
            }
            5 -> {
                obstacles.add(
                    Obstacle(
                        x = 400f,
                        y = 680f,
                        width = 240f,
                        height = 24f,
                        vx = 6f,
                        minX = 50f,
                        maxX = 750f
                    )
                )
            }
        }
        return obstacles
    }

    override fun onCleared() {
        super.onCleared()
        gameJob?.cancel()
    }
}
