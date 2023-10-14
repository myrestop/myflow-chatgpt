package top.myrest.myflow.ai

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.unfbx.chatgpt.entity.chat.Message
import com.unfbx.sparkdesk.entity.Text
import top.myrest.myflow.AppInfo
import top.myrest.myflow.action.ActionFocusedKeywordHandler
import top.myrest.myflow.action.ActionFocusedSession
import top.myrest.myflow.action.ActionParam
import top.myrest.myflow.action.ActionResult
import top.myrest.myflow.action.customContentResult
import top.myrest.myflow.ai.openai.ChatgptStreamResults
import top.myrest.myflow.ai.spark.SparkStreamResults
import top.myrest.myflow.component.ActionKeywordPin
import top.myrest.myflow.component.Composes
import top.myrest.myflow.component.SettingsContent
import top.myrest.myflow.component.logoSize
import top.myrest.myflow.constant.AppConsts
import top.myrest.myflow.util.singleList

class AssistantActionHandler : ActionFocusedKeywordHandler() {

    private val stopChars = setOf('`', '@', '#', '$', '%', '^', '&', '*', '(', '-', '_', '=', '+', '/', '[', '{', '\\', '|', ';', ':', '\'', '"', ',', '<', '>', '/', '·', '￥', '（', '—', '【', '、', '：', '’', '“', '，', '《', '》')

    override fun getCustomizeSettingContent(): SettingsContent {
        return AssistantSettingsContent()
    }

    override fun enterFocusMode(pin: ActionKeywordPin): ActionFocusedSession {
        return AssistantFocusedSession(pin)
    }

    override fun isSupportLazyQuery(): Boolean = true

    override fun lazyQuery(param: ActionParam): ActionResult? {
        val action = param.originAction
        if (action.isBlank() || action.last().isWhitespace() || stopChars.contains(action.last())) {
            return null
        }

        val listener: StreamResultListener = when (Constants.provider) {
            Constants.OPENAI_PROVIDER -> {
                val message = Message.builder().role(Message.Role.USER.getName()).content(action).build()
                ChatgptStreamResults.getListener(message.singleList(), Constants.openaiModel)
            }

            Constants.SPARK_PROVIDER -> {
                val text = Text.builder().role(Text.Role.USER.getName()).content(action).build()
                SparkStreamResults.getListener(text.singleList())
            }

            else -> return null
        }

        return customContentResult(
            actionId = "",
            contentHeight = -1,
            content = {
                DisposableEffect(Unit) {
                    onDispose {

                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = Composes.getPainter(Constants.robotLogo) ?: painterResource(AppInfo.LOGO),
                        contentDescription = AppConsts.LOGO,
                        modifier = Modifier.width(logoSize.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))

                }
            },
        )
    }
}
