package top.myrest.myflow.chatgpt

import kotlin.math.max
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.onClick
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TextFieldDefaults.indicatorLine
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.dizitart.no2.IndexType
import org.dizitart.no2.objects.Index
import org.dizitart.no2.objects.Indices
import org.dizitart.no2.objects.InheritIndices
import org.dizitart.no2.objects.filters.ObjectFilters
import top.myrest.myflow.AppInfo
import top.myrest.myflow.chatgpt.ChatGptStreamResults.toResult
import top.myrest.myflow.component.ActionKeywordPin
import top.myrest.myflow.component.AttachedWindow
import top.myrest.myflow.component.Composes
import top.myrest.myflow.component.MyHoverable
import top.myrest.myflow.component.MyVerticalListViewer
import top.myrest.myflow.db.AutoIncrementDoc
import top.myrest.myflow.db.BaseRepo
import top.myrest.myflow.enumeration.CornerType
import top.myrest.myflow.event.ActionPinKeywordChangedEvent
import top.myrest.myflow.event.BaseEventListener
import top.myrest.myflow.event.EventExtraDetail
import top.myrest.myflow.util.SearchingSyntax

internal enum class ContentType {
    TEXT, IMAGES, FILE
}

@InheritIndices
@Indices(
    value = [
        Index(value = "session", type = IndexType.NonUnique),
        Index(value = "content", type = IndexType.Fulltext),
    ]
)
internal data class ChatHistoryDoc(
    val session: String,
    val role: String,
    val content: String,
    val value: String = "",
    val type: ContentType = ContentType.TEXT,
    val at: Long = System.currentTimeMillis(),
) : AutoIncrementDoc()

internal object ChatHistoryRepo : BaseRepo<Int, ChatHistoryDoc>(ChatHistoryDoc::class.java) {

    fun addChat(userDoc: ChatHistoryDoc, chatDoc: ChatHistoryDoc) {
        if (userDoc.id != null || chatDoc.id != null || userDoc.session.isBlank() || chatDoc.session.isBlank()) {
            return
        }
        insertDoc(chatDoc)
        insertDoc(userDoc)
    }

    fun searchChat(keyword: String): List<ChatHistoryData> {
        val list = if (keyword.isBlank()) {
            getAll().sortedByDescending { it.at }
        } else {
            val kw = SearchingSyntax.normalizeKeyword(keyword, fuzzyStart = true, fuzzyEnd = true)
            getBy(ObjectFilters.text("content", kw), descOpt("at"))
        }

        var session = ""
        val results = mutableListOf<ChatHistoryData>()
        val result = mutableListOf<ChatHistoryDoc>()
        list.forEach {
            if (session != it.session && result.isNotEmpty()) {
                results.add(ChatHistoryData(result.first(), result))
                result.clear()
            }
            session = it.session
            result.add(it)
        }

        if (result.isNotEmpty()) {
            results.add(ChatHistoryData(result.first(), result))
        }

        return results
    }
}

internal data class ChatHistoryData(val firstUser: ChatHistoryDoc, val list: List<ChatHistoryDoc>)

internal class ChatHistoryWindow(private val session: ChatGptFocusedSession, private val pin: ActionKeywordPin) : AttachedWindow(CornerType.LEFT_SIDE, 200, 120) {

    private val pinChangedListener = object : BaseEventListener<ActionPinKeywordChangedEvent>(ActionPinKeywordChangedEvent::class.java) {
        override fun onEvent(detail: EventExtraDetail, event: ActionPinKeywordChangedEvent) {
            dialog.isVisible = pin == event.pin
        }
    }

    init {
        pinChangedListener.listen()
    }

    override fun attach() {
        super.attach()
        setContent {
            MaterialTheme(colors = AppInfo.themeColors, typography = AppInfo.typography) {
                Column(modifier = Modifier.fillMaxSize().border(1.dp, MaterialTheme.colors.primaryVariant).background(MaterialTheme.colors.background)) {
                    var value by remember { mutableStateOf("") }
                    SearchTextField(value) { value = it }
                    ChatHistoryViewer(value)
                }
            }
        }
    }

    @Composable
    @Suppress("FunctionName")
    @OptIn(ExperimentalFoundationApi::class)
    private fun ChatHistoryViewer(value: String) {
        val list = ChatHistoryRepo.searchChat(value)
        if (list.isEmpty()) {
            return
        }
        var currItem: ChatHistoryData? by remember { mutableStateOf(null) }
        Box(modifier = Modifier.fillMaxSize()) {
            MyVerticalListViewer(
                pinId = pin.getPinId(),
                list = list,
            ) { _, item ->
                var modifier = Modifier.fillMaxWidth().height(30.dp).onClick {
                    currItem = item
                    session.results.set(item.list.map { it.toResult() })
                    Composes.actionWindowProvider?.updateActionResultList(pin, session.results.get())
                }
                if (item == currItem) {
                    modifier = modifier.background(MaterialTheme.colors.secondaryVariant)
                }
                MyHoverable(
                    hoveredBackground = MaterialTheme.colors.secondaryVariant,
                    modifier = modifier,
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = item.firstUser.content,
                        color = MaterialTheme.colors.onPrimary.copy(0.7f),
                        modifier = Modifier.padding(start = 2.dp),
                        overflow = TextOverflow.Ellipsis,
                        fontSize = MaterialTheme.typography.h6.fontSize,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    @Composable
    @Suppress("FunctionName")
    @OptIn(ExperimentalMaterialApi::class)
    private fun SearchTextField(value: String, onValueUpdate: (String) -> Unit) {
        val interactionSource = remember { MutableInteractionSource() }
        BasicTextField(
            value = value,
            onValueChange = onValueUpdate,
            textStyle = TextStyle(
                color = MaterialTheme.colors.onPrimary.copy(0.7f),
                fontSize = MaterialTheme.typography.h6.fontSize,
            ),
            modifier = Modifier.height(30.dp).fillMaxWidth().indicatorLine(
                enabled = true,
                isError = false,
                interactionSource = interactionSource,
                focusedIndicatorLineThickness = 1.dp,
                unfocusedIndicatorLineThickness = 1.dp,
                colors = TextFieldDefaults.textFieldColors(
                    unfocusedIndicatorColor = MaterialTheme.colors.onPrimary.copy(0.3f),
                    backgroundColor = MaterialTheme.colors.background,
                ),
            ).padding(6.dp),
        )
    }

    override fun onActionWindowSizeUpdate(width: Int, height: Int) {
        if (dialog.isVisible) {
            super.onActionWindowSizeUpdate(width, height)
            dialog.setSize(dialog.width, max(initHeight ?: 0, height))
        }
    }

    override fun dispose() {
        pinChangedListener.unlisten()
        super.dispose()
    }
}
