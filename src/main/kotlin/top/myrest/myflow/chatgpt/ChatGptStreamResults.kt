package top.myrest.myflow.chatgpt

import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import top.myrest.myflow.component.MyMarkdownText
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
            contentHeight = -1,
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
                SelectionContainer {
                    var text by remember { mutableStateOf("") }
                    LaunchedEffect(Unit) {
                        while (!closed.get()) {
                            delay(50)
                            if (hasNewText.get()) {
                                text = textBuffer.toString()
                                hasNewText.set(false)
                            }
                        }
                        delay(100)
                        val chatGptMessage = Message.builder().role(Message.Role.ASSISTANT).content(textBuffer.toString()).build()
                        renderMessageResult(session, chatGptMessage, userMessage)
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

        private fun renderMessageResult(session: ChatGptFocusedSession, chatGptMessage: Message, userMessage: Message) {
            val list = mutableListOf(userMessage.toResult(), chatGptMessage.toResult())
            list.addAll(session.results)
            session.results = list
            Composes.actionWindowProvider?.updateActionResultList(session.pin, session.results)
        }

        private fun Message.toResult(): ActionResult = customContentResult(
            actionId = "",
            result = this,
            contentHeight = -1,
            content = {
                val isUser = role == Message.Role.USER.getName()
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    val minSize = 40
                    Image(
                        painter = Composes.getPainter(if (isUser) Constants.userLogo else Constants.chatGptLogo) ?: painterResource(AppInfo.LOGO),
                        contentDescription = AppConsts.LOGO,
                        modifier = Modifier.width(minSize.dp).height(minSize.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    SelectionContainer {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = minSize.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                }
            },
        )
    }
}