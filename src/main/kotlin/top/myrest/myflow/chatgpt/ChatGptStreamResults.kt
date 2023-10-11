package top.myrest.myflow.chatgpt

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.hutool.core.exceptions.ExceptionUtil
import cn.hutool.core.img.ImgUtil
import cn.hutool.core.io.FileUtil
import cn.hutool.core.util.RandomUtil
import com.unfbx.chatgpt.OpenAiClient
import com.unfbx.chatgpt.OpenAiStreamClient
import com.unfbx.chatgpt.entity.chat.ChatCompletion
import com.unfbx.chatgpt.entity.chat.ChatCompletionResponse
import com.unfbx.chatgpt.entity.chat.Message
import com.unfbx.chatgpt.entity.images.Image
import com.unfbx.chatgpt.entity.images.ImageEdit
import com.unfbx.chatgpt.entity.images.ImageVariations
import com.unfbx.chatgpt.entity.images.Item
import com.unfbx.chatgpt.entity.images.ResponseFormat
import com.unfbx.chatgpt.sse.ConsoleEventSourceListener
import kotlinx.coroutines.delay
import okhttp3.Response
import okhttp3.sse.EventSource
import org.slf4j.LoggerFactory
import top.myrest.myflow.AppInfo
import top.myrest.myflow.action.ActionResult
import top.myrest.myflow.action.customContentResult
import top.myrest.myflow.action.plain
import top.myrest.myflow.component.Composes
import top.myrest.myflow.component.MyHoverable
import top.myrest.myflow.component.MyMarkdownText
import top.myrest.myflow.constant.AppConsts
import top.myrest.myflow.dev.DevProps
import top.myrest.myflow.util.AsyncTasks
import top.myrest.myflow.util.Jackson.readByJson
import top.myrest.myflow.util.Jackson.readByJsonArray
import top.myrest.myflow.util.Jackson.toJsonString

internal object ChatGptStreamResults {

    private val log = LoggerFactory.getLogger(ChatGptStreamResults::class.java)

    private const val MIN_SIZE = 40

    private val client = OpenAiClient.builder().apiKey(listOf(ChatGptActionHandler.apiKey)).build()

    private val streamClient = OpenAiStreamClient.builder().apiKey(listOf(ChatGptActionHandler.apiKey)).build()

    fun getVariationImageResult(session: ChatGptFocusedSession, file: File): List<ActionResult> {
        return getImageResult(
            session = session,
            action = AppInfo.currLanguageBundle.shared.variation,
            getImages = { _, _ ->
                val imageVariations = ImageVariations.builder().responseFormat(ResponseFormat.B64_JSON.getName()).build()
                client.variationsImages(file, imageVariations).data
            },
        )
    }

    fun getModifyImageResult(session: ChatGptFocusedSession, action: String): List<ActionResult> {
        return getImageResult(
            session = session,
            action = action,
            getImages = { s, a ->
                var file: File = FileUtil.file(AppInfo.tempDir, "chat_gpt.png")
                for (result in s.results.get()) {
                    val finalResult = result.result
                    if (finalResult is ChatHistoryDoc) {
                        if (finalResult.type == ContentType.FILE) {
                            file = File(finalResult.value)
                        } else if (finalResult.type == ContentType.IMAGES) {
                            val base64 = finalResult.value.readByJsonArray<String>().first()
                            ImgUtil.write(ImgUtil.toImage(base64), file)
                        }
                    }
                }
                val imageEdit = ImageEdit.builder().prompt(a).responseFormat(ResponseFormat.B64_JSON.getName()).build()
                client.editImages(file, imageEdit)
            },
        )
    }

    fun getGenerateImageResult(session: ChatGptFocusedSession, action: String): List<ActionResult> {
        return getImageResult(
            session = session,
            action = action,
            getImages = { _, a ->
                val image = Image.builder().responseFormat(ResponseFormat.B64_JSON.getName()).prompt(a).build()
                client.genImages(image).data
            },
        )
    }

    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun getImageResult(session: ChatGptFocusedSession, action: String, getImages: (ChatGptFocusedSession, String) -> List<Item>): List<ActionResult> {
        AsyncTasks.execute {
            val userDoc = action.asUserTextDoc(session)
            val imageDoc = try {
                val value = getImages(session, action).map { it.b64Json }.toJsonString()
                ChatHistoryDoc(userDoc.session, Message.Role.ASSISTANT.getName(), "", value, ContentType.IMAGES)
            } catch (e: Exception) {
                log.error("error", e)
                ChatHistoryDoc(userDoc.session, Message.Role.ASSISTANT.getName(), ExceptionUtil.stacktraceToString(e, Int.MAX_VALUE))
            }
            val list = mutableListOf(userDoc.toResult(), imageDoc.toResult())
            list.addAll(session.results.get())
            if (imageDoc.type == ContentType.IMAGES) {
                ChatHistoryRepo.addChat(userDoc, imageDoc)
                session.results.set(list)
                session.chatHistoryWindow?.updateChatList?.invoke(session.results.get().filter { it.result is ChatHistoryDoc }.map { it.result as ChatHistoryDoc })
            }
            Composes.actionWindowProvider?.updateActionResultList(session.pin, list)
        }

        return listOf(
            ActionResult(actionId = "", logo = Constants.chatGptLogo, title = listOf(AppInfo.currLanguageBundle.shared.generating.plain))
        )
    }

