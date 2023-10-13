package top.myrest.myflow.ai.spark

import java.util.concurrent.atomic.AtomicBoolean
import com.unfbx.sparkdesk.SparkDeskClient
import com.unfbx.sparkdesk.constant.SparkDesk
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

    private val client = SparkDeskClient.builder().host(SparkDesk.SPARK_API_HOST_WS_V2_1).appid(Constants.sparkAppId).apiKey(Constants.sparkApiKey).apiSecret(Constants.sparkApiSecret).build()

    fun getStreamChatResult(session: AssistantFocusedSession, action: String): ActionResult {
        val header = InHeader.builder().appid(Constants.sparkAppId).build()
        val parameter = Parameter.builder().chat(Chat.builder().domain("generalv2").maxTokens(2048).temperature(Constants.sparkTemperature.toDouble()).build()).build()

        val texts = mutableListOf<Text>()
        session.results.get().forEach {
            val result = it.result
            if (result is ChatHistoryDoc) {
                texts.add(result.toText())
            }
        }

        val doc = action.asUserTextDoc(session)
        texts.add(doc.toText())
        val payload = InPayload.builder().message(Message.builder().text(texts).build()).build()
        val request = AIChatRequest.builder().header(header).parameter(parameter).payload(payload).build()
        val listener = SparkStreamChatListener(request)
        client.chat(listener)

        return customContentResult(
            actionId = "",
            contentHeight = -1,
            content = {
                StreamResult(session, doc, Composes.getPainter(Constants.sparkLogo), listener)
            },
        )
    }

    private fun String.asUserTextDoc(session: AssistantFocusedSession) = ChatHistoryDoc(resolveSession(session), Text.Role.USER.getName(), this, Constants.SPARK_PROVIDER)

    private fun ChatHistoryDoc.toText(): Text {
        return Text.builder().role(role).content(content).build()
    }

    private class SparkStreamChatListener(request: AIChatRequest) : ChatListener(request), StreamResultListener {

        private val log = LoggerFactory.getLogger(SparkStreamChatListener::class.java)

        private val textBuffer = StringBuffer()

        private val hasNewText = AtomicBoolean(false)

        private val closed = AtomicBoolean(false)

        private val failure = AtomicBoolean(false)

        override fun isClosed(): Boolean = closed.get()

        override fun close() = closed.set(true)

        override fun hasNewText(): Boolean = hasNewText.get()

        override fun getProvider(): String = Constants.SPARK_PROVIDER

        override fun consumeBuffer(): String {
            hasNewText.set(false)
            return textBuffer.toString()
        }

        override fun isSuccess(): Boolean = !failure.get()

        override fun onChatError(response: AIChatResponse) {
            log.error("open spark chat session error: {}", response)
            textBuffer.append(response.toJsonString(true))
            hasNewText.set(true)
            closed.set(true)
            failure.set(true)
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
            hasNewText.set(true)
        }

        override fun onChatEnd() {
            log.info("close spark chat session")
            closed.set(true)
        }

        override fun onChatToken(usage: Usage) {
            log.info("token usage: {}", usage.toJsonString())
        }
    }
}