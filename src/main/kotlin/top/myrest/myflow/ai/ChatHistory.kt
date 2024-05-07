package top.myrest.myflow.ai

import kotlin.math.max
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TextFieldDefaults.indicatorLine
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unfbx.chatgpt.entity.chat.BaseMessage
import org.dizitart.no2.IndexType
import org.dizitart.no2.objects.Index
import org.dizitart.no2.objects.Indices
import org.dizitart.no2.objects.InheritIndices
import org.dizitart.no2.objects.filters.ObjectFilters
import top.myrest.myflow.AppInfo
import top.myrest.myflow.component.ActionKeywordPin
import top.myrest.myflow.component.AttachedWindow
import top.myrest.myflow.component.Composes
import top.myrest.myflow.component.MyHoverable
import top.myrest.myflow.component.MyMaterialTheme
import top.myrest.myflow.component.MyVerticalListViewer
import top.myrest.myflow.db.AutoIncrementDoc
import top.myrest.myflow.db.BaseRepo
import top.myrest.myflow.enumeration.CornerType
import top.myrest.myflow.enumeration.DataModifyMethod
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
    val provider: String = Constants.OPENAI_PROVIDER,
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

    fun removeBySession(session: String) {
        removeBy(ObjectFilters.eq("session", session))
    }

    fun searchChat(keyword: String): List<ChatHistoryData> {
        val list = if (keyword.isBlank()) {
            getAll().sortedByDescending { it.id }
        } else {
            val kw = SearchingSyntax.fuzzyKeyword(keyword, fuzzyStart = true, fuzzyEnd = true)
            val sessions = getBy(ObjectFilters.text("content", kw), idDescOption).distinctBy { it.session }.map { it.session }
            getBy(ObjectFilters.`in`("session", *sessions.toTypedArray()), idDescOption)
        }

        val sessions = LinkedHashSet<String>()
        val chats = HashMap<String, ArrayList<ChatHistoryDoc>>()
        list.forEach {
            sessions.add(it.session)
            chats.computeIfAbsent(it.session) { ArrayList() }.add(it)
        }

        return sessions.map { ChatHistoryData(chats[it]!!) }
    }

    override fun onSyncFrom(method: DataModifyMethod, doc: ChatHistoryDoc) {
        doc.id = null
        when (method) {
            DataModifyMethod.UPDATE -> {}
            DataModifyMethod.ADD -> insertDoc(doc)
            DataModifyMethod.DELETE -> removeBySession(doc.session)
        }
    }
}

internal data class ChatHistoryData(val list: List<ChatHistoryDoc>) {

    val firstAsk = list.last { it.role == BaseMessage.Role.USER.getName() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ChatHistoryData
        return firstAsk == other.firstAsk
    }

    override fun hashCode(): Int {
        return firstAsk.hashCode()
    }
}

internal class ChatHistoryWindow(private val session: AssistantFocusedSession, private val pin: ActionKeywordPin) : AttachedWindow(pin.getPinId(), CornerType.LEFT_SIDE, 200, null) {

    lateinit var updateChatList: (List<ChatHistoryDoc>) -> Unit

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
            MyMaterialTheme {
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
    @OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
    private fun ChatHistoryViewer(value: String) {
        val list = remember { mutableStateListOf<ChatHistoryData>() }
        LaunchedEffect(Unit) {
            updateChatList = method@{
                val session = it.firstOrNull()?.session ?: return@method
                if (session.isBlank()) {
                    return@method
                }

                val data = ChatHistoryData(it)
                val idx = list.indexOfFirst { e -> e.firstAsk.session == session }
                if (idx < 0) {
                    list.add(0, data)
                } else {
                    list[idx] = data
                }
            }
        }
        list.clear()
        list.addAll(ChatHistoryRepo.searchChat(value))
        var currItem: ChatHistoryData? by remember { mutableStateOf(null) }
        Box(modifier = Modifier.fillMaxSize()) {
            MyVerticalListViewer(
                pinId = pin.getPinId(),
                list = list,
                emptyContent = {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = AppInfo.currLanguageBundle.shared.noContent,
                            color = MaterialTheme.colors.onSecondary.copy(0.7f),
                            fontSize = MaterialTheme.typography.h5.fontSize,
                        )
                    }
                },
            ) { idx, item ->
                if (idx == 0) {
                    MyHoverable(
                        hoveredBackground = MaterialTheme.colors.secondaryVariant,
                        modifier = Modifier.fillMaxWidth().height(30.dp).onClick {
                            session.results.set(emptyList())
                            Composes.actionWindowProvider?.updateActionResultList(pin, emptyList())
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddCircle,
                            contentDescription = "Add",
                            tint = MaterialTheme.colors.onSecondary,
                            modifier = Modifier.height(20.dp).width(20.dp),
                        )
                    }
                }
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
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = item.firstAsk.content,
                            color = MaterialTheme.colors.onPrimary.copy(0.7f),
                            modifier = Modifier.padding(start = 2.dp),
                            overflow = TextOverflow.Ellipsis,
                            fontSize = MaterialTheme.typography.h6.fontSize,
                            maxLines = 1,
                        )
                    }

                    if (it) {
                        var hovered by remember { mutableStateOf(false) }
                        Icon(
                            imageVector = if (hovered) Icons.Filled.Delete else Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colors.onSecondary,
                            modifier = Modifier.padding(end = 2.dp).height(16.dp).width(16.dp).onPointerEvent(eventType = PointerEventType.Enter) {
                                hovered = true
                            }.onPointerEvent(eventType = PointerEventType.Exit) {
                                hovered = false
                            }.onClick {
                                ChatHistoryRepo.removeBySession(item.firstAsk.session)
                                list.remove(item)
                            },
                        )
                    }
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
            singleLine = true,
            onValueChange = onValueUpdate,
            textStyle = TextStyle(
                color = MaterialTheme.colors.onPrimary.copy(0.7f),
                fontSize = MaterialTheme.typography.h6.fontSize,
            ),
            cursorBrush = SolidColor(MaterialTheme.colors.onPrimary),
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

    override fun onWindowSizeUpdate(width: Int, height: Int) {
        if (dialog.isVisible) {
            super.onWindowSizeUpdate(width, height)
            dialog.setSize(dialog.width, max(initHeight ?: 0, height))
        }
    }

    override fun dispose() {
        pinChangedListener.unlisten()
        super.dispose()
    }
}