    fun getStreamChatResult(session: ChatGptFocusedSession, action: String, model: String): ActionResult {
        val messages = mutableListOf<Message>()
        session.results.get().forEach {
            val result = it.result
            if (result is ChatHistoryDoc) {
                messages.add(result.toMessage())
            }
        }

        val doc = action.asUserTextDoc(session)
        messages.add(doc.toMessage())
        val completion = ChatCompletion.builder().temperature(ChatGptActionHandler.temperature).model(model).messages(messages).build()
        val listener = OpenAiStreamEventListener()
        streamClient.streamChatCompletion(completion, listener)

        return customContentResult(
            actionId = "",
            contentHeight = -1,
            content = {
                listener.ChatGptStreamResult(session, doc)
            },
        )
    }

    private fun String.asUserTextDoc(session: ChatGptFocusedSession) = ChatHistoryDoc(resolveSession(session), Message.Role.USER.getName(), this)

    private fun resolveSession(session: ChatGptFocusedSession): String {
        for (result in session.results.get()) {
            val finalResult = result.result
            if (finalResult is ChatHistoryDoc && finalResult.session.isNotBlank()) {
                return finalResult.session
            }
        }
        val length = RandomUtil.randomInt(5, 10)
        return RandomUtil.randomString(length)
    }

    private fun ChatHistoryDoc.toMessage(): Message {
        return Message.builder().role(role).content(content).build()
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
    fun ChatHistoryDoc.toResult(): ActionResult = customContentResult(
        actionId = "",
        result = this,
        contentHeight = -1,
        content = {
            val isUser = role == Message.Role.USER.getName()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Image(
                    painter = Composes.getPainter(if (isUser) Constants.userLogo else Constants.chatGptLogo) ?: painterResource(AppInfo.LOGO),
                    contentDescription = AppConsts.LOGO,
                    modifier = Modifier.width(MIN_SIZE.dp).height(MIN_SIZE.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = MIN_SIZE.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
//                    if (isUser) {
//                        Text(
//                            text = DateUtil.formatDateTime(Date(at)),
//                            color = MaterialTheme.colors.onPrimary.copy(0.3f),
//                            fontSize = MaterialTheme.typography.subtitle2.fontSize,
//                        )
//                    }
                    ChatResponseViewer(isUser)
                }
            }
        },
    )

    @Composable
    @Suppress("FunctionName")
    @OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
    private fun ChatHistoryDoc.ChatResponseViewer(isUser: Boolean) {
        when (type) {
            ContentType.TEXT -> {
                SelectionContainer {
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

    private class OpenAiStreamEventListener : ConsoleEventSourceListener() {

        private val log = LoggerFactory.getLogger(OpenAiStreamEventListener::class.java)

        private val textBuffer = StringBuffer()

        private val hasNewText = AtomicBoolean(false)

        private val closed = AtomicBoolean(false)

        private val failure = AtomicBoolean(false)

        override fun onOpen(eventSource: EventSource, response: Response) {
            log.info("connect to openai")
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            if (DevProps.isDev) {
                log.debug("get openai data: {}", data);
            }
            if (closed.get()) {
                eventSource.cancel()
                return
            }
            if ("[DONE]" == data) {
                closed.set(true)
                return
            }
            val json = data.readByJson<ChatCompletionResponse>()
            json.choices.forEach {
                if (it?.delta?.content != null) {
                    textBuffer.append(it.delta.content)
                }
            }
            hasNewText.set(true)
        }

        override fun onClosed(eventSource: EventSource) {
            log.info("close openai connection")
            closed.set(true)
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            val message: String = response?.body?.string() ?: response?.toString() ?: ""
            log.error("openai sse connection error: {}", message, t)
            textBuffer.append(message)
            hasNewText.set(true)
            closed.set(true)
            failure.set(true)
            eventSource.cancel()
        }

        @Composable
        @Suppress("FunctionName")
        fun ChatGptStreamResult(session: ChatGptFocusedSession, doc: ChatHistoryDoc) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
                Image(
                    painter = Composes.getPainter(Constants.chatGptLogo) ?: painterResource(AppInfo.LOGO),
                    contentDescription = AppConsts.LOGO,
                    modifier = Modifier.width(40.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                SelectionContainer {
                    var text by remember { mutableStateOf(AppInfo.currLanguageBundle.shared.connecting) }
                    LaunchedEffect(Unit) {
                        while (!closed.get()) {
                            delay(50)
                            if (hasNewText.get()) {
                                text = textBuffer.toString()
                                hasNewText.set(false)
                            }
                        }
                        val chatDoc = ChatHistoryDoc(doc.session, Message.Role.ASSISTANT.getName(), textBuffer.toString())
                        renderChatResult(session, doc, chatDoc)
                    }
                    DisposableEffect(Unit) {
                        onDispose {
                            closed.set(true)
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
        }

        private fun renderChatResult(session: ChatGptFocusedSession, userDoc: ChatHistoryDoc, chatDoc: ChatHistoryDoc) {
            val list = mutableListOf(userDoc.toResult(), chatDoc.toResult())
            list.addAll(session.results.get())
            if (!failure.get()) {
                ChatHistoryRepo.addChat(userDoc, chatDoc)
                session.results.set(list)
                session.chatHistoryWindow?.updateChatList?.invoke(session.results.get().filter { it.result is ChatHistoryDoc }.map { it.result as ChatHistoryDoc })
            }
            Composes.actionWindowProvider?.updateActionResultList(session.pin, list)
        }
    }
}
