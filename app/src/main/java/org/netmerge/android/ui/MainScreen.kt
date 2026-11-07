package org.netmerge.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.netmerge.android.Engine
import org.netmerge.android.R
import org.netmerge.android.Stats

@Composable
fun MainScreen(
    phase: Engine.Phase,
    mode: Engine.Mode,
    stats: List<Stats>,
    history: Map<String, List<Float>>,
    evenSplit: Boolean,
    onToggle: () -> Unit,
    onModeChange: (Engine.Mode) -> Unit,
    onEvenSplit: (Boolean) -> Unit,
) {
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.backgroundTop, p.backgroundBottom))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(phase, mode, stats.size)
            Spacer(Modifier.height(16.dp))
            PowerButton(phase, onToggle, size = 172.dp)
            Spacer(Modifier.height(16.dp))
            CombinedSpeed(phase, stats)
            Spacer(Modifier.height(18.dp))

            AnimatedVisibility(
                visible = phase == Engine.Phase.ON,
                enter = fadeIn(tween(500)) + expandVertically(tween(500)),
                exit = fadeOut(tween(300)) + shrinkVertically(tween(300)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (mode == Engine.Mode.PROXY) {
                        ProxyGuideCard(phase)
                    }
                    for ((i, s) in stats.withIndex()) {
                        NetworkCard(s, stats, i)
                    }
                    if (stats.isEmpty()) WaitingCard()
                    ChartCard(stats, history)
                }
            }
            AnimatedVisibility(
                visible = phase == Engine.Phase.OFF,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(200)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (mode == Engine.Mode.PROXY) {
                        ProxyGuideCard(phase)
                    }
                    IntroCard()
                }
            }
            Spacer(Modifier.height(14.dp))
            ModeCard(mode, phase, onModeChange)
            Spacer(Modifier.height(14.dp))
            SettingsCard(evenSplit, onEvenSplit)
            Spacer(Modifier.height(18.dp))
            Text(
                "Android port of net-merge by Rohit Mahto · MIT",
                color = p.muted.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Header(phase: Engine.Phase, mode: Engine.Mode, networks: Int) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "net-merge",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        AnimatedContent(
            targetState = Triple(phase, mode, networks),
            transitionSpec = { (fadeIn(tween(300)) + slideInVertically { it / 2 }) togetherWith fadeOut(tween(200)) },
            label = "status",
        ) { (ph, m, n) ->
            val (text, color) = when (ph) {
                Engine.Phase.OFF -> {
                    if (m == Engine.Mode.PROXY) "Proxy Mode · Bật SOCKS5 (127.0.0.1:10808)" to p.muted
                    else "VPN Mode · Chạm để gộp Wi-Fi và 4G" to p.muted
                }
                Engine.Phase.STARTING -> "Connecting…" to p.warn
                Engine.Phase.ON -> when (n) {
                    0 -> "Waiting for networks" to p.warn
                    1 -> (if (m == Engine.Mode.PROXY) "Proxy active (127.0.0.1:10808) · 1 network" else "Using 1 network") to p.warn
                    else -> (if (m == Engine.Mode.PROXY) "Proxy active (127.0.0.1:10808) · Gộp $n mạng" else "Merging $n networks") to p.good
                }
            }
            Text(text, color = color, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun CombinedSpeed(phase: Engine.Phase, stats: List<Stats>) {
    val p = LocalPalette.current
    val down by animateFloatAsState(stats.sumOf { it.rateIn }.toFloat(), tween(800), label = "down")
    val up by animateFloatAsState(stats.sumOf { it.rateOut }.toFloat(), tween(800), label = "up")
    val alpha by animateFloatAsState(if (phase == Engine.Phase.ON) 1f else 0.35f, tween(500), label = "a")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            Stats.rate((down + up).toDouble()),
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
        )
        Text("combined", color = p.muted.copy(alpha = alpha), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            SpeedLabel("↓", down, p.wifi.copy(alpha = alpha))
            SpeedLabel("↑", up, p.cell.copy(alpha = alpha))
        }
    }
}

@Composable
private fun SpeedLabel(arrow: String, value: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(arrow, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(Stats.rate(value.toDouble()), color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.card)
            .padding(18.dp),
    ) { content() }
}

fun colorFor(name: String, p: Palette): Color = if (name.startsWith("Wi-Fi")) p.wifi else p.cell

@Composable
private fun ChartCard(stats: List<Stats>, history: Map<String, List<Float>>) {
    val p = LocalPalette.current
    Card {
        Column {
            Text("Last minute", color = p.muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            LiveChart(
                series = stats.map { colorFor(it.name, p) to (history[it.name] ?: emptyList()) },
                modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

@Composable
private fun NetworkCard(s: Stats, all: List<Stats>, index: Int) {
    val p = LocalPalette.current
    val color = colorFor(s.name, p)
    val total = all.sumOf { it.rateIn + it.rateOut }
    val share by animateFloatAsState(
        if (total > 0) ((s.rateIn + s.rateOut) / total).toFloat() else 0f,
        tween(900, easing = FastOutSlowInEasing), label = "share",
    )
    val statusColor by animateColorAsState(
        when (s.status) {
            "up" -> p.good
            "checking" -> p.warn
            else -> p.bad
        },
        tween(500), label = "status",
    )
    val pulse = rememberInfiniteTransition(label = "pulse")
    val dot by pulse.animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(900 + index * 150), RepeatMode.Reverse), label = "dot",
    )
    Card {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (s.name.startsWith("Wi-Fi")) R.drawable.ic_wifi else R.drawable.ic_cell),
                        contentDescription = null, tint = color, modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(8.dp)) {
                            drawCircle(statusColor.copy(alpha = if (s.status == "up") dot else 1f))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(s.status, color = statusColor, fontSize = 13.sp)
                        Text("  ·  ${s.active} open", color = p.muted, fontSize = 13.sp)
                    }
                }
                Text(
                    "${(share * 100).toInt()}%",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color,
                    textAlign = TextAlign.End,
                )
            }
            Spacer(Modifier.height(14.dp))
            // Share of the combined traffic this network is carrying right now.
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(p.muted.copy(alpha = 0.18f), cornerRadius = r)
                drawRoundRect(
                    Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color)),
                    size = size.copy(width = size.width * share), cornerRadius = r,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Metric("↓ Download", Stats.rate(s.rateIn), Modifier.weight(1f))
                Metric("↑ Upload", Stats.rate(s.rateOut), Modifier.weight(1f))
                Metric("Moved", Stats.size((s.bytesIn + s.bytesOut).toDouble()), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier) {
        Text(label, color = p.muted, fontSize = 12.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun WaitingCard() {
    val p = LocalPalette.current
    Card {
        Text(
            "Waiting for Wi-Fi or mobile data. Make sure both are switched on.",
            color = p.muted, fontSize = 14.sp,
        )
    }
}

@Composable
private fun IntroCard() {
    val p = LocalPalette.current
    Card {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_wifi), null, tint = p.wifi, modifier = Modifier.size(20.dp))
                Text("  +  ", color = p.muted)
                Icon(painterResource(R.drawable.ic_cell), null, tint = p.cell, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Use both at once", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Each new connection goes out over Wi-Fi or mobile data, balanced by how fast each one is. " +
                    "Downloads and pages that open many connections get faster; a single connection stays on one network. " +
                    "Mobile data stays on while merging, so keep an eye on your data plan.",
                color = p.muted, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun SettingsCard(evenSplit: Boolean, onEvenSplit: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Split evenly", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Give both networks the same share of new connections instead of following their speed. " +
                        "Only helps when Wi-Fi and mobile data are about as fast as each other.",
                    color = p.muted, fontSize = 13.sp, lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = evenSplit,
                onCheckedChange = onEvenSplit,
                colors = SwitchDefaults.colors(checkedTrackColor = p.good),
            )
        }
    }
}

@Composable
private fun ModeCard(
    mode: Engine.Mode,
    phase: Engine.Phase,
    onModeChange: (Engine.Mode) -> Unit,
) {
    val p = LocalPalette.current
    Card {
        Column {
            Text("Chế độ hoạt động", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                if (phase != Engine.Phase.OFF) "Tắt kết nối trước khi đổi chế độ"
                else "Chọn chế độ phù hợp với nhu cầu:",
                color = p.muted, fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == Engine.Mode.PROXY,
                    onClick = { if (phase == Engine.Phase.OFF) onModeChange(Engine.Mode.PROXY) },
                    enabled = phase == Engine.Phase.OFF,
                    label = { Text("Proxy SOCKS5 (Dùng chung)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = p.good.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                FilterChip(
                    selected = mode == Engine.Mode.VPN,
                    onClick = { if (phase == Engine.Phase.OFF) onModeChange(Engine.Mode.VPN) },
                    enabled = phase == Engine.Phase.OFF,
                    label = { Text("VPN (Độc lập)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = p.good.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ProxyGuideCard(phase: Engine.Phase) {
    val p = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val addr = "${Engine.PROXY_HOST}:${Engine.PROXY_PORT}"
    Card {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cổng SOCKS5: ", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(addr, fontWeight = FontWeight.Bold, color = p.good, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text(
                    "Sao chép",
                    color = p.wifi,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable {
                            clipboard.setText(AnnotatedString(addr))
                            Toast.makeText(context, "Đã sao chép $addr", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Cách dùng chung với RethinkDNS:\n" +
                    "1. Bấm nút nguồn bên trên để bật Net-Merge\n" +
                    "2. Mở RethinkDNS > Proxy > SOCKS5 > Nhập $addr\n" +
                    "3. Bật VPN của RethinkDNS để vừa chặn quảng cáo vừa gộp mạng!",
                color = p.muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
        }
    }
}
