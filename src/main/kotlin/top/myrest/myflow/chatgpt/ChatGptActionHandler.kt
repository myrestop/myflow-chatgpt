package top.myrest.myflow.chatgpt

import top.myrest.myflow.AppInfo
import top.myrest.myflow.action.*
import top.myrest.myflow.component.ActionKeywordPin
import top.myrest.myflow.component.SettingsContent
import top.myrest.myflow.enumeration.ActionWindowBehavior
import top.myrest.myflow.language.LanguageBundle

class ChatGptActionHandler : ActionFocusedKeywordHandler {

    override fun getCustomizeSettingContent(): SettingsContent? {
        return super.getCustomizeSettingContent()
    }

    override fun enterFocusMode(pin: ActionKeywordPin): ActionFocusedSession {
        return ChatGptFocusedSession(pin)
    }

    companion object {

        const val API_KEY = Constants.PLUGIN_ID + ".ApiKey"

        var apiKey: String = AppInfo.runtimeProps.getParam(API_KEY, "")
    }
}

private class ChatGptFocusedSession(pin: ActionKeywordPin) : ActionFocusedSession(pin) {

    override fun exitFocusMode() {}

    override fun getLabel(): String {
        if (ChatGptActionHandler.apiKey.isBlank()) {
            return LanguageBundle.getBy(Constants.PLUGIN_ID, "input-api-key")
        }
        return ""
    }

    override fun queryAction(action: String): List<ActionResult> {
        if (action.isBlank()) {
            return emptyList()
        }
        if (ChatGptActionHandler.apiKey.isBlank()) {
            return listOf(
                ActionResult(
                    actionId = "",
                    title = listOf(action.plain),
                    subtitle = LanguageBundle.getBy(Constants.PLUGIN_ID, "set-api-key"),
                    result = action,
                    callbacks = singleCallback(
                        actionWindowBehavior = ActionWindowBehavior.EMPTY_LIST,
                    ) {
                        if (it is String) {
                            AppInfo.runtimeProps.paramMap[ChatGptActionHandler.API_KEY] = it
                            ChatGptActionHandler.apiKey = it
                        }
                    },
                )
            )
        }
        return emptyList()
    }
}