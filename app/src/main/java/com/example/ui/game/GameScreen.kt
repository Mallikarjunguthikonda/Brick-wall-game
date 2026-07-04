package com.example.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.HighScoreEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Custom Colors for Sophisticated Dark Theme
val DarkBackground = Color(0xFF1C1B1F)
val CyberDark = Color(0xFF141218)
val CyberCard = Color(0xFF25232A)
val NeonPink = Color(0xFFD0BCFF)      // Accent Lavender
val NeonCyan = Color(0xFF4FD8EB)      // Soft Cyan
val NeonYellow = Color(0xFFE8DEF8)    // Pale Lavender Yellow
val NeonGreen = Color(0xFFB1D18A)     // Soft Olive Green
val NeonBlue = Color(0xFF938F99)      // Sophisticated Grey
val NeonRed = Color(0xFFFFB4AB)       // Soft Coral Red

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when (state.gameState) {
            GameState.MENU -> MenuScreen(
                currentHighScore = state.currentHighScore,
                onPlayClicked = { viewModel.startNewGame() },
                onHighScoresClicked = { viewModel.changeState(GameState.HIGH_SCORES) }
            )
            GameState.PLAYING, GameState.PAUSED, GameState.LEVEL_COMPLETED, GameState.GAME_OVER -> GameplayScreen(
                state = state,
                viewModel = viewModel
            )
            GameState.HIGH_SCORES -> HighScoresScreen(
                scores = state.topScores,
                onBackClicked = { viewModel.changeState(GameState.MENU) },
                onClearClicked = { viewModel.clearHighScores() }
            )
        }
    }
}

@Composable
fun MenuScreen(
    currentHighScore: Int,
    onPlayClicked: () -> Unit,
    onHighScoresClicked: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "title_glow")
    val titleScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "title_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Neon Icon / Decorative banner
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(NeonPink.copy(alpha = 0.4f), Color.Transparent)))
                .border(2.dp, NeonPink, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = "Arcade Icon",
                tint = NeonPink,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title
        Text(
            text = "ELEGANT ARCADE",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            color = NeonPink,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Text(
            text = "Brick Breaker",
            fontSize = 44.sp,
            fontWeight = FontWeight.Light,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            fontFamily = FontFamily.Serif,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(bottom = 32.dp)
                .testTag("game_title")
        )

        // High Score display
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(RoundedCornerShape(12.dp))
                .background(CyberCard)
                .border(1.dp, NeonYellow.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = "Trophy",
                    tint = NeonYellow,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BEST SCORE: $currentHighScore",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = NeonYellow
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Play Button
        Button(
            onClick = onPlayClicked,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(56.dp)
                .border(2.dp, NeonGreen, RoundedCornerShape(28.dp))
                .testTag("play_game_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonGreen.copy(alpha = 0.15f),
                contentColor = NeonGreen
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NEW GAME",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // High scores button
        OutlinedButton(
            onClick = onHighScoresClicked,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(56.dp)
                .border(1.5.dp, NeonCyan, RoundedCornerShape(28.dp))
                .testTag("view_highscores_button"),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = NeonCyan
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Leaderboard, contentDescription = "Leaderboard")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LEADERBOARD",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

@Composable
fun GameplayScreen(
    state: GameUiState,
    viewModel: GameViewModel
) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Level Display
                Column {
                    Text(
                        text = "LEVEL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                        color = NeonPink,
                        fontFamily = FontFamily.SansSerif
                    )
                    val levelWord = when (state.level) {
                        1 -> "One"
                        2 -> "Two"
                        3 -> "Three"
                        4 -> "Four"
                        5 -> "Five"
                        else -> "Loop ${state.level}"
                    }
                    Text(
                        text = levelWord,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontFamily = FontFamily.Serif,
                        color = Color.White
                    )
                }

                // Middle: Pause Button
                IconButton(
                    onClick = { viewModel.togglePause() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF332D41), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF49454F), RoundedCornerShape(12.dp))
                        .testTag("pause_button")
                ) {
                    Icon(
                        imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Pause",
                        tint = NeonPink,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Right: Score Display
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "SCORE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        color = Color(0xFFCAC4D0),
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = state.score.toString().padStart(6, '0'),
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF25232A), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Row for Lives and High Score
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Lives dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 1..3) {
                            val dotColor = if (i <= state.lives) NeonRed else Color(0xFF49454F)
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                    }

                    // Right: High Score
                    Text(
                        text = "HIGH SCORE: ${state.currentHighScore}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = Color(0xFFCAC4D0),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Touch/Slide Pad
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkBackground)
                        .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    viewModel.movePaddleRelative(dragAmount.x)
                                }
                            )
                        }
                        .clickable {
                            // Tap to launch ball if stuck
                            if (state.balls.any { it.isStuck }) {
                                viewModel.launchBall()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Left Arrow Accent
                    Text(
                        text = "◀",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                    )

                    // Middle Instruction text
                    Text(
                        text = if (state.balls.any { it.isStuck }) "TAP PAD TO LAUNCH BALL" else "SLIDE TO MOVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = if (state.balls.any { it.isStuck }) NeonPink else Color(0xFF938F99),
                        textAlign = TextAlign.Center
                    )

                    // Right Arrow Accent
                    Text(
                        text = "▶",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom home indicator line
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF49454F))
                )
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            viewModel.movePaddleRelative(dragAmount.x)
                        }
                    )
                }
                .clickable {
                    // Tap to launch ball if stuck
                    if (state.balls.any { it.isStuck }) {
                        viewModel.launchBall()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Active Power-ups Bar at the top of the board
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Active powerup badge indicators
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    state.activePowerUps.forEach { active ->
                        val timeLeft = (active.expirationTime - System.currentTimeMillis()).coerceAtLeast(0L) / 1000f
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(active.type.color).copy(alpha = 0.2f))
                                .border(1.dp, Color(active.type.color), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${active.type.iconString} ${active.type.displayName} (${String.format("%.1fs", timeLeft)})",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                    }
                    if (state.shieldActive) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonCyan.copy(alpha = 0.2f))
                                .border(1.dp, NeonCyan, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🛡️ Safety Floor ACTIVE",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                    }
                }

                // Centered 2:3 Aspect Ratio Arcade Board Frame
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(CyberDark)
                        .border(1.dp, Color(0xFF49454F), RoundedCornerShape(32.dp))
                ) {
                    // Physics Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val scaleX = size.width / 800f
                        val scaleY = size.height / 1200f

                        drawContext.canvas.save()
                        drawContext.canvas.scale(scaleX, scaleY)

                        // 1. Draw Safety Shield if active
                        if (state.shieldActive) {
                            drawLine(
                                color = NeonCyan,
                                start = Offset(0f, 1175f),
                                end = Offset(800f, 1175f),
                                strokeWidth = 5f
                            )
                            // Draw light glow effect
                            for (i in 1..4) {
                                drawLine(
                                    color = NeonCyan.copy(alpha = 0.15f / i),
                                    start = Offset(0f, 1175f),
                                    end = Offset(800f, 1175f),
                                    strokeWidth = 5f + i * 4f
                                )
                            }
                        }

                        // 2. Draw Bricks
                        state.bricks.forEach { brick ->
                            if (brick.hp > 0) {
                                val brickColor = Color(brick.colorHex)
                                val hpPercent = brick.hp.toFloat() / brick.maxHp.toFloat()
                                val fillAlpha = 0.25f + hpPercent * 0.55f

                                // Draw brick filled rectangle
                                drawRoundRect(
                                    color = brickColor.copy(alpha = fillAlpha),
                                    topLeft = Offset(brick.x, brick.y),
                                    size = Size(brick.width, brick.height),
                                    cornerRadius = CornerRadius(4f, 4f)
                                )

                                // Draw glowing border
                                drawRoundRect(
                                    color = brickColor,
                                    topLeft = Offset(brick.x, brick.y),
                                    size = Size(brick.width, brick.height),
                                    cornerRadius = CornerRadius(4f, 4f),
                                    style = Stroke(width = 1.5f)
                                )

                                // Highlight top shine
                                drawLine(
                                    color = Color.White.copy(alpha = 0.6f),
                                    start = Offset(brick.x + 3f, brick.y + 3f),
                                    end = Offset(brick.x + brick.width - 3f, brick.y + 3f),
                                    strokeWidth = 1.5f
                                )

                                // If HP > 1, draw strength indicators (dots)
                                if (brick.maxHp > 1) {
                                    for (dot in 0 until brick.hp) {
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.8f),
                                            radius = 2.5f,
                                            center = Offset(brick.x + 10f + dot * 10f, brick.y + brick.height / 2f)
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Draw Obstacles (moving bars on level 3+)
                        state.obstacles.forEach { obs ->
                            val left = obs.x - obs.width / 2f
                            val top = obs.y - obs.height / 2f

                            // Draw main bar body
                            drawRoundRect(
                                color = Color(0xFF607D8B),
                                topLeft = Offset(left, top),
                                size = Size(obs.width, obs.height),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                            // Bright border
                            drawRoundRect(
                                color = Color(0xFFCFD8DC),
                                topLeft = Offset(left, top),
                                size = Size(obs.width, obs.height),
                                cornerRadius = CornerRadius(6f, 6f),
                                style = Stroke(width = 2f)
                            )

                            // Diagonal hazard warning stripes
                            for (s in 0 until (obs.width / 20f).toInt()) {
                                val startX = left + s * 20f
                                drawLine(
                                    color = Color(0xFF37474F).copy(alpha = 0.8f),
                                    start = Offset(startX, top),
                                    end = Offset(startX + 10f, top + obs.height),
                                    strokeWidth = 4f
                                )
                            }
                        }

                        // 4. Draw Falling Power-ups
                        state.powerUps.forEach { powerUp ->
                            val glowColor = Color(powerUp.type.color)

                            // Halo glow
                            drawCircle(
                                color = glowColor.copy(alpha = 0.3f),
                                radius = powerUp.radius + 6f,
                                center = Offset(powerUp.x, powerUp.y)
                            )

                            drawCircle(
                                color = CyberDark,
                                radius = powerUp.radius,
                                center = Offset(powerUp.x, powerUp.y)
                            )

                            drawCircle(
                                color = glowColor,
                                radius = powerUp.radius,
                                center = Offset(powerUp.x, powerUp.y),
                                style = Stroke(width = 2f)
                            )

                            // Draw Emoji on canvas
                            drawIntoCanvas { canvas ->
                                val paint = android.graphics.Paint().apply {
                                    textSize = 18f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                                canvas.nativeCanvas.drawText(
                                    powerUp.type.iconString,
                                    powerUp.x,
                                    powerUp.y + 6f,
                                    paint
                                )
                            }
                        }

                        // 5. Draw Laser Beams
                        state.laserBeams.forEach { laser ->
                            drawRoundRect(
                                color = NeonRed,
                                topLeft = Offset(laser.x - laser.width / 2f, laser.y),
                                size = Size(laser.width, laser.height),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                            // White inner core
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(laser.x - 1f, laser.y + 2f),
                                size = Size(2f, laser.height - 4f),
                                cornerRadius = CornerRadius(1f, 1f)
                            )
                        }

                        // 6. Draw Paddle
                        val pLeft = state.paddle.x - state.paddle.width / 2f
                        val pTop = state.paddle.y

                        // Pulsing glow for paddle if laser blaster is active
                        val isLaserActive = state.activePowerUps.any { it.type == PowerUpType.LASER_PADDLE }
                        val paddleBaseColor = if (isLaserActive) NeonRed else NeonPink

                        // Paddle outline glow
                        drawRoundRect(
                            color = paddleBaseColor.copy(alpha = 0.3f),
                            topLeft = Offset(pLeft - 4f, pTop - 4f),
                            size = Size(state.paddle.width + 8f, state.paddle.height + 8f),
                            cornerRadius = CornerRadius(12f, 12f)
                        )

                        // Main paddle capsule
                        drawRoundRect(
                            color = CyberCard,
                            topLeft = Offset(pLeft, pTop),
                            size = Size(state.paddle.width, state.paddle.height),
                            cornerRadius = CornerRadius(10f, 10f)
                        )

                        // Colorful neon stroke
                        drawRoundRect(
                            color = paddleBaseColor,
                            topLeft = Offset(pLeft, pTop),
                            size = Size(state.paddle.width, state.paddle.height),
                            cornerRadius = CornerRadius(10f, 10f),
                            style = Stroke(width = 2.5f)
                        )

                        // Shiny inner line
                        drawLine(
                            color = Color.White.copy(alpha = 0.7f),
                            start = Offset(pLeft + 12f, pTop + 6f),
                            end = Offset(pLeft + state.paddle.width - 12f, pTop + 6f),
                            strokeWidth = 2f
                        )

                        // 7. Draw Balls
                        state.balls.forEach { ball ->
                            // Outer Neon Glow
                            drawCircle(
                                color = NeonYellow.copy(alpha = 0.35f),
                                radius = ball.radius + 4f,
                                center = Offset(ball.x, ball.y)
                            )
                            // Main Ball Body
                            drawCircle(
                                color = NeonYellow,
                                radius = ball.radius,
                                center = Offset(ball.x, ball.y)
                            )
                            // Inner core highlight
                            drawCircle(
                                color = Color.White,
                                radius = ball.radius / 2.2f,
                                center = Offset(ball.x - 2f, ball.y - 2f)
                            )
                        }

                        // 8. Draw Particles
                        state.particles.forEach { p ->
                            drawCircle(
                                color = Color(p.color).copy(alpha = p.alpha),
                                radius = p.radius * p.alpha,
                                center = Offset(p.x, p.y)
                            )
                        }

                        drawContext.canvas.restore()
                    }

                    // Game Over Overlay
                    if (state.gameState == GameState.GAME_OVER) {
                        GameOverOverlay(
                            score = state.score,
                            onSaveScore = { name -> viewModel.saveHighScore(name) },
                            onPlayAgain = { viewModel.startNewGame() },
                            onMainMenu = { viewModel.changeState(GameState.MENU) }
                        )
                    }

                    // Level Completed Overlay
                    if (state.gameState == GameState.LEVEL_COMPLETED) {
                        LevelCompletedOverlay(
                            level = state.level,
                            onNextLevel = { viewModel.nextLevel() },
                            onRestart = { viewModel.restartLevel() }
                        )
                    }

                    // Paused Overlay
                    if (state.isPaused) {
                        PausedOverlay(
                            onResume = { viewModel.resumeGame() },
                            onQuit = { viewModel.changeState(GameState.MENU) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GameOverOverlay(
    score: Int,
    onSaveScore: (String) -> Unit,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit
) {
    var playerName by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(CyberCard)
                .border(2.dp, NeonRed, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "GAME OVER",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                color = NeonRed,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "YOU SCORED",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.Gray
            )

            Text(
                text = score.toString(),
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = NeonYellow,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // High score registry form
            Text(
                text = "ENTER YOUR NAME FOR LEADERBOARD",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = NeonCyan,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = playerName,
                onValueChange = { if (it.length <= 12) playerName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_name_input"),
                placeholder = { Text("ANONYMOUS", color = Color.Gray) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = Color.DarkGray,
                    cursorColor = NeonCyan
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Save Score button
            Button(
                onClick = { onSaveScore(playerName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("save_score_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "SAVE SCORE",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color.DarkGray, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Play again and exit buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onPlayAgain,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                        .testTag("restart_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                ) {
                    Text("RETRY", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onMainMenu,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp)
                        .testTag("quit_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                ) {
                    Text("MENU", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LevelCompletedOverlay(
    level: Int,
    onNextLevel: () -> Unit,
    onRestart: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(CyberCard)
                .border(2.dp, NeonGreen, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Stars,
                contentDescription = "Success Star",
                tint = NeonYellow,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "VICTORY!",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                color = NeonGreen
            )

            Text(
                text = "LEVEL $level CLEARED",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = NeonCyan,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Next Level button
            Button(
                onClick = onNextLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(2.dp, NeonGreen, RoundedCornerShape(25.dp))
                    .testTag("next_level_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen.copy(alpha = 0.2f),
                    contentColor = NeonGreen
                ),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = if (level >= 5) "RESTART GRAND LOOP" else "NEXT LEVEL",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Replay level
            TextButton(
                onClick = onRestart,
                modifier = Modifier.testTag("replay_level_button")
            ) {
                Text(
                    text = "REPLAY LEVEL",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun PausedOverlay(
    onResume: () -> Unit,
    onQuit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(RoundedCornerShape(16.dp))
                .background(CyberCard)
                .border(1.5.dp, NeonCyan, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "GAME PAUSED",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = NeonCyan,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Resume button
            Button(
                onClick = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("resume_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("RESUME", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quit Button
            OutlinedButton(
                onClick = onQuit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("quit_button"),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = NeonPink
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("QUIT GAME", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HighScoresScreen(
    scores: List<HighScoreEntity>,
    onBackClicked: () -> Unit,
    onClearClicked: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding()
    ) {
        // Back Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClicked,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .testTag("back_to_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = NeonCyan
                )
            }

            Text(
                text = "LEADERBOARD",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                color = NeonCyan
            )

            // Trash clear button
            IconButton(
                onClick = onClearClicked,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .testTag("clear_scores_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear",
                    tint = NeonRed
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (scores.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "Empty",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "NO HIGH SCORES YET",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )
                    Text(
                        text = "Play a game and save your score!",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = Color.DarkGray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(scores) { index, item ->
                    val placeColor = when (index) {
                        0 -> NeonYellow
                        1 -> Color(0xFFC0C0C0) // Silver
                        2 -> Color(0xFFCD7F32) // Bronze
                        else -> NeonCyan.copy(alpha = 0.7f)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberCard)
                            .border(1.dp, placeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${index + 1}.",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = placeColor,
                                modifier = Modifier.width(32.dp)
                            )

                            Column {
                                Text(
                                    text = item.playerName.uppercase(),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    color = Color.White
                                )
                                Text(
                                    text = "Cleared Level ${item.levelReached} • ${dateFormat.format(Date(item.timestamp))}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.Gray
                                )
                            }
                        }

                        Text(
                            text = item.score.toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = NeonYellow
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onBackClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCard, contentColor = Color.White)
        ) {
            Text("BACK TO MAIN MENU")
        }
    }
}
