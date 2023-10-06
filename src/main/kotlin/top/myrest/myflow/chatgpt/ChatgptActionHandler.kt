package top.myrest.myflow.chatgpt

import top.myrest.myflow.action.ActionFocusedKeywordHandler
import top.myrest.myflow.action.ActionFocusedSession
import top.myrest.myflow.action.ActionResult
import top.myrest.myflow.component.ActionKeywordPin
import top.myrest.myflow.component.SettingsContent
import top.myrest.myflow.enumeration.HotEventType

class ChatgptActionHandler : ActionFocusedKeywordHandler {

    override fun getCustomizeSettingContent(): SettingsContent? {
        return super.getCustomizeSettingContent()
    }

    override fun isSupportHotEventTrigger(): Boolean {
        return super.isSupportHotEventTrigger()
    }

    override fun triggerOnHotEvent(hotEventType: HotEventType) {
        super.triggerOnHotEvent(hotEventType)
    }

    override fun enterFocusMode(pin: ActionKeywordPin): ActionFocusedSession {
        return ChatgptFocusedSession(pin)
    }
}

private class ChatgptFocusedSession(pin: ActionKeywordPin) : ActionFocusedSession(pin) {

    override fun exitFocusMode() {}

    override fun getLabel(): String = ""

    override fun queryAction(action: String): List<ActionResult> {
        return emptyList()
    }
}