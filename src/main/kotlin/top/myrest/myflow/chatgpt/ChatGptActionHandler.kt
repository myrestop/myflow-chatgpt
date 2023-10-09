package top.myrest.myflow.chatgpt

import com.unfbx.chatgpt.entity.chat.ChatCompletion
import top.myrest.myflow.AppInfo
import top.myrest.myflow.action.ActionFocusedKeywordHandler
import top.myrest.myflow.action.ActionFocusedSession
import top.myrest.myflow.action.ActionResult
import top.myrest.myflow.action.plain
import top.myrest.myflow.action.singleCallback
import top.myrest.myflow.component.ActionKeywordPin
import top.myrest.myflow.component.Composes
import top.myrest.myflow.component.SettingsContent
import top.myrest.myflow.enumeration.ActionWindowBehavior
import top.myrest.myflow.language.LanguageBundle
import top.myrest.myflow.util.singleList

class ChatGptActionHandler : ActionFocusedKeywordHandler {

    override fun getCustomizeSettingContent(): SettingsContent {
        return ChatGptSettingsContent()
    }

    override fun enterFocusMode(pin: ActionKeywordPin): ActionFocusedSession {
        return ChatGptFocusedSession(pin)
    }

    companion object {

        const val API_KEY = Constants.PLUGIN_ID + ".ApiKey"

        const val TEMPERATURE_KEY = Constants.PLUGIN_ID + ".Temperature"

        const val MODEL_KEY = Constants.PLUGIN_ID + ".Model"

        var apiKey: String = AppInfo.runtimeProps.getParam(API_KEY, "")

        var temperature: Double = AppInfo.runtimeProps.getParam(TEMPERATURE_KEY, 0.6)

        var model: String = AppInfo.runtimeProps.getParam(MODEL_KEY, ChatCompletion.Model.GPT_3_5_TURBO.getName())
    }
}

internal class ChatGptFocusedSession(pin: ActionKeywordPin) : ActionFocusedSession(pin) {

    internal var results = emptyList<ActionResult>()

    private val sendMessageTip = AppInfo.currLanguageBundle.shared.send + AppInfo.currLanguageBundle.wordSep + AppInfo.currLanguageBundle.shared.message

    override fun exitFocusMode() {

    }

    override fun getLabel(): String {
        if (ChatGptActionHandler.apiKey.isBlank()) {
            return LanguageBundle.getBy(Constants.PLUGIN_ID, "input-api-key")
        }
        return sendMessageTip
    }

    override fun queryAction(action: String): List<ActionResult> {
        if (action.isBlank()) {
            return results
        }
        if (ChatGptActionHandler.apiKey.isBlank()) {
            return setApiKeyResult(action).singleList()
        }

        val list = mutableListOf(messageResult(action))
        list.addAll(results)
        return list
    }

    private fun messageResult(action: String): ActionResult {
        return ActionResult(
            actionId = "",
            title = listOf(action.plain),
            subtitle = sendMessageTip,
            result = action,
            callbacks = singleCallback(
                actionWindowBehavior = ActionWindowBehavior.NOTHING,
            ) {
                if (it is String) {
                    Composes.actionWindowProvider?.setAction(pin, "", false)
                    val list = ChatGptStreamResults.getResult(this, it).singleList()
                    Composes.actionWindowProvider?.updateActionResultList(pin, list)
                }
            },
        )
    }

    private fun setApiKeyResult(action: String) = ActionResult(
        actionId = "",
        title = listOf(action.plain),
        subtitle = LanguageBundle.getBy(Constants.PLUGIN_ID, "set-api-key"),
        result = action,
        callbacks = singleCallback(
            actionWindowBehavior = ActionWindowBehavior.EMPTY_LIST,
        ) {
            if (it is String && it.startsWith("sk-")) {
                AppInfo.runtimeProps.paramMap[ChatGptActionHandler.API_KEY] = it
                ChatGptActionHandler.apiKey = it
            }
        },
    )
}