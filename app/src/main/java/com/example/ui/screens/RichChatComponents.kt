package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AudioPlaybackUiState
import com.example.data.ChatMediaHelper
import com.example.data.ChatAudioPlayer
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.example.data.VoiceWaveformCodec
import com.example.model.AvatarColor
import com.example.model.DirectMessage
import com.example.model.MessageDeliveryStatus
import kotlin.math.roundToInt

data class MentionCandidate(
    val userId: String,
    val displayName: String,
    val avatarColorKey: String = AvatarColor.AMBER.key,
    val isOnline: Boolean = false,
    val photoData: String? = null,
    val photoUrl: String? = null,
    val subtitle: String = "",
)

fun findActiveMentionQuery(text: String, cursorPos: Int): Pair<Int, String>? {
    if (text.isEmpty()) return null
    val safeCursor = cursorPos.coerceIn(0, text.length)
    for (i in (safeCursor - 1) downTo 0) {
        val c = text[i]
        if (c == '\n' || c == '\r') return null
        if (c == '@') {
            val validBoundary = i == 0 || text[i - 1].isWhitespace() || text[i - 1] in charArrayOf('(', '[', '"', '\'')
            if (!validBoundary) return null
            val query = text.substring(i + 1, safeCursor)
            if (query.length > 32 || query.count { it == ' ' } > 2) return null
            return i to query
        }
    }
    return null
}

fun filterMentionCandidates(
    candidates: List<MentionCandidate>,
    query: String,
): List<MentionCandidate> {
    if (candidates.isEmpty()) return emptyList()
    if (query.isEmpty()) return candidates
    if (query.endsWith(" ")) {
        val exactCandidateDone = candidates.any {
            it.displayName.equals(query.trimEnd(), ignoreCase = true)
        }
        if (exactCandidateDone) return emptyList()
    }
    val cleanQuery = query.trim()
    if (cleanQuery.isEmpty()) return candidates
    return candidates
        .filter { it.displayName.contains(cleanQuery, ignoreCase = true) }
        .sortedWith(
            compareByDescending<MentionCandidate> {
                it.displayName.startsWith(cleanQuery, ignoreCase = true)
            }.thenBy { it.displayName.lowercase() }
        )
}

fun insertMention(
    value: TextFieldValue,
    atIndex: Int,
    displayName: String,
    maxLength: Int = 1000,
): TextFieldValue {
    val safeStart = atIndex.coerceIn(0, value.text.length)
    val safeCursor = value.selection.end.coerceIn(safeStart, value.text.length)
    val prefix = value.text.substring(0, safeStart)
    val suffix = value.text.substring(safeCursor)
    val insertion = "@$displayName "
    val combined = (prefix + insertion + suffix).take(maxLength)
    val newCursor = (prefix.length + insertion.length).coerceAtMost(combined.length)
    return TextFieldValue(
        text = combined,
        selection = TextRange(newCursor),
    )
}

fun appendTextAtSelection(
    value: TextFieldValue,
    addition: String,
    maxLength: Int = 1000,
): TextFieldValue {
    if (value.text.length + addition.length > maxLength) return value
    val start = value.selection.start.coerceIn(0, value.text.length)
    val end = value.selection.end.coerceIn(start, value.text.length)
    val newText = value.text.substring(0, start) + addition + value.text.substring(end)
    val newCursor = (start + addition.length).coerceAtMost(newText.length)
    return TextFieldValue(
        text = newText,
        selection = TextRange(newCursor),
    )
}

fun buildMentionAnnotatedText(
    text: String,
    mentionNames: List<String>,
    highlightColor: Color,
    badgeBackground: Color = highlightColor.copy(alpha = 0.14f),
): AnnotatedString {
    if (text.isEmpty() || !text.contains('@')) return AnnotatedString(text)
    val ranges = mutableListOf<IntRange>()
    val cleanNames = mentionNames
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedByDescending { it.length }

    for (name in cleanNames) {
        val target = "@$name"
        var searchFrom = 0
        while (searchFrom < text.length) {
            val idx = text.indexOf(target, startIndex = searchFrom, ignoreCase = true)
            if (idx < 0) break
            val end = idx + target.length
            val startOk = idx == 0 || !text[idx - 1].isLetterOrDigit()
            val endOk = end == text.length || !text[end].isLetterOrDigit()
            val overlaps = ranges.any { r -> idx <= r.last && (end - 1) >= r.first }
            if (startOk && endOk && !overlaps) {
                ranges.add(idx until end)
            }
            searchFrom = end
        }
    }

    val genericMentionRegex = Regex("""(?:^|(?<=[^\p{L}\p{N}_]))@[\p{L}\p{N}._-]{2,30}""")
    genericMentionRegex.findAll(text).forEach { match ->
        val r = match.range
        val overlaps = ranges.any { existing -> r.first <= existing.last && r.last >= existing.first }
        if (!overlaps) {
            ranges.add(r)
        }
    }

    if (ranges.isEmpty()) return AnnotatedString(text)
    val sorted = ranges.sortedBy { it.first }
    return buildAnnotatedString {
        var cursor = 0
        for (r in sorted) {
            if (r.first > cursor) {
                append(text.substring(cursor, r.first))
            }
            withStyle(
                SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.SemiBold,
                    background = badgeBackground,
                )
            ) {
                append(text.substring(r.first, r.last + 1))
            }
            cursor = r.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

@Composable
fun MentionSuggestionsPanel(
    candidates: List<MentionCandidate>,
    query: String,
    onSelect: (MentionCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(candidates, query) {
        filterMentionCandidates(candidates, query)
    }
    if (filtered.isEmpty()) return

    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("mention_suggestions_panel"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Etiquetar persona (@)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp),
            ) {
                lazyItems(filtered, key = { it.userId }) { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelect(candidate) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("mention_option_${candidate.userId}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AvatarCircle(
                            name = candidate.displayName,
                            avatarColor = AvatarColor.fromKey(candidate.avatarColorKey),
                            size = 32.dp,
                            isOnline = candidate.isOnline,
                            photoData = candidate.photoData,
                            photoUrl = candidate.photoUrl,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = candidate.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (candidate.subtitle.isNotBlank()) {
                                Text(
                                    text = candidate.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            text = "@${candidate.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp),
                        )
                    }
                }
            }
        }
    }
}

data class EmojiCategory(
    val key: String,
    val icon: String,
    val labelEs: String,
    val emojis: List<String>,
)

val SERAFIEL_EMOJI_CATEGORIES = listOf(
    EmojiCategory(
        key = "faces",
        icon = "😊",
        labelEs = "Caras",
        emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😗", "😚", "😋", "😛", "😜", "🤪", "😝",
            "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐",
            "😑", "😶", "😏", "😒", "🙄", "😬", "😌", "😔",
            "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤧",
            "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "😎",
            "🤓", "🧐", "😕", "😟", "🙁", "😮", "😯", "😲",
            "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢",
            "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫",
            "🥱", "😤", "😡", "😠", "🤬", "😈", "👿", "💀",
            "🤡", "👻", "👽", "🤖", "💩", "❤️", "🧡", "💛",
            "💚", "💙", "💜", "🖤", "🤍", "💔", "❤️‍🔥", "💕",
        ),
    ),
    EmojiCategory(
        key = "people",
        icon = "👋",
        labelEs = "Gestos",
        emojis = listOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏",
            "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆",
            "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜",
            "👏", "🙌", "🫶", "👐", "🤲", "🤝", "🙏", "✍️",
            "💪", "🦵", "🦶", "👂", "👃", "🧠", "👀", "👁️",
            "👶", "🧒", "👦", "👧", "🧑", "👨", "👩", "🧓",
            "👴", "👵", "🙋", "🙆", "🙅", "🤷", "🤦", "💁",
            "🚶", "🏃", "💃", "🕺", "👯", "🧘", "🧑‍🤝‍🧑", "👨‍👩‍👧‍👦",
        ),
    ),
    EmojiCategory(
        key = "nature",
        icon = "🌿",
        labelEs = "Naturaleza",
        emojis = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🙈",
            "🙉", "🙊", "🐔", "🐧", "🐦", "🐤", "🦅", "🦉",
            "🦆", "🐺", "🐴", "🦄", "🐝", "🦋", "🐌", "🐞",
            "🐢", "🐍", "🦖", "🐙", "🐠", "🐬", "🐳", "🦈",
            "🌸", "💐", "🌷", "🌹", "🌺", "🌻", "🌼", "🌱",
            "🌲", "🌳", "🌴", "🌵", "🍀", "🍁", "🍂", "🍃",
            "☀️", "🌤️", "⛅", "🌦️", "🌧️", "⛈️", "🌈", "⭐",
            "🌟", "✨", "⚡", "🔥", "💧", "🌊", "🌙", "🌍",
        ),
    ),
    EmojiCategory(
        key = "food",
        icon = "🍕",
        labelEs = "Comida",
        emojis = listOf(
            "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇",
            "🍓", "🫐", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝",
            "🍅", "🥑", "🍆", "🥦", "🌽", "🌶️", "🧄", "🧅",
            "🍞", "🥐", "🥖", "🧀", "🥚", "🍳", "🥞", "🧇",
            "🥓", "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🌯",
            "🥗", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟",
            "🍤", "🍦", "🍨", "🍩", "🍪", "🎂", "🍰", "🧁",
            "🍫", "🍬", "🍯", "☕", "🍵", "🧃", "🥤", "🧋",
            "🍺", "🍻", "🥂", "🍷", "🍸", "🍹", "🧉", "🍾",
        ),
    ),
    EmojiCategory(
        key = "activities",
        icon = "⚽",
        labelEs = "Deportes",
        emojis = listOf(
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉",
            "🎱", "🏓", "🏸", "🏒", "⛳", "🏹", "🎣", "🥊",
            "🥋", "⛸️", "🎿", "🏂", "🏋️", "🤸", "🚴", "🏊",
            "🏄", "🧗", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️",
            "🎫", "🎟️", "🎭", "🎨", "🎬", "🎤", "🎧", "🎼",
            "🎹", "🥁", "🎷", "🎺", "🎸", "🎻", "🎲", "♟️",
            "🎯", "🎳", "🎮", "🕹️", "🧩", "🎉", "🎊", "🎈",
            "🎁", "🎇", "🎆", "🪁", "🧸", "🎪", "🎡", "🎢",
        ),
    ),
    EmojiCategory(
        key = "travel",
        icon = "✈️",
        labelEs = "Viajes",
        emojis = listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑",
            "🚒", "🚐", "🚚", "🚜", "🛵", "🏍️", "🚲", "🛴",
            "🚨", "🚆", "🚇", "Tram" to "🚊", "🚝", "✈️", "🛫", "🛬",
            "🚀", "🛸", "🚁", "⛵", "🚤", "🛳️", "⛴️", "🚢",
            "🗺️", "🧭", "🏔️", "⛰️", "🌋", "🏕️", "🏖️", "🏜️",
            "🏝️", "🏞️", "🏟️", "🏛️", "🏗️", "🏠", "🏡", "🏢",
            "🏥", "🏦", "🏨", "🏪", "🏫", "🏬", "⛪", "🕌",
            "🗼", "🗽", "⛲", "⛺", "🌃", "🏙️", "🌄", "🌅",
        ).map { if (it is Pair<*, *>) it.second.toString() else it.toString() },
    ),
    EmojiCategory(
        key = "objects",
        icon = "💡",
        labelEs = "Objetos",
        emojis = listOf(
            "⌚", "📱", "💻", "⌨️", "🖥️", "🖨️", "📷", "📸",
            "📹", "🎥", "📞", "☎️", "📺", "📻", "🎙️", "⏰",
            "🕰️", "⌛", "🔋", "🔌", "💡", "🔦", "🕯️", "💸",
            "💵", "💰", "💳", "💎", "⚖️", "🔧", "🔨", "⚒️",
            "🪛", "🔩", "⚙️", "🔗", "📎", "✂️", "📌", "📍",
            "🔑", "🗝️", "🔒", "🔓", "🔔", "📣", "📢", "📖",
            "📚", "📓", "📝", "✏️", "✒️", "✉️", "📩", "📦",
            "📅", "📊", "📈", "🎁", "🛍️", "🎒", "👓", "🕶️",
        ),
    ),
    EmojiCategory(
        key = "symbols",
        icon = "✨",
        labelEs = "Símbolos",
        emojis = listOf(
            "💯", "💢", "💥", "💫", "💦", "💨", "💤", "💬",
            "💭", "🗯️", "♠️", "♥️", "♦️", "♣️", "🔔", "🎵",
            "🎶", "✅", "❌", "⭕", "❓", "❔", "❕", "❗",
            "‼️", "⁉️", "➕", "➖", "➗", "✖️", "♾️", "💲",
            "♻️", "🔱", "⚜️", "🔰", "☑️", "✔️", "🔝", "🔙",
            "🔚", "🔛", "🔜", "🆕", "🆓", "🆙", "🆗", "🆘",
            "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫", "⚪",
            "🇪🇸", "🇲🇽", "🇦🇷", "🇨🇴", "🇨🇱", "🇵🇪", "🇺🇸", "🏳️‍🌈",
        ),
    ),
)

@Composable
fun EmojiPickerPanel(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val activeCategory = SERAFIEL_EMOJI_CATEGORIES.getOrElse(selectedTab) {
        SERAFIEL_EMOJI_CATEGORIES.first()
    }

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .testTag("emoji_picker_panel"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SERAFIEL_EMOJI_CATEGORIES.forEachIndexed { index, category ->
                    FilterChip(
                        selected = index == selectedTab,
                        onClick = { selectedTab = index },
                        label = { Text("${category.icon} ${category.labelEs}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("emoji_tab_${category.key}"),
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 42.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
            ) {
                items(activeCategory.emojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onEmojiSelected(emoji) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatAttachmentOptionsBar(
    onTakeCameraPhoto: () -> Unit,
    onPickImage: () -> Unit,
    onPickAudio: () -> Unit,
    onOpenLocationPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .testTag("chat_attachments_panel"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AttachmentOptionTile(
                icon = Icons.Outlined.PhotoCamera,
                label = "Cámara",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                testTag = "attach_camera_button",
                onClick = onTakeCameraPhoto,
            )
            AttachmentOptionTile(
                icon = Icons.Outlined.Image,
                label = "Galería",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                testTag = "attach_image_button",
                onClick = onPickImage,
            )
            AttachmentOptionTile(
                icon = Icons.Outlined.AudioFile,
                label = "Audio",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                testTag = "attach_audio_button",
                onClick = onPickAudio,
            )
            AttachmentOptionTile(
                icon = Icons.Outlined.LocationOn,
                label = "Ubicación",
                containerColor = Color(0xFFD1FAE5),
                contentColor = Color(0xFF065F46),
                testTag = "attach_location_button",
                onClick = onOpenLocationPicker,
            )
        }
    }
}

@Composable
private fun AttachmentOptionTile(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    testTag: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag),
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

data class ReplyDraft(
    val messageId: String,
    val senderName: String,
    val previewText: String,
)

@Composable
fun SwipeToReplyWrapper(
    onSwipeReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val thresholdPx = with(density) { 52.dp.toPx() }
    val maxDragPx = with(density) { 78.dp.toPx() }
    var rawDragPx by remember { mutableFloatStateOf(0f) }
    var triggeredHaptic by remember { mutableStateOf(false) }
    val swipeCallback by rememberUpdatedState(onSwipeReply)

    val animatedOffset by animateFloatAsState(
        targetValue = rawDragPx,
        animationSpec = spring(stiffness = 650f, dampingRatio = 0.78f),
        label = "swipeReplyOffset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(thresholdPx, maxDragPx) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        val next = (rawDragPx + dragAmount).coerceIn(0f, maxDragPx)
                        if (!triggeredHaptic && next >= thresholdPx) {
                            triggeredHaptic = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (next < thresholdPx * 0.75f) {
                            triggeredHaptic = false
                        }
                        rawDragPx = next
                    },
                    onDragEnd = {
                        if (rawDragPx >= thresholdPx) {
                            swipeCallback()
                        }
                        rawDragPx = 0f
                        triggeredHaptic = false
                    },
                    onDragCancel = {
                        rawDragPx = 0f
                        triggeredHaptic = false
                    },
                )
            },
    ) {
        val progress = (animatedOffset / thresholdPx).coerceIn(0f, 1f)
        if (progress > 0.05f) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .size(34.dp)
                    .alpha(progress),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Reply,
                        contentDescription = "Responder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) },
        ) {
            content()
        }
    }
}

@Composable
fun ReplyQuoteBox(
    senderName: String?,
    previewText: String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = contentColor.copy(alpha = 0.08f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor),
            )
            Column(modifier = Modifier.weight(1f)) {
                if (!senderName.isNullOrBlank()) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = previewText.ifBlank { "Mensaje" },
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ReplyOrEditComposerBanner(
    replyDraft: ReplyDraft?,
    editingTextPreview: String?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (replyDraft == null && editingTextPreview == null) return
    val isEditing = editingTextPreview != null
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .testTag(if (isEditing) "editing_banner" else "replying_banner"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (isEditing) Icons.Outlined.Edit else Icons.AutoMirrored.Rounded.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEditing) "Editando mensaje" else "Respondiendo a ${replyDraft?.senderName ?: ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = editingTextPreview ?: replyDraft?.previewText.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("cancel_reply_or_edit_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Cancelar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun MessageActionsDialog(
    senderName: String,
    previewText: String,
    canCopy: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    myReaction: String? = null,
    onReact: ((String?) -> Unit)? = null,
    onForward: (() -> Unit)? = null,
) {
    var showFullEmojiPicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = senderName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onReact != null) {
                    if (showFullEmojiPicker) {
                        EmojiPickerPanel(
                            onEmojiSelected = { emoji ->
                                onDismiss()
                                onReact(emoji)
                            },
                        )
                    } else {
                        QuickReactionsRow(
                            myReaction = myReaction,
                            onReact = { emoji ->
                                onDismiss()
                                onReact(emoji)
                            },
                            onOpenFullPicker = { showFullEmojiPicker = true },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                if (previewText.isNotBlank()) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                MessageActionRow(
                    icon = Icons.AutoMirrored.Rounded.Reply,
                    label = "Responder",
                    testTag = "action_reply_message",
                    onClick = {
                        onDismiss()
                        onReply()
                    },
                )
                if (canCopy) {
                    MessageActionRow(
                        icon = Icons.Outlined.ContentCopy,
                        label = "Copiar texto",
                        testTag = "action_copy_message",
                        onClick = {
                            onDismiss()
                            onCopy()
                        },
                    )
                }
                if (onForward != null) {
                    MessageActionRow(
                        icon = Icons.AutoMirrored.Rounded.Reply,
                        label = "Reenviar",
                        testTag = "action_forward_message",
                        onClick = {
                            onDismiss()
                            onForward()
                        },
                    )
                }
                if (canEdit) {
                    MessageActionRow(
                        icon = Icons.Outlined.Edit,
                        label = "Editar mensaje",
                        testTag = "action_edit_message",
                        onClick = {
                            onDismiss()
                            onEdit()
                        },
                    )
                }
                if (canDelete) {
                    MessageActionRow(
                        icon = Icons.Outlined.DeleteOutline,
                        label = "Eliminar mensaje",
                        tint = MaterialTheme.colorScheme.error,
                        testTag = "action_delete_message",
                        onClick = {
                            onDismiss()
                            onDelete()
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
private fun MessageActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DirectMessageBubble(
    message: DirectMessage,
    isMine: Boolean,
    audioPlaybackState: AudioPlaybackUiState,
    onToggleAudio: (DirectMessage) -> Unit,
    onStopLiveLocation: () -> Unit,
    onPreviewImage: (String) -> Unit,
    onOpenLocation: () -> Unit = {},
    onLongPressOrSelect: () -> Unit = {},
    senderLabel: String? = null,
    senderPhotoData: String? = null,
    senderPhotoUrl: String? = null,
    senderColor: AvatarColor = AvatarColor.AMBER,
    mentionNames: List<String> = emptyList(),
    deliveryStatus: MessageDeliveryStatus? = null,
    myUserId: String = "",
    onToggleReaction: ((String) -> Unit)? = null,
    onOpenVideo: () -> Unit = {},
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp,
    )
    val bubbleColor = if (isMine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
      Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(bubbleShape)
                .combinedClickable(
                    onClick = onLongPressOrSelect,
                    onLongClick = onLongPressOrSelect,
                )
                .testTag("message_bubble_${message.id}"),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!senderLabel.isNullOrBlank() && !isMine) {
                    Text(
                        text = senderLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (message.forwarded) {
                    Text(
                        text = "↪ Reenviado",
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!message.replyToMessageId.isNullOrBlank()) {
                    ReplyQuoteBox(
                        senderName = message.replyToSenderName,
                        previewText = message.replyToPreview.orEmpty(),
                        contentColor = contentColor,
                    )
                }

                when (message.messageType) {
                    DirectMessage.TYPE_IMAGE -> {
                        val standardBitmap = remember(message.id, message.mediaData) {
                            ChatMediaHelper.decodeBase64Bitmap(message.mediaData)?.asImageBitmap()
                        }
                        val hdBitmap = rememberHdImage(message, force = false, maxSidePx = 1080)
                        val imageBitmap = hdBitmap ?: standardBitmap
                        if (imageBitmap != null) {
                            Box {
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = "Imagen compartida",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp, max = 230.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(
                                            onClick = { onPreviewImage(message.mediaData.orEmpty()) },
                                            onLongClick = onLongPressOrSelect,
                                        ),
                                )
                                if (message.chunkCount > 0L) {
                                    HdBadge(
                                        downloaded = hdBitmap != null,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp),
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Imagen no disponible",
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                            )
                        }
                        if (message.text.isNotBlank()) {
                            Text(
                                text = buildMentionAnnotatedText(
                                    text = message.text,
                                    mentionNames = mentionNames,
                                    highlightColor = MaterialTheme.colorScheme.primary,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                            )
                        }
                    }

                    DirectMessage.TYPE_VIDEO -> {
                        VideoMessageContent(
                            message = message,
                            onOpen = onOpenVideo,
                            onLongPress = onLongPressOrSelect,
                        )
                        if (message.text.isNotBlank()) {
                            Text(
                                text = buildMentionAnnotatedText(
                                    text = message.text,
                                    mentionNames = mentionNames,
                                    highlightColor = MaterialTheme.colorScheme.primary,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor,
                            )
                        }
                    }

                    DirectMessage.TYPE_VOICE, DirectMessage.TYPE_AUDIO -> {
                        val isThisActive = audioPlaybackState.activeMessageId == message.id
                        val isPlaying = isThisActive && audioPlaybackState.isPlaying
                        val isVoice = message.messageType == DirectMessage.TYPE_VOICE
                        val titleLabel = if (isVoice) {
                            "Mensaje de voz"
                        } else {
                            message.mediaName?.takeIf { it.isNotBlank() } ?: "Archivo de audio"
                        }
                        val timeText = if (isThisActive && audioPlaybackState.currentSec > 0L) {
                            "${DirectMessage.formatDuration(audioPlaybackState.currentSec)} / ${DirectMessage.formatDuration(message.durationSec)}"
                        } else if (message.durationSec > 0L) {
                            DirectMessage.formatDuration(message.durationSec)
                        } else {
                            "Audio"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            IconButton(
                                onClick = { onToggleAudio(message) },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier.size(44.dp),
                            ) {
                                if (isThisActive && audioPlaybackState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pausar audio" else "Reproducir audio",
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isVoice) Icons.Rounded.GraphicEq else Icons.Outlined.AudioFile,
                                        contentDescription = null,
                                        tint = contentColor,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = titleLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = contentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                val audioContext = LocalContext.current
                                val playbackSpeed by ChatAudioPlayer.speed.collectAsState()
                                val levels = remember(message.id, message.waveform) {
                                    VoiceWaveformCodec.decode(message.waveform)
                                        ?: VoiceWaveformCodec.pseudo(message.id)
                                }
                                VoiceWaveformBar(
                                    levels = levels,
                                    progress = if (isThisActive) audioPlaybackState.progress else 0f,
                                    playedColor = MaterialTheme.colorScheme.secondary,
                                    pendingColor = contentColor.copy(alpha = 0.35f),
                                    onSeek = { fraction -> ChatAudioPlayer.seekMessage(audioContext, message, fraction) },
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = timeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = contentColor.copy(alpha = 0.78f),
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isThisActive) {
                                        PlaybackSpeedChip(
                                            speed = playbackSpeed,
                                            onClick = { ChatAudioPlayer.cycleSpeed() },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DirectMessage.TYPE_LOCATION, DirectMessage.TYPE_LIVE_LOCATION -> {
                        WhatsAppLocationMessageCard(
                            message = message,
                            isMine = isMine,
                            senderPhotoData = senderPhotoData,
                            senderPhotoUrl = senderPhotoUrl,
                            senderColor = senderColor,
                            onStopLiveLocation = onStopLiveLocation,
                            onOpenInAppLocation = onOpenLocation,
                            onLongPress = onLongPressOrSelect,
                        )
                    }

                    else -> {
                        Text(
                            text = buildMentionAnnotatedText(
                                text = message.text,
                                mentionNames = mentionNames,
                                highlightColor = MaterialTheme.colorScheme.primary,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                        )
                    }
                }

                val baseTime = if (message.messageType == DirectMessage.TYPE_LIVE_LOCATION && message.updatedAt != null) {
                    "Actualizado: ${formatClockTime(message.updatedAt)}"
                } else {
                    formatClockTime(message.createdAt)
                }
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (message.edited) "$baseTime · Editado" else baseTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isMine && deliveryStatus != null) {
                        DeliveryTicks(status = deliveryStatus)
                    }
                }
            }
        }
        if (message.reactions.isNotEmpty()) {
            ReactionChips(
                reactions = message.reactions,
                myUserId = myUserId,
                onToggleMine = { emoji -> onToggleReaction?.invoke(emoji) },
                modifier = Modifier
                    .offset(y = (-6).dp)
                    .padding(horizontal = 8.dp),
            )
        }
      }
    }
}

@Composable
fun FullscreenImageDialog(
    base64Data: String,
    onDismiss: () -> Unit,
    hdMessage: DirectMessage? = null,
) {
    val standardBitmap = remember(base64Data) {
        ChatMediaHelper.decodeBase64Bitmap(base64Data)?.asImageBitmap()
    }
    // Al abrir a pantalla completa siempre se pide la versión HD si existe.
    val hdBitmap = rememberHdImage(hdMessage, force = true)
    val imageBitmap = hdBitmap ?: standardBitmap
    var scale by remember(base64Data) { mutableFloatStateOf(1f) }
    var offsetX by remember(base64Data) { mutableFloatStateOf(0f) }
    var offsetY by remember(base64Data) { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = nextScale
                        if (nextScale <= 1.01f) {
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            val maxPanX = (size.width * (nextScale - 1f)) / 2f
                            val maxPanY = (size.height * (nextScale - 1f)) / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxPanX, maxPanX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxPanY, maxPanY)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Vista previa ampliable de imagen",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .testTag("close_fullscreen_image_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Cerrar imagen",
                    tint = Color.White,
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = {
                            scale = (scale - 0.5f).coerceIn(1f, 5f)
                            if (scale <= 1.01f) {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Outlined.ZoomOut, contentDescription = "Alejar", tint = Color.White)
                    }
                    Text(
                        text = "${(scale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    IconButton(
                        onClick = { scale = (scale + 0.5f).coerceIn(1f, 5f) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Outlined.ZoomIn, contentDescription = "Ampliar", tint = Color.White)
                    }
                    if (scale > 1.05f) {
                        TextButton(
                            onClick = {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            },
                        ) {
                            Text("1x", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
