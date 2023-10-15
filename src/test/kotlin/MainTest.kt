import top.myrest.myflow.AppInfo
import top.myrest.myflow.action.Actions
import top.myrest.myflow.ai.Constants
import top.myrest.myflow.ai.spark.TranslateSettingsContent
import top.myrest.myflow.baseimpl.App
import top.myrest.myflow.baseimpl.FlowApp
import top.myrest.myflow.baseimpl.Replacement
import top.myrest.myflow.baseimpl.action.ActionFuncPageResultImpl
import top.myrest.myflow.baseimpl.enableDevEnv
import top.myrest.myflow.baseimpl.setting.AppSettingsActionHandler
import top.myrest.myflow.baseimpl.setting.SettingKey
import top.myrest.myflow.baseimpl.setting.SettingKeys
import top.myrest.myflow.component.Composes
import top.myrest.myflow.dev.DevProps
import top.myrest.myflow.event.AppStartedEvent
import top.myrest.myflow.event.EventBus.addSimpleListener

fun main() {
    val name = "Assistant"
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
                    logo = "./logos/robot.png",
                    content = TranslateSettingsContent(),
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

    DevProps.pinFuncPage(AppSettingsActionHandler().getFuncPageResults().first())
    AppStartedEvent::class.java.addSimpleListener {
//        Actions.setFocusedData(Composes.mainPin, Constants.PLUGIN_ID, AppConsts.ANY_KEYWORD, AssistantActionHandler())
        DevProps.getFuncPages().forEach {
            Composes.actionWindowProvider?.pinActionFuncPage(ActionFuncPageResultImpl(Constants.PLUGIN_ID, it))
        }
    }
    App(AppInfo.APP_NAME + name)
}