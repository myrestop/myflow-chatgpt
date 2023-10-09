package top.myrest.myflow.chatgpt

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unfbx.chatgpt.OpenAiStreamClient
import com.unfbx.chatgpt.entity.chat.ChatCompletion
import com.unfbx.chatgpt.entity.chat.ChatCompletionResponse
import com.unfbx.chatgpt.entity.chat.Message
import com.unfbx.chatgpt.sse.ConsoleEventSourceListener
import kotlinx.coroutines.delay
import okhttp3.Response
import okhttp3.sse.EventSource
import org.slf4j.LoggerFactory
import top.myrest.myflow.AppInfo
import top.myrest.myflow.action.ActionResult
import top.myrest.myflow.action.customContentResult
import top.myrest.myflow.component.Composes
import top.myrest.myflow.constant.AppConsts
import top.myrest.myflow.dev.DevProps
import top.myrest.myflow.util.Jackson.readByJson

internal object ChatGptStreamResults {

    private val client = OpenAiStreamClient.builder().apiKey(listOf(ChatGptActionHandler.apiKey)).build()

    fun getResult(session: ChatGptFocusedSession, action: String): ActionResult {
        val messages = mutableListOf<Message>()
        session.results.forEach {
            val result = it.result
            if (result is Message) {
                messages.add(result)
            }
        }

        val message = Message.builder().role(Message.Role.USER).content(action).build()
        messages.add(message)
        val completion = ChatCompletion.builder().temperature(ChatGptActionHandler.temperature).model(ChatGptActionHandler.model).messages(messages).build()
        val listener = OpenAiStreamEventListener()
        client.streamChatCompletion(completion, listener)

        return customContentResult(
            actionId = "",
            contentHeight = 600,
            content = {
                listener.ChatGptStreamResult(session, message)
            },
        )
    }

    private class OpenAiStreamEventListener : ConsoleEventSourceListener() {

        private val log = LoggerFactory.getLogger(OpenAiStreamEventListener::class.java)

        private val textBuffer = StringBuffer()

        private val hasNewText = AtomicBoolean(false)

        private val closed = AtomicBoolean(false)

        override fun onOpen(eventSource: EventSource, response: Response) {
            log.info("connect to openai")
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            if (DevProps.isDev) {
                log.info("get openai data: {}", data);
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
            eventSource.cancel()
        }

        @Composable
        @Suppress("FunctionName")
        fun ChatGptStreamResult(session: ChatGptFocusedSession, userMessage: Message) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
                Image(
                    painter = Composes.getPainter(Constants.chatGptLogo) ?: painterResource(AppInfo.LOGO),
                    contentDescription = AppConsts.LOGO,
                    modifier = Modifier.width(40.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                    var text by remember { mutableStateOf(userMessage.content) }
                    var atUser by remember { mutableStateOf(true) }
                    var cHeight by remember { mutableStateOf(0) }
                    var uHeight by remember { mutableStateOf(0) }
                    LaunchedEffect(Unit) {
                        atUser = false
                        while (!closed.get()) {
                            delay(50)
                            if (hasNewText.get()) {
                                text = textBuffer.toString()
                                hasNewText.set(false)
                            }
                        }
                        delay(100)
                        val chatGptMessage = Message.builder().role(Message.Role.ASSISTANT).content(text).build()
                        renderMessageResult(session, chatGptMessage, cHeight, userMessage, uHeight)
                    }
                    Text(
                        text = text,
                        color = MaterialTheme.colors.onPrimary,
                        fontSize = MaterialTheme.typography.h6.fontSize,
                        overflow = TextOverflow.Visible,
                        onTextLayout = {
                            val height = it.lineCount * 20
                            if (atUser) {
                                uHeight = height
                            } else {
                                cHeight = height
                            }
                        },
                    )
                }
            }
        }

        private fun renderMessageResult(session: ChatGptFocusedSession, chatGptMessage: Message, cHeight: Int, userMessage: Message, uHeight: Int) {
            val list = mutableListOf(userMessage.toResult(uHeight), chatGptMessage.toResult(cHeight))
            list.addAll(session.results)
            session.results = list
            Composes.actionWindowProvider?.updateActionResultList(session.pin, session.results)
        }

        private fun Message.toResult(height: Int): ActionResult = customContentResult(
            actionId = "",
            contentHeight = max(height, 60),
            result = this,
            content = {
                val isUser = role == Message.Role.USER.getName()
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = if (isUser) Alignment.CenterVertically else Alignment.Top,
                ) {
                    Image(
                        painter = Composes.getPainter(if (isUser) Constants.userLogo else Constants.chatGptLogo) ?: painterResource(AppInfo.LOGO),
                        contentDescription = AppConsts.LOGO,
                        modifier = Modifier.width(40.dp).height(40.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = content,
                            color = if (isUser) MaterialTheme.colors.onSecondary else MaterialTheme.colors.onPrimary,
                            fontSize = MaterialTheme.typography.h6.fontSize,
                            overflow = TextOverflow.Visible,
                            fontWeight = if (isUser) FontWeight.Bold else null,
                        )
                    }
                }
            },
        )
    }
}