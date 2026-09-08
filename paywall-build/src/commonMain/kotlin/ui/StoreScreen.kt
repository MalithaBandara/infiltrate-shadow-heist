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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infiltrate.ads.CoinsAdLimiter
import com.infiltrate.ads.CoinsRewardAdHost
import com.infiltrate.billing.StoreBilling
import com.infiltrate.storage.PlatformStorage
import game.model.GameProfile
import game.model.GameProfileStorage
import game.model.MapBackedGameProfileStorage
import game.model.PowerupType
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import paywall_build.generated.resources.Res
import paywall_build.generated.resources.bebas_neue_regular
import paywall_build.generated.resources.button1
import paywall_build.generated.resources.button2
import paywall_build.generated.resources.button3
import paywall_build.generated.resources.noads
import paywall_build.generated.resources.store_ad
import paywall_build.generated.resources.store_briefcase
import paywall_build.generated.resources.store_duffle
import paywall_build.generated.resources.store_pouch
import paywall_build.generated.resources.store_stash
import paywall_build.generated.resources.store_vault

enum class StoreTab {
    POWER_UPS,
    COINS,
    REMOVE_ADS
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
    val imageRes: DrawableResource,
    val imageSizeDp: Int,
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
    val coinsAdLimiter = remember {
        CoinsAdLimiter(
            getRaw = { PlatformStorage.getRaw(it) },
            setRaw = { k, v -> PlatformStorage.setRaw(k, v) }
        )
    }
    var coinsAdWatchesRemaining by remember { mutableStateOf(coinsAdLimiter.watchesRemainingToday()) }
    var showCoinsRewardAd by remember { mutableStateOf(false) }
    var pendingCoinsAdAmount by remember { mutableStateOf(0) }

    var profile by remember {
        val p = profileStorage.getProfile()
        mutableStateOf(p.copy(coins = p.coins, powerupInventory = p.powerupInventory.toMutableMap()))
    }
    var currentTab by remember { mutableStateOf(initialTab) }
    val bebasFont = FontFamily(Font(Res.font.bebas_neue_regular))

    var toastMessage by remember { mutableStateOf<String?>(null) }
    var toastIsSuccess by remember { mutableStateOf(true) }
    var isPurchasing by remember { mutableStateOf(false) }

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

    val toastSuccessSound = LocalToastSuccess.current
    val toastErrorSound = LocalToastError.current

    fun showToast(msg: String, isSuccess: Boolean) {
        toastMessage = msg
        toastIsSuccess = isSuccess
        if (isSuccess) toastSuccessSound() else toastErrorSound()
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
                title = "LOOSE COINS",
                amount = 250,
                price = "WATCH AD",
                imageRes = Res.drawable.store_ad,
                imageSizeDp = 48,
                isAd = true
            ),
            CoinPackItem(
                id = "coins_tier_1",
                title = "SMUGGLER'S POUCH",
                amount = 1000,
                price = "$0.99",
                imageRes = Res.drawable.store_pouch,
                imageSizeDp = 56
            ),
            CoinPackItem(
                id = "coins_tier_2",
                title = "TACTICAL BRIEFCASE",
                amount = 2500,
                price = "$1.99",
                imageRes = Res.drawable.store_briefcase,
                imageSizeDp = 64
            ),
            CoinPackItem(
                id = "coins_tier_3",
                title = "OPERATIVE STASH",
                amount = 4000,
                price = "$2.99",
                imageRes = Res.drawable.store_stash,
                imageSizeDp = 72
            ),
            CoinPackItem(
                id = "coins_tier_4",
                title = "HEIST DUFFLE BAG",
                amount = 7500,
                price = "$4.99",
                imageRes = Res.drawable.store_duffle,
                imageSizeDp = 80
            ),
            CoinPackItem(
                id = "coins_tier_5",
                title = "BLACK MARKET VAULT",
                amount = 20000,
                price = "$9.99",
                imageRes = Res.drawable.store_vault,
                imageSizeDp = 90
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
                // --- Sidebar --- (scrollable: the inventory list has no fixed length)
                Column(
                    modifier = Modifier
                        .width((220 * scale).dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
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

                    TexturedSidebarTab(
                        text = "REMOVE ADS",
                        isSelected = currentTab == StoreTab.REMOVE_ADS,
                        texture = Res.drawable.button3,
                        font = bebasFont,
                        iconRenderer = { color ->
                            drawNoAdsIcon(if (profile.isPremium && currentTab != StoreTab.REMOVE_ADS) Color(0xFF00E5FF) else color)
                        },
                        onClick = { currentTab = StoreTab.REMOVE_ADS },
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
                                        showToast("INSUFFICIENT COINS", false)
                                    }
                                }
                            )
                        }
                        StoreTab.COINS -> {
                            CoinsGrid(
                                items = coinPacks,
                                font = bebasFont,
                                scale = scale,
                                isPurchasing = isPurchasing,
                                coinsAdWatchesRemaining = coinsAdWatchesRemaining,
                                coinsAdLimiter = coinsAdLimiter,
                                onPurchase = { pack ->
                                    if (pack.isAd) {
                                        if (coinsAdLimiter.canWatch()) {
                                            pendingCoinsAdAmount = pack.amount
                                            showCoinsRewardAd = true
                                        } else {
                                            showToast("DAILY AD LIMIT REACHED - COME BACK TOMORROW", false)
                                        }
                                    } else {
                                        if (isPurchasing) return@CoinsGrid
                                        isPurchasing = true
                                        StoreBilling.purchase(pack.id) { success, errorMsg ->
                                            isPurchasing = false
                                            if (success) {
                                                profileStorage.addCoins(pack.amount)
                                                refreshProfile()
                                                showToast("+${pack.amount} COINS TRANSFERRED", true)
                                            } else {
                                                showToast(errorMsg ?: "PURCHASE CANCELLED", false)
                                            }
                                        }
                                    }
                                },
                                onRestore = {
                                    if (isPurchasing) return@CoinsGrid
                                    isPurchasing = true
                                    StoreBilling.restorePurchases { success, errorMsg ->
                                        isPurchasing = false
                                        if (success) {
                                            refreshProfile()
                                            showToast("PURCHASES RESTORED", true)
                                        } else {
                                            showToast(errorMsg ?: "RESTORE FAILED", false)
                                        }
                                    }
                                }
                            )
                        }
                        StoreTab.REMOVE_ADS -> {
                            RemoveAdsSection(
                                isPremium = profile.isPremium,
                                font = bebasFont,
                                scale = scale,
                                isPurchasing = isPurchasing,
                                onPurchase = {
                                    if (isPurchasing) return@RemoveAdsSection
                                    isPurchasing = true
                                    StoreBilling.purchase("remove_ads") { success, errorMsg ->
                                        isPurchasing = false
                                        if (success) {
                                            profileStorage.activatePremium()
                                            refreshProfile()
                                            showToast("ADS REMOVED PERMANENTLY", true)
                                        } else {
                                            showToast(errorMsg ?: "PURCHASE CANCELLED", false)
                                        }
                                    }
                                },
                                onRestore = {
                                    if (isPurchasing) return@RemoveAdsSection
                                    isPurchasing = true
                                    StoreBilling.restorePurchases { success, errorMsg ->
                                        isPurchasing = false
                                        if (success) {
                                            profileStorage.activatePremium()
                                            refreshProfile()
                                            showToast("PURCHASES RESTORED", true)
                                        } else {
                                            showToast(errorMsg ?: "RESTORE FAILED", false)
                                        }
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
                        if (toastIsSuccess) Color.White else Color(0xFFFF5252).copy(alpha = 0.95f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = toastMessage ?: "",
                    color = if (toastIsSuccess) Color(0xFF0A0A0C) else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }

        // "Watch ad for coins" - composed only while requested, same load-then-show shape as
        // ContinueAdContent()'s gameplay-continue flow, but with no cross-framework bridge needed:
        // the button tap and this composable both live in the same Compose tree already, so the
        // reward can be granted directly instead of polling a shared trigger object from Swift.
        if (showCoinsRewardAd) {
            CoinsRewardAdHost(
                onRewardEarned = {
                    profileStorage.addCoins(pendingCoinsAdAmount)
                    coinsAdLimiter.recordWatch()
                    coinsAdWatchesRemaining = coinsAdLimiter.watchesRemainingToday()
                    refreshProfile()
                    showToast("+$pendingCoinsAdAmount COINS GRANTED", true)
                },
                onDismissed = { showCoinsRewardAd = false },
                onFailure = {
                    showCoinsRewardAd = false
                    showToast("AD NOT AVAILABLE - TRY AGAIN LATER", false)
                },
            )
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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

        // 2x3 Grid of Powerup Cards. Row height comes from the cards' own content
        // (IntrinsicSize.Min), not weight(1f) - a weight has no bounded height to distribute
        // inside this now-scrollable column.
        for (chunk in items.chunked(3)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
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
    isPurchasing: Boolean = false,
    coinsAdWatchesRemaining: Int = CoinsAdLimiter.MAX_WATCHES_PER_DAY,
    coinsAdLimiter: CoinsAdLimiter? = null,
    onPurchase: (CoinPackItem) -> Unit,
    onRestore: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy((10 * scale).dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "COIN PACKS",
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
            Spacer(modifier = Modifier.width(12.dp))
            val restoreInteraction = remember { MutableInteractionSource() }
            val click = LocalUiClick.current
            Text(
                text = if (isPurchasing) "CONNECTING..." else "RESTORE",
                color = if (isPurchasing) Color(0xFF6E6E72) else Color(0xFFB7B7BC),
                fontSize = (11 * scale).sp,
                fontFamily = font,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clickable(
                        interactionSource = restoreInteraction,
                        indication = null,
                        enabled = !isPurchasing,
                        onClick = { click(); onRestore() }
                    )
                    .padding(horizontal = (6 * scale).dp, vertical = (4 * scale).dp)
            )
        }

        // 2x3 Grid of Coin Packs. Row height comes from the cards' own content
        // (IntrinsicSize.Min), not weight(1f) - a weight has no bounded height to distribute
        // inside this now-scrollable column.
        for (chunk in items.chunked(3)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)
            ) {
                for (pack in chunk) {
                    CoinPackCard(
                        pack = pack,
                        font = font,
                        scale = scale,
                        isPurchasing = isPurchasing,
                        adWatchesRemaining = coinsAdWatchesRemaining,
                        coinsAdLimiter = coinsAdLimiter,
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
    isPurchasing: Boolean = false,
    adWatchesRemaining: Int = CoinsAdLimiter.MAX_WATCHES_PER_DAY,
    coinsAdLimiter: CoinsAdLimiter? = null,
    onPurchase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val adLimitReached = pack.isAd && adWatchesRemaining <= 0

    // Ticks once a second only while the limit is actually reached, so the button reads
    // "AVAILABLE IN 04:12:07" instead of a dead "come back tomorrow" with no indication of when.
    var secondsUntilAvailable by remember(adLimitReached) {
        mutableStateOf(if (adLimitReached) coinsAdLimiter?.secondsUntilReset() ?: 0L else 0L)
    }
    LaunchedEffect(adLimitReached) {
        if (adLimitReached && coinsAdLimiter != null) {
            while (true) {
                val remaining = coinsAdLimiter.secondsUntilReset()
                secondsUntilAvailable = remaining
                if (remaining <= 0L) break
                delay(1000)
            }
        }
    }
    Box(
        modifier = modifier
            .background(Color(0xFF141416), RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (pack.isAd && !adLimitReached) Color(0xFF00E5FF).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            )
            .padding((10 * scale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height((2 * scale).dp))

            // Graphic Image + Title + Amount
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.height((90 * scale).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(pack.imageRes),
                        contentDescription = pack.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size((pack.imageSizeDp * scale).dp)
                    )
                }
                Spacer(modifier = Modifier.height((3 * scale).dp))
                Text(
                    text = pack.title,
                    color = Color(0xFFB7B7BC),
                    fontSize = (10 * scale).sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height((2 * scale).dp))
                Text(
                    text = "${pack.amount} COINS",
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
                        if (adLimitReached) Color(0xFF242428)
                        else if (isPurchasing && !pack.isAd) Color(0xFF242428)
                        else if (pack.isAd) Color(0xFF00E5FF)
                        else Color(0xFFECE7DA),
                        RoundedCornerShape(5.dp)
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !isPurchasing && !adLimitReached,
                        onClick = { click(); onPurchase() }
                    )
                    .padding(vertical = (6 * scale).dp),
                contentAlignment = Alignment.Center
            ) {
                if (adLimitReached) {
                    Text(
                        text = "AVAILABLE IN ${formatCountdown(secondsUntilAvailable)}",
                        color = Color(0xFF6E6E72),
                        fontSize = (11 * scale).sp,
                        fontFamily = font,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (isPurchasing && !pack.isAd) {
                    Text(
                        text = "WAIT...",
                        color = Color(0xFF6E6E72),
                        fontSize = (13 * scale).sp,
                        fontFamily = font,
                        letterSpacing = 1.sp
                    )
                } else if (pack.isAd) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size((12.5 * scale).dp, (10.5 * scale).dp)
                                .offset(y = (-1 * scale).dp)
                        ) {
                            drawAdClapperIcon(Color(0xFF0A0A0C), Color(0xFF00E5FF))
                        }
                        Spacer(modifier = Modifier.width((5 * scale).dp))
                        Text(
                            text = pack.price,
                            color = Color(0xFF0A0A0C),
                            fontSize = (13 * scale).sp,
                            fontFamily = font,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
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
}

// Manual padStart formatting, not "%02d".format(...) - the latter has no Kotlin/Native
// implementation and would break iOS compilation (see .junie/guidelines.md's LevelSelectScene
// note on the exact same trap).
private fun formatCountdown(totalSeconds: Long): String {
    val clamped = totalSeconds.coerceAtLeast(0L)
    val hours = clamped / 3600
    val minutes = (clamped % 3600) / 60
    val seconds = clamped % 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

private fun DrawScope.drawAdClapperIcon(c: Color, accent: Color) {
    val w = size.width
    val h = size.height

    // Top angled clapper stick
    val stickPath = Path().apply {
        moveTo(0f, h * 0.16f)
        lineTo(w, 0f)
        lineTo(w, h * 0.28f)
        lineTo(0f, h * 0.44f)
        close()
    }
    drawPath(stickPath, color = c)

    // Clapper stripes
    val stripe1 = Path().apply {
        moveTo(w * 0.20f, h * 0.13f)
        lineTo(w * 0.36f, h * 0.10f)
        lineTo(w * 0.28f, h * 0.40f)
        lineTo(w * 0.12f, h * 0.43f)
        close()
    }
    drawPath(stripe1, color = accent)

    val stripe2 = Path().apply {
        moveTo(w * 0.54f, h * 0.08f)
        lineTo(w * 0.70f, h * 0.05f)
        lineTo(w * 0.62f, h * 0.35f)
        lineTo(w * 0.46f, h * 0.38f)
        close()
    }
    drawPath(stripe2, color = accent)

    // Clapperboard body
    val bodyTop = h * 0.40f
    drawRoundRect(
        color = c,
        topLeft = Offset(0f, bodyTop),
        size = Size(w, h - bodyTop),
        cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
    )

    // Play triangle cutout inside clapperboard body
    val bodyH = h - bodyTop
    val triPath = Path().apply {
        moveTo(w * 0.36f, bodyTop + bodyH * 0.22f)
        lineTo(w * 0.70f, bodyTop + bodyH * 0.50f)
        lineTo(w * 0.36f, bodyTop + bodyH * 0.78f)
        close()
    }
    drawPath(triPath, color = accent)
}

@Composable
private fun RemoveAdsSection(
    isPremium: Boolean,
    font: FontFamily,
    scale: Float,
    isPurchasing: Boolean = false,
    onPurchase: () -> Unit,
    onRestore: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy((12 * scale).dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REMOVE ADS",
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
            Spacer(modifier = Modifier.width(12.dp))
            val restoreInteraction = remember { MutableInteractionSource() }
            val click = LocalUiClick.current
            Text(
                text = if (isPurchasing) "CONNECTING..." else "RESTORE",
                color = if (isPurchasing) Color(0xFF6E6E72) else Color(0xFFB7B7BC),
                fontSize = (11 * scale).sp,
                fontFamily = font,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clickable(
                        interactionSource = restoreInteraction,
                        indication = null,
                        enabled = !isPurchasing,
                        onClick = { click(); onRestore() }
                    )
                    .padding(horizontal = (6 * scale).dp, vertical = (4 * scale).dp)
            )
        }

        // Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141416), RoundedCornerShape(8.dp))
                .border(
                    1.dp,
                    if (isPremium) Color(0xFF00E5FF).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(8.dp)
                )
                .padding((18 * scale).dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy((14 * scale).dp)
            ) {
                // Top Row: Icon + Title + Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((14 * scale).dp)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.noads),
                        contentDescription = "No Ads",
                        modifier = Modifier.size((56 * scale).dp),
                        contentScale = ContentScale.Fit
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)
                        ) {
                            Text(
                                text = "LIFETIME PASS",
                                color = if (isPremium) Color(0xFF00E5FF) else Color(0xFFB7B7BC),
                                fontSize = (10 * scale).sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            if (isPremium) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = (6 * scale).dp, vertical = (1 * scale).dp)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        color = Color(0xFF00E5FF),
                                        fontSize = (9 * scale).sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height((2 * scale).dp))
                        Text(
                            text = "PERMANENT AD REMOVAL",
                            color = Color.White,
                            fontSize = (18 * scale).sp,
                            fontFamily = font,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "One-time purchase. Enjoy seamless stealth infiltration forever.",
                            color = Color(0xFFB7B7BC),
                            fontSize = (11 * scale).sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // Perks List
                Column(
                    verticalArrangement = Arrangement.spacedBy((8 * scale).dp)
                ) {
                    val perks = listOf(
                        "ZERO ADS" to "Completely removes all banner and interstitial advertisements.",
                        "2X HEIST BOUNTY" to "Permanently doubles all coin payouts for 1-star, 2-star, and 3-star level clears.",
                        "+2,000 BONUS COINS" to "Immediate injection of 2,000 gold coins into your operative balance."
                    )
                    for ((title, desc) in perks) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)
                        ) {
                            Text(
                                text = "✓",
                                color = if (isPremium) Color(0xFF00E5FF) else Color.White,
                                fontSize = (13 * scale).sp,
                                fontWeight = FontWeight.Black
                            )
                            Column {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = (12 * scale).sp,
                                    fontFamily = font,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = desc,
                                    color = Color(0xFF8A8A90),
                                    fontSize = (10.5 * scale).sp,
                                    lineHeight = (13 * scale).sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height((4 * scale).dp))

                // Action / Purchase Button
                if (isPremium) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF00E5FF), RoundedCornerShape(5.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(5.dp))
                            .padding(vertical = (10 * scale).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ ALL ADS REMOVED — LIFETIME PASS ACTIVE",
                            color = Color(0xFF0A0A0C),
                            fontSize = (13 * scale).sp,
                            fontFamily = font,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    val interactionSource = remember { MutableInteractionSource() }
                    val click = LocalUiClick.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isPurchasing) Color(0xFF242428) else Color(0xFFECE7DA),
                                RoundedCornerShape(5.dp)
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                enabled = !isPurchasing,
                                onClick = { click(); onPurchase() }
                            )
                            .padding(vertical = (10 * scale).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isPurchasing) "CONNECTING..." else "REMOVE ADS — $2.99",
                            color = if (isPurchasing) Color(0xFF6E6E72) else Color(0xFF0A0A0C),
                            fontSize = (14 * scale).sp,
                            fontFamily = font,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawNoAdsIcon(c: Color) {
    val d = size.minDimension / 20f
    val cx = size.width / 2f
    val cy = size.height / 2f

    // Screen rounded rectangle
    drawRoundRect(
        color = c,
        topLeft = Offset(cx - 7.5f * d, cy - 5.5f * d),
        size = Size(15f * d, 11f * d),
        cornerRadius = CornerRadius(2f * d),
        style = Stroke(width = 1.4f * d)
    )

    // Play triangle
    val triPath = Path().apply {
        moveTo(cx - 2f * d, cy - 3f * d)
        lineTo(cx + 3.5f * d, cy)
        lineTo(cx - 2f * d, cy + 3f * d)
        close()
    }
    drawPath(triPath, color = c)

    // Diagonal prohibition slash
    drawLine(
        color = Color(0xFFFF5252),
        start = Offset(cx - 8.5f * d, cy - 6.5f * d),
        end = Offset(cx + 8.5f * d, cy + 6.5f * d),
        strokeWidth = 1.8f * d
    )
}

