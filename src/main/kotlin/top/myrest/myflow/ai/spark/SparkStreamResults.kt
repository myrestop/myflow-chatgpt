package top.myrest.myflow.ai.spark

import java.util.concurrent.atomic.AtomicBoolean
import com.unfbx.sparkdesk.SparkDeskClient
import com.unfbx.sparkdesk.entity.AIChatRequest
import com.unfbx.sparkdesk.entity.AIChatResponse
import com.unfbx.sparkdesk.entity.Chat
import com.unfbx.sparkdesk.entity.InHeader
import com.unfbx.sparkdesk.entity.InPayload
import com.unfbx.sparkdesk.entity.Message
import com.unfbx.sparkdesk.entity.Parameter
import com.unfbx.sparkdesk.entity.Text
import com.unfbx.sparkdesk.entity.Usage
import com.unfbx.sparkdesk.listener.ChatListener
import okhttp3.WebSocket
import org.slf4j.LoggerFactory
import top.myrest.myflow.action.ActionResult
import top.myrest.myflow.action.customContentResult
import top.myrest.myflow.ai.AssistantFocusedSession
import top.myrest.myflow.ai.ChatHistoryDoc
import top.myrest.myflow.ai.Constants
import top.myrest.myflow.ai.StreamResult
import top.myrest.myflow.ai.StreamResultListener
import top.myrest.myflow.ai.resolveSession
import top.myrest.myflow.component.Composes
import top.myrest.myflow.dev.DevProps
import top.myrest.myflow.util.Jackson.toJsonString

internal object SparkStreamResults {

    private var _client: SparkDeskClient? = null

    val client: SparkDeskClient
        @Synchronized get() {
            val appId = Constants.sparkAppId
            val apiKey = Constants.sparkApiKey
            val apiSecret = Constants.sparkApiSecret
            val innerClient = _client
            if (innerClient == null || innerClient.appid != appId || innerClient.apiKey != apiKey || innerClient.apiSecret != apiSecret) {
                _client = SparkDeskClient.builder().host("https://spark-api.xf-yun.com/v3.5/chat").appid(appId).apiKey(apiKey).apiSecret(apiSecret).build()
            }
            return _client!!
        }

    fun getStreamChatResult(session: AssistantFocusedSession, action: String): ActionResult {
        val texts = mutableListOf<Text>()
        session.results.get().forEach {
            val result = it.result
            if (result is ChatHistoryDoc) {
                texts.add(result.toText())
            }
        }

        val doc = action.asUserSparkTextDoc(session)
        texts.add(doc.toText())
        val listener = getListener(texts)

        return customContentResult(
            actionId = "",
            contentHeight = -1,
            content = {
                StreamResult(session, doc, Composes.getPainter(Constants.sparkLogo), listener)
            },
        )
    }

    fun getListener(texts: List<Text>): StreamResultListener {
        val header = InHeader.builder().appid(Constants.sparkAppId).build()
        val parameter = Parameter.builder().chat(Chat.builder().domain("generalv2").maxTokens(2048).temperature(Constants.sparkTemperature.toDouble()).build()).build()
        val payload = InPayload.builder().message(Message.builder().text(texts).build()).build()
        val request = AIChatRequest.builder().header(header).parameter(parameter).payload(payload).build()
        val listener = SparkStreamChatListener(request)
        listener.socket = client.chat(listener)
        return listener
    }

    fun String.asUserSparkTextDoc(session: AssistantFocusedSession?) = ChatHistoryDoc(resolveSession(session), Text.Role.USER.getName(), this, Constants.SPARK_PROVIDER)

    fun ChatHistoryDoc.toText(): Text {
        return Text.builder().role(role).content(content).build()
    }

    private class SparkStreamChatListener(request: AIChatRequest) : ChatListener(request), StreamResultListener {

        private val log = LoggerFactory.getLogger(SparkStreamChatListener::class.java)

        private val textBuffer = StringBuffer()

        private val closed = AtomicBoolean(false)

        private val failure = AtomicBoolean(false)

        lateinit var socket: WebSocket

        private var updater: ((String, Boolean) -> Unit)? = null

        override fun close() {
            closed.set(true)
            socket.cancel()
            log.info("close spark websocket connection")
        }

        override fun getProvider(): String = Constants.SPARK_PROVIDER

        override fun getRole(): String = Text.Role.ASSISTANT.getName()

        override fun isSuccess(): Boolean = !failure.get()

        override fun onChatError(response: AIChatResponse) {
            log.error("open spark chat session error: {}", response)
            textBuffer.append(response.toJsonString(true))
            closed.set(true)
            failure.set(true)
            socket.cancel()
            updater?.invoke(textBuffer.toString(), false)
        }

        override fun onChatOutput(response: AIChatResponse) {
            if (DevProps.isDev) {
                log.debug("get spark data: {}", response);
            }
            if (closed.get()) {
                return
            }
            response.payload.choices.text.forEach {
                textBuffer.append(it.content)
            }
            updater?.invoke(textBuffer.toString(), false)
        }

        override fun updateText(updater: (text: String, finished: Boolean) -> Unit) {
            this.updater = updater
        }

        override fun onChatEnd() {
            log.info("close spark chat session")
            closed.set(true)
            updater?.invoke(textBuffer.toString(), true)
        }

        override fun onChatToken(usage: Usage) {
            log.info("token usage: {}", usage.toJsonString())
        }
    }
}