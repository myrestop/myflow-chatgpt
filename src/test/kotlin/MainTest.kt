import top.myrest.myflow.AppInfo
import top.myrest.myflow.action.Actions
import top.myrest.myflow.baseimpl.App
import top.myrest.myflow.baseimpl.FlowApp
import top.myrest.myflow.baseimpl.Replacement
import top.myrest.myflow.baseimpl.enableDevEnv
import top.myrest.myflow.baseimpl.setting.AppSettingsActionHandler
import top.myrest.myflow.baseimpl.setting.SettingKey
import top.myrest.myflow.baseimpl.setting.SettingKeys
import top.myrest.myflow.chatgpt.ChatGptSettingsContent
import top.myrest.myflow.chatgpt.Constants
import top.myrest.myflow.dev.DevProps

fun main() {
    val name = "ChatGPT"
    enableDevEnv()
    DevProps.disableNativeListener = false
    val mySettings = object : SettingKeys() {
        override fun getSettingKeys(): List<SettingKey> {
            val list = mutableListOf<SettingKey>()
            list.addAll(super.getSettingKeys())
            list.add(
                SettingKey(
                    key = Constants.PLUGIN_ID,
                    name = name,
                    logo = "./logos/chatgpt.png",
                    content = ChatGptSettingsContent(),
                )
            )
            return list
        }
    }
    object : FlowApp() {
        override fun getReplacement(): Replacement {
            return super.getReplacement().copy(settingKeys = mySettings)
        }
    }.configApp()
    Actions.register("settings", AppSettingsActionHandler())
    App(AppInfo.APP_NAME + name)
}