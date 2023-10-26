package top.myrest.myflow.ai

import java.util.concurrent.atomic.AtomicReference
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.myrest.myflow.AppInfo
import top.myrest.myflow.action.ActionFocusedKeywordHandler
import top.myrest.myflow.action.ActionFocusedSession
import top.myrest.myflow.action.ActionParam
import top.myrest.myflow.action.ActionResult
import top.myrest.myflow.action.customContentResult
import top.myrest.myflow.ai.openai.ChatgptStreamResults
import top.myrest.myflow.ai.openai.ChatgptStreamResults.asUserChatgptTextDoc
import top.myrest.myflow.ai.openai.ChatgptStreamResults.toMessage
import top.myrest.myflow.ai.spark.SparkStreamResults
import top.myrest.myflow.ai.spark.SparkStreamResults.asUserSparkTextDoc
import top.myrest.myflow.ai.spark.SparkStreamResults.toText
import top.myrest.myflow.component.ActionKeywordPin
import top.myrest.myflow.component.Composes
import top.myrest.myflow.component.MyMarkdownText
import top.myrest.myflow.component.SettingsContent
import top.myrest.myflow.component.logoSize
import top.myrest.myflow.constant.AppConsts
import top.myrest.myflow.db.MyDb
import top.myrest.myflow.util.javaClassName
import top.myrest.myflow.util.singleList

class AssistantActionHandler : ActionFocusedKeywordHandler() {

    private val ref = AtomicReference<Pair<ChatHistoryDoc, StreamResultListener>>(null)

    init {
        MyDb.repos[ChatHistoryRepo.javaClassName] = ChatHistoryRepo
    }

    override fun getCustomizeSettingContent(): SettingsContent {
        return AssistantSettingsContent()
    }

    override fun enterFocusMode(pin: ActionKeywordPin): ActionFocusedSession {
        return AssistantFocusedSession(pin)
    }

    override fun queryAction(param: ActionParam): List<ActionResult> {
        var action = param.originAction.trim()
        if (param.keyword.isNotBlank() && !param.isAnyKeyword()) {
            action = action.removePrefix(param.keyword).removeSuffix(param.keyword)
        }
        if (action.isBlank() || action.last().isWhitespace()) {
            return emptyList()
        }

        action = action.trim()
        val pair = when (Constants.provider) {
            Constants.OPENAI_PROVIDER -> {
                val userDoc = action.asUserChatgptTextDoc(null)
                val message = userDoc.toMessage()
                userDoc to ChatgptStreamResults.getListener(message.singleList(), Constants.openaiModel)
            }

            Constants.SPARK_PROVIDER -> {
                val userDoc = action.asUserSparkTextDoc(null)
                val text = userDoc.toText()
                userDoc to SparkStreamResults.getListener(text.singleList())
            }

            else -> return emptyList()
        }

        ref.get()?.second?.close()
        ref.set(pair)

        return listOf(
            customContentResult(
                actionId = "",
                contentHeight = -1,
                content = {
                    var text by remember { mutableStateOf(AppInfo.currLanguageBundle.shared.connecting) }
                    var finished by remember { mutableStateOf(false) }
                    ref.get()?.second?.updateText { str, b ->
                        text = str
                        finished = false
                        if (b) {
                            val userDoc = ref.get().first
                            val listener = ref.get().second
                            val chatDoc = ChatHistoryDoc(userDoc.session, listener.getRole(), str, listener.getProvider())
                            ChatHistoryRepo.addChat(userDoc, chatDoc)
                            finished = true
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Image(
                            painter = Composes.getPainter(Constants.robotLogo) ?: painterResource(AppInfo.LOGO),
                            contentDescription = AppConsts.LOGO,
                            modifier = Modifier.width(logoSize.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        DisposableEffect(Unit) {
                            onDispose {
                                ref.get()?.second?.close()
                                ref.set(null)
                            }
                        }
                        if (finished) {
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(min = logoSize.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                MaterialTheme(
                                    colors = MaterialTheme.colors,
                                    typography = MaterialTheme.typography.copy(
                                        body1 = MaterialTheme.typography.h6,
                                    ),
                                ) {
                                    MyMarkdownText(text)
                                }
                            }
                        } else {
                            Text(
                                text = text,
                                color = MaterialTheme.colors.onPrimary,
                                fontSize = MaterialTheme.typography.h6.fontSize,
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                },
            ),
        )
    }
}
