package top.myrest.myflow.ai

import java.util.Date
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.hutool.core.date.DateUtil
import cn.hutool.core.img.ImgUtil
import cn.hutool.core.util.RandomUtil
import com.unfbx.chatgpt.entity.chat.Message
import kotlinx.coroutines.delay
import top.myrest.myflow.AppInfo
import top.myrest.myflow.action.ActionResult
import top.myrest.myflow.action.customContentResult
import top.myrest.myflow.component.Composes
import top.myrest.myflow.component.MyHoverable
import top.myrest.myflow.component.MyMarkdownText
import top.myrest.myflow.component.logoSize
import top.myrest.myflow.constant.AppConsts
import top.myrest.myflow.util.Jackson.readByJsonArray

private const val MIN_SIZE = 36

internal fun resolveSession(session: AssistantFocusedSession?): String {
    if (session != null) {
        for (result in session.results.get()) {
            val finalResult = result.result
            if (finalResult is ChatHistoryDoc && finalResult.session.isNotBlank()) {
                return finalResult.session
            }
        }
    }
    val length = RandomUtil.randomInt(5, 10)
    return RandomUtil.randomString(length)
}

internal fun renderChatResult(session: AssistantFocusedSession, userDoc: ChatHistoryDoc, chatDoc: ChatHistoryDoc, success: Boolean) {
    val list = mutableListOf(userDoc.toResult(), chatDoc.toResult())
    list.addAll(session.results.get())
    if (success) {
        ChatHistoryRepo.addChat(userDoc, chatDoc)
        session.results.set(list)
        session.chatHistoryWindow?.updateChatList?.invoke(session.results.get().filter { it.result is ChatHistoryDoc }.map { it.result as ChatHistoryDoc })
    }
    Composes.actionWindowProvider?.updateActionResultList(session.pin, list)
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun ChatHistoryDoc.toResult(): ActionResult = customContentResult(
    actionId = "",
    result = this,
    contentHeight = -1,
    content = {
        val isUser = role == Message.Role.USER.getName()
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            var showTime by remember { mutableStateOf(false) }
            Image(
                painter = Composes.getPainter(if (isUser) Constants.userLogo else Constants.robotLogo) ?: painterResource(AppInfo.LOGO),
                contentDescription = AppConsts.LOGO,
                modifier = Modifier.width(MIN_SIZE.dp).height(MIN_SIZE.dp).onPointerEvent(eventType = PointerEventType.Enter) {
                    showTime = true
                }.onPointerEvent(eventType = PointerEventType.Exit) {
                    showTime = false
                },
                contentScale = ContentScale.FillBounds,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_SIZE.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (isUser && showTime) {
                    Text(
                        text = DateUtil.formatDateTime(Date(at)),
                        color = MaterialTheme.colors.onPrimary.copy(0.3f),
                        fontSize = MaterialTheme.typography.subtitle2.fontSize,
                    )
                }
                ChatResponseViewer(isUser)
            }
        }
    },
)

@Composable
@Suppress("FunctionName")
@OptIn(ExperimentalFoundationApi::class)
internal fun ChatHistoryDoc.ChatResponseViewer(isUser: Boolean) {
    when (type) {
        ContentType.TEXT -> {
            if (isUser) {
                Text(
                    text = content,
                    color = MaterialTheme.colors.onSecondary,
                    fontSize = MaterialTheme.typography.h6.fontSize,
                    overflow = TextOverflow.Visible,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                MaterialTheme(
                    colors = MaterialTheme.colors,
                    typography = MaterialTheme.typography.copy(
                        body1 = MaterialTheme.typography.h6,
                    ),
                ) {
                    MyMarkdownText(content)
                }
            }
        }

        ContentType.IMAGES -> {
            Column {
                value.readByJsonArray<String>().forEach { it ->
                    val img = ImgUtil.toImage(it)
                    val painter = Composes.getPainter(img)
                    if (painter != null) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Image(painter = painter, contentDescription = "image", contentScale = ContentScale.None)
                            MyHoverable(padding = 3.dp) {
                                Icon(
                                    imageVector = Icons.Outlined.Download,
                                    contentDescription = "Download",
                                    tint = MaterialTheme.colors.onSecondary,
                                    modifier = Modifier.height(16.dp).width(16.dp).onClick {
                                        val file = AppInfo.actionWindow.showFileChooser(fileToSave = true).firstOrNull()
                                        if (file != null) {
                                            ImgUtil.write(img, file)
                                        }
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        else -> {}
    }
}

@Composable
@Suppress("FunctionName")
internal fun StreamResult(
    session: AssistantFocusedSession,
    doc: ChatHistoryDoc,
    logo: Painter?,
    listener: StreamResultListener,
) {
    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
        Image(
            painter = logo ?: painterResource(AppInfo.LOGO),
            contentDescription = AppConsts.LOGO,
            modifier = Modifier.width(logoSize.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        var text by remember { mutableStateOf(AppInfo.currLanguageBundle.shared.connecting) }
        LaunchedEffect(Unit) {
            while (!listener.isClosed()) {
                delay(50)
                if (listener.hasNewText()) {
                    text = listener.consumeBuffer()
                }
            }
            val chatDoc = ChatHistoryDoc(doc.session, listener.getRole(), listener.consumeBuffer(), listener.getProvider())
            renderChatResult(session, doc, chatDoc, listener.isSuccess())
        }
        DisposableEffect(Unit) {
            onDispose {
                listener.close()
            }
        }
        Text(
            text = text,
            color = MaterialTheme.colors.onPrimary,
            fontSize = MaterialTheme.typography.h6.fontSize,
            overflow = TextOverflow.Visible,
        )
    }
}

internal interface StreamResultListener {
    fun isClosed(): Boolean
    fun close()
    fun hasNewText(): Boolean
    fun consumeBuffer(): String
    fun isSuccess(): Boolean
    fun getProvider(): String
    fun getRole(): String
}
