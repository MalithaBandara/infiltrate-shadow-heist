package com.infiltrate.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infiltrate.storage.PlatformStorage
import game.model.GameProfile
import game.model.GameProfileStorage
import game.model.MapBackedGameProfileStorage
import game.model.PowerupType
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import paywall_build.generated.resources.Res
import paywall_build.generated.resources.bebas_neue_regular
import paywall_build.generated.resources.bg_menu
import paywall_build.generated.resources.button1
import paywall_build.generated.resources.button2

enum class StoreTab {
    POWER_UPS,
    COINS
}

private data class PowerupItem(
    val type: PowerupType,
    val title: String,
    val description: String,
    val cost: Int,
    val icon: DrawScope.(Color) -> Unit
)

private data class CoinPackItem(
    val id: String,
    val amount: Int,
    val price: String,
    val isBestValue: Boolean = false
)

@Composable
fun StoreScreen(
    initialTab: StoreTab = StoreTab.POWER_UPS,
    onBackClicked: () -> Unit
) {
    val profileStorage: GameProfileStorage = remember {
        MapBackedGameProfileStorage(
            getRaw = { PlatformStorage.getRaw(it) },
            setRaw = { k, v -> PlatformStorage.setRaw(k, v) }
        )
    }

    var profile by remember { mutableStateOf(profileStorage.getProfile()) }
    var currentTab by remember { mutableStateOf(initialTab) }
    val bebasFont = FontFamily(Font(Res.font.bebas_neue_regular))

    var toastMessage by remember { mutableStateOf<String?>(null) }
    var toastIsSuccess by remember { mutableStateOf(true) }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2200)
            toastMessage = null
        }
    }

    fun showToast(msg: String, isSuccess: Boolean) {
        toastMessage = msg
        toastIsSuccess = isSuccess
    }

    val powerupItems = remember {
        listOf(
            PowerupItem(PowerupType.SMOKE_SCREEN, "SMOKE SCREEN", "Disables all cameras for 10 seconds.", PowerupType.SMOKE_SCREEN.defaultCost) { c -> drawSmokeIcon(c) },
            PowerupItem(PowerupType.PHANTOM_CLOAK, "PHANTOM CLOAK", "Puts all guards to sleep for 10 seconds.", PowerupType.PHANTOM_CLOAK.defaultCost) { c -> drawCloakIcon(c) },
            PowerupItem(PowerupType.INVISIBILITY, "INVISIBILITY", "Total sight immunity for 10 seconds.", PowerupType.INVISIBILITY.defaultCost) { c -> drawInvisIcon(c) },
            PowerupItem(PowerupType.NOISE_SUPPRESSION, "NOISE SUPPRESSION", "Silent movement for entire mission.", PowerupType.NOISE_SUPPRESSION.defaultCost) { c -> drawBootIcon(c) }
        )
    }

    val coinPacks = remember {
        listOf(
            CoinPackItem("coins_small", 500, "$0.99"),
            CoinPackItem("coins_medium", 1200, "$1.99", isBestValue = true),
            CoinPackItem("coins_large", 3500, "$4.99")
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0D))
    ) {
        val screenHeight = maxHeight
        val scale = (screenHeight / 720.dp).coerceIn(0.75f, 1.4f)

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            MenuTopBar(
                title = "STORE",
                font = bebasFont,
                onBackClicked = onBackClicked,
                statPills = {
                    CoinPill(
                        coins = profile.coins,
                        onPlusClicked = { currentTab = StoreTab.COINS }
                    )
                }
            )

            // Content: Sidebar + Main Area
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = (24 * scale).dp, vertical = (12 * scale).dp),
                horizontalArrangement = Arrangement.spacedBy((20 * scale).dp)
            ) {
                // --- Sidebar ---
                Column(
                    modifier = Modifier
                        .width((220 * scale).dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TexturedSidebarTab(
                        text = "POWER-UPS",
                        isSelected = currentTab == StoreTab.POWER_UPS,
                        texture = Res.drawable.button1,
                        font = bebasFont,
                        iconRenderer = { color -> drawBoltIcon(color) },
                        onClick = { currentTab = StoreTab.POWER_UPS },
                        tabHeight = (46 * scale).dp
                    )

                    TexturedSidebarTab(
                        text = "COINS",
                        isSelected = currentTab == StoreTab.COINS,
                        texture = Res.drawable.button2,
                        font = bebasFont,
                        iconRenderer = { color -> drawCoinStackIcon(color) },
                        onClick = { currentTab = StoreTab.COINS },
                        tabHeight = (46 * scale).dp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Thumbnail Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((110 * scale).dp)
                            .background(Color(0xFF141416), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.bg_menu),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Inventory Summary
                    Text(
                        text = "INVENTORY",
                        color = Color(0xFF6E6E72),
                        fontSize = (12 * scale).sp,
                        fontFamily = bebasFont,
                        letterSpacing = 1.sp
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (item in powerupItems) {
                            val count = profile.getPowerupCount(item.type)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Canvas(modifier = Modifier.size(14.dp)) {
                                        item.icon(this, Color(0xFFB7B7BC))
                                    }
                                    Text(
                                        text = item.title,
                                        color = Color(0xFFC9C9CC),
                                        fontSize = (11 * scale).sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = "x$count",
                                    color = if (count > 0) Color(0xFFFFD54F) else Color(0xFF6E6E72),
                                    fontSize = (12 * scale).sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // --- Main Content Area ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (currentTab) {
                        StoreTab.POWER_UPS -> {
                            PowerupsGrid(
                                items = powerupItems,
                                profile = profile,
                                font = bebasFont,
                                scale = scale,
                                onBuy = { item ->
                                    if (profileStorage.buyPowerup(item.type.id, item.cost)) {
                                        profile = profileStorage.getProfile()
                                        showToast("ACQUIRED ${item.title}", true)
                                    } else {
                                        showToast("INSUFFICIENT CREDITS", false)
                                    }
                                }
                            )
                        }
                        StoreTab.COINS -> {
                            CoinsGrid(
                                items = coinPacks,
                                font = bebasFont,
                                scale = scale,
                                onPurchase = { pack ->
                                    profileStorage.addCoins(pack.amount)
                                    profile = profileStorage.getProfile()
                                    showToast("+${pack.amount} CREDITS TRANSFERRED", true)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Floating Toast Notification at Root Screen Level
        androidx.compose.animation.AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        if (toastIsSuccess) Color(0xFF00E676).copy(alpha = 0.95f) else Color(0xFFFF5252).copy(alpha = 0.95f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = toastMessage ?: "",
                    color = Color(0xFF0A0A0C),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun PowerupsGrid(
    items: List<PowerupItem>,
    profile: GameProfile,
    font: FontFamily,
    scale: Float,
    onBuy: (PowerupItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy((14 * scale).dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GADGETS & EQUIPMENT",
                color = Color.White,
                fontSize = (18 * scale).sp,
                fontFamily = font,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }

        // 2x2 Grid of Powerup Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy((14 * scale).dp)
        ) {
            for (i in 0..1) {
                PowerupCard(
                    item = items[i],
                    canAfford = profile.coins >= items[i].cost,
                    font = font,
                    scale = scale,
                    onBuy = { onBuy(items[i]) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy((14 * scale).dp)
        ) {
            for (i in 2..3) {
                PowerupCard(
                    item = items[i],
                    canAfford = profile.coins >= items[i].cost,
                    font = font,
                    scale = scale,
                    onBuy = { onBuy(items[i]) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun PowerupCard(
    item: PowerupItem,
    canAfford: Boolean,
    font: FontFamily,
    scale: Float,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF141416), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding((16 * scale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)
            ) {
                Box(
                    modifier = Modifier
                        .size((44 * scale).dp)
                        .background(Color(0xFF1B1B1F), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size((22 * scale).dp)) {
                        item.icon(this, Color(0xFF00E5FF))
                    }
                }

                Column {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = (16 * scale).sp,
                        fontFamily = font,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.description,
                        color = Color(0xFFB7B7BC),
                        fontSize = (12 * scale).sp,
                        lineHeight = (15 * scale).sp
                    )
                }
            }

            // Buy Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        drawCoinIcon(Color(0xFFFFD54F))
                    }
                    Text(
                        text = "${item.cost}",
                        color = Color(0xFFFFD54F),
                        fontSize = (16 * scale).sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .background(
                            if (canAfford) Color(0xFFECE7DA) else Color(0xFF242428),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onBuy
                        )
                        .padding(horizontal = (18 * scale).dp, vertical = (8 * scale).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "BUY",
                        color = if (canAfford) ShadowTheme.Ink else Color(0xFF6E6E72),
                        fontSize = (13 * scale).sp,
                        fontFamily = font,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CoinsGrid(
    items: List<CoinPackItem>,
    font: FontFamily,
    scale: Float,
    onPurchase: (CoinPackItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SHADOW PASS & CREDITS",
                color = Color.White,
                fontSize = (18 * scale).sp,
                fontFamily = font,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }

        // Coin Packs Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            for (pack in items) {
                CoinPackCard(
                    pack = pack,
                    font = font,
                    scale = scale,
                    onPurchase = { onPurchase(pack) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun CoinPackCard(
    pack: CoinPackItem,
    font: FontFamily,
    scale: Float,
    onPurchase: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF141416), RoundedCornerShape(10.dp))
            .border(
                1.dp,
                if (pack.isBestValue) Color(0xFFFFD54F).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp)
            )
            .padding((18 * scale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (pack.isBestValue) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFD54F), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "BEST VALUE",
                        color = Color(0xFF0A0A0C),
                        fontSize = (10 * scale).sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Coin Graphic + Amount
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(modifier = Modifier.size((40 * scale).dp)) {
                    drawCoinStackIcon(Color(0xFFFFD54F))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${pack.amount} CREDITS",
                    color = Color.White,
                    fontSize = (20 * scale).sp,
                    fontFamily = font,
                    letterSpacing = 1.sp
                )
            }

            // Price Button
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFECE7DA), RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onPurchase
                    )
                    .padding(vertical = (10 * scale).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pack.price,
                    color = ShadowTheme.Ink,
                    fontSize = (16 * scale).sp,
                    fontFamily = font,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
