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
import androidx.compose.ui.text.style.TextOverflow
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

private data class InventoryItem(
    val type: PowerupType,
    val title: String,
    val icon: DrawScope.(Color) -> Unit
)

private data class CoinPackItem(
    val id: String,
    val title: String,
    val amount: Int,
    val price: String,
    val badge: String? = null,
    val isAd: Boolean = false
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

    var profile by remember {
        val p = profileStorage.getProfile()
        mutableStateOf(p.copy(coins = p.coins, powerupInventory = p.powerupInventory.toMutableMap()))
    }
    var currentTab by remember { mutableStateOf(initialTab) }
    val bebasFont = FontFamily(Font(Res.font.bebas_neue_regular))

    var toastMessage by remember { mutableStateOf<String?>(null) }
    var toastIsSuccess by remember { mutableStateOf(true) }

    fun refreshProfile() {
        val p = profileStorage.getProfile()
        profile = p.copy(
            coins = p.coins,
            powerupInventory = p.powerupInventory.toMutableMap()
        )
    }

    LaunchedEffect(Unit) {
        refreshProfile()
    }

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
            PowerupItem(PowerupType.NOISE_SUPPRESSION, "NOISE SUPPRESSION", "Silent movement for entire mission.", PowerupType.NOISE_SUPPRESSION.defaultCost) { c -> drawBootIcon(c) },
            PowerupItem(PowerupType.SMOKE_SCREEN, "SMOKE GRENADE", "Portable smoke canister for rapid evasion.", 150) { c -> drawSmokeIcon(c) },
            PowerupItem(PowerupType.PHANTOM_CLOAK, "SLEEP DART", "Tranquilizer dart to pacify patrol guards.", 250) { c -> drawCloakIcon(c) }
        )
    }

    val inventoryItems = remember {
        listOf(
            InventoryItem(PowerupType.SMOKE_SCREEN, "SMOKE SCREEN") { c -> drawSmokeIcon(c) },
            InventoryItem(PowerupType.PHANTOM_CLOAK, "PHANTOM CLOAK") { c -> drawCloakIcon(c) },
            InventoryItem(PowerupType.INVISIBILITY, "INVISIBILITY") { c -> drawInvisIcon(c) },
            InventoryItem(PowerupType.NOISE_SUPPRESSION, "NOISE SUPPRESSION") { c -> drawBootIcon(c) }
        )
    }

    val coinPacks = remember {
        listOf(
            CoinPackItem(
                id = "coins_ad",
                title = "SPONSORED INTEL",
                amount = 500,
                price = "WATCH AD",
                badge = "FREE REWARD",
                isAd = true
            ),
            CoinPackItem(
                id = "coins_tier_1",
                title = "OPERATIVE STASH",
                amount = 1000,
                price = "$0.99"
            ),
            CoinPackItem(
                id = "coins_tier_2",
                title = "SMUGGLER'S POUCH",
                amount = 2500,
                price = "$1.99",
                badge = "+25% BONUS"
            ),
            CoinPackItem(
                id = "coins_tier_3",
                title = "TACTICAL BRIEFCASE",
                amount = 4000,
                price = "$2.99",
                badge = "+33% BONUS"
            ),
            CoinPackItem(
                id = "coins_tier_4",
                title = "HEIST DUFFLE BAG",
                amount = 7500,
                price = "$4.99",
                badge = "MOST POPULAR"
            ),
            CoinPackItem(
                id = "coins_tier_5",
                title = "BLACK MARKET VAULT",
                amount = 20000,
                price = "$9.99",
                badge = "BEST VALUE"
            )
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // Inventory Summary
                    Text(
                        text = "INVENTORY",
                        color = Color(0xFF6E6E72),
                        fontSize = (13 * scale).sp,
                        fontFamily = bebasFont,
                        letterSpacing = 1.sp
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy((8 * scale).dp)
                    ) {
                        for (item in inventoryItems) {
                            val count = profile.getPowerupCount(item.type)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Canvas(modifier = Modifier.size(15.dp)) {
                                        item.icon(this, Color(0xFFB7B7BC))
                                    }
                                    Text(
                                        text = item.title,
                                        color = Color(0xFFC9C9CC),
                                        fontSize = (12 * scale).sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = "x$count",
                                    color = if (count > 0) Color(0xFFFFD54F) else Color(0xFF6E6E72),
                                    fontSize = (13 * scale).sp,
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
                                        refreshProfile()
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
                                    refreshProfile()
                                    if (pack.isAd) {
                                        showToast("+${pack.amount} INTEL CREDITS GRANTED", true)
                                    } else {
                                        showToast("+${pack.amount} CREDITS TRANSFERRED", true)
                                    }
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
        verticalArrangement = Arrangement.spacedBy((10 * scale).dp)
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

        // 2x3 Grid of Powerup Cards
        for (chunk in items.chunked(3)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)
            ) {
                for (item in chunk) {
                    PowerupCard(
                        item = item,
                        canAfford = profile.coins >= item.cost,
                        font = font,
                        scale = scale,
                        onBuy = { onBuy(item) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                for (empty in 0 until (3 - chunk.size)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
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
            .background(Color(0xFF141416), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding((10 * scale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)
            ) {
                Box(
                    modifier = Modifier
                        .size((34 * scale).dp)
                        .background(Color(0xFF1B1B1F), RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size((18 * scale).dp)) {
                        item.icon(this, Color(0xFF00E5FF))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = (13 * scale).sp,
                        fontFamily = font,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.description,
                        color = Color(0xFFB7B7BC),
                        fontSize = (10 * scale).sp,
                        lineHeight = (12 * scale).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Canvas(modifier = Modifier.size(13.dp)) {
                        drawCoinIcon(Color(0xFFFFD54F))
                    }
                    Text(
                        text = "${item.cost}",
                        color = Color(0xFFFFD54F),
                        fontSize = (14 * scale).sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val interactionSource = remember { MutableInteractionSource() }
                val click = LocalUiClick.current
                Box(
                    modifier = Modifier
                        .background(
                            if (canAfford) Color(0xFFECE7DA) else Color(0xFF242428),
                            RoundedCornerShape(5.dp)
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { click(); onBuy() }
                        )
                        .padding(horizontal = (12 * scale).dp, vertical = (5 * scale).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "BUY",
                        color = if (canAfford) ShadowTheme.Ink else Color(0xFF6E6E72),
                        fontSize = (12 * scale).sp,
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
        verticalArrangement = Arrangement.spacedBy((10 * scale).dp)
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

        // 2x3 Grid of Coin Packs
        for (chunk in items.chunked(3)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)
            ) {
                for (pack in chunk) {
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
                for (empty in 0 until (3 - chunk.size)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
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
    val isHighlight = pack.badge != null
    val accentColor = when {
        pack.isAd -> Color(0xFF00E5FF)
        pack.badge == "BEST VALUE" -> Color(0xFFFFD54F)
        pack.badge == "MOST POPULAR" -> Color(0xFFFF9800)
        isHighlight -> Color(0xFF00E676)
        else -> Color.White.copy(alpha = 0.08f)
    }

    Box(
        modifier = modifier
            .background(Color(0xFF141416), RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (isHighlight) accentColor.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            )
            .padding((10 * scale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (pack.badge != null) {
                Box(
                    modifier = Modifier
                        .background(accentColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = pack.badge,
                        color = Color(0xFF0A0A0C),
                        fontSize = (9 * scale).sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                Spacer(modifier = Modifier.height((12 * scale).dp))
            }

            // Graphic + Title + Amount
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(modifier = Modifier.size((26 * scale).dp)) {
                    if (pack.isAd) {
                        drawBoltIcon(Color(0xFF00E5FF))
                    } else {
                        drawCoinStackIcon(Color(0xFFFFD54F))
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pack.title,
                    color = Color(0xFFB7B7BC),
                    fontSize = (10 * scale).sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
                Text(
                    text = "${pack.amount} CREDITS",
                    color = Color.White,
                    fontSize = (15 * scale).sp,
                    fontFamily = font,
                    letterSpacing = 1.sp
                )
            }

            // Price / Action Button
            val interactionSource = remember { MutableInteractionSource() }
            val click = LocalUiClick.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        when {
                            pack.isAd -> Color(0xFF00E5FF)
                            pack.badge == "BEST VALUE" -> Color(0xFFFFD54F)
                            else -> Color(0xFFECE7DA)
                        },
                        RoundedCornerShape(5.dp)
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { click(); onPurchase() }
                    )
                    .padding(vertical = (6 * scale).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pack.price,
                    color = Color(0xFF0A0A0C),
                    fontSize = (13 * scale).sp,
                    fontFamily = font,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
