package top.myrest.myflow.ai.spark

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.myrest.myflow.AppInfo
import top.myrest.myflow.action.ActionParam
import top.myrest.myflow.action.ActionRequireArgHandler
import top.myrest.myflow.action.ActionResult
import top.myrest.myflow.ai.Constants
import top.myrest.myflow.component.FuncPageScope
import top.myrest.myflow.component.SettingCombo
import top.myrest.myflow.component.SettingHorizontalDivider
import top.myrest.myflow.component.SettingInputText
import top.myrest.myflow.component.SettingItemRow
import top.myrest.myflow.component.SettingLabelText
import top.myrest.myflow.component.SettingSlider
import top.myrest.myflow.component.SettingsContent
import top.myrest.myflow.enumeration.ActionArgMode
import top.myrest.myflow.enumeration.ActionArgType
import top.myrest.myflow.enumeration.HotEventType
import top.myrest.myflow.language.LanguageBundle
import top.myrest.myflow.util.singleList

class SparkTranslateActionHandler : ActionRequireArgHandler() {

    override fun getCustomizeSettingContent(): SettingsContent = TranslateSettingsContent()

    override fun isSupportHotEventTrigger(): Boolean = true

    override val argRequireMode = ActionArgMode.REQUIRE_NOT_EMPTY to ActionArgType.STRING.singleList()

    override fun triggerOnHotEvent(hotEventType: HotEventType) {
        super.triggerOnHotEvent(hotEventType)
    }

    override fun queryArgAction(param: ActionParam): List<ActionResult> {
        return emptyList()
    }
}

internal class TranslateSettingsContent : SettingsContent {
    override val settingsContent: @Composable FuncPageScope.(pluginId: String) -> Unit
        get() = {
            Column(
                modifier = Modifier.width(400.dp).background(MaterialTheme.colors.secondary).padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                SparkSettings()
                SettingHorizontalDivider()
                SettingCombo(
                    label = LanguageBundle.getBy(Constants.PLUGIN_ID, "source-language"),
                    labelWidth = 90,
                    value = "en",
                    menus = listOf(""),
                    valueMapper = { it },
                    onMenuClick = {},
                )
                SettingCombo(
                    label = LanguageBundle.getBy(Constants.PLUGIN_ID, "target-language"),
                    labelWidth = 90,
                    value = "en",
                    menus = listOf(""),
                    valueMapper = { it },
                    onMenuClick = {},
                )
            }
        }
}

@Composable
@Suppress("FunctionName")
internal fun SparkSettings() {
    val labelWidth = 90
    SettingItemRow {
        SettingInputText(
            label = "App ID",
            labelWidth = labelWidth,
            value = Constants.sparkAppId,
            placeholder = "app id",
            update = {
                AppInfo.runtimeProps.paramMap[Constants.SPARK_APP_ID_KEY] = it
            },
        )
    }
    SettingItemRow {
        SettingInputText(
            label = "API Secret",
            labelWidth = labelWidth,
            value = Constants.sparkApiSecret,
            placeholder = "app secret",
            update = {
                AppInfo.runtimeProps.paramMap[Constants.SPARK_API_SECRET_KEY] = it
            },
        )
    }
    SettingItemRow {
        SettingInputText(
            label = "API Key",
            labelWidth = labelWidth,
            value = Constants.sparkApiKey,
            placeholder = "app id",
            update = {
                AppInfo.runtimeProps.paramMap[Constants.SPARK_API_KEY] = it
            },
        )
    }

    var temperature: Float by remember { mutableStateOf(Constants.sparkTemperature) }
    SettingSlider(
        label = LanguageBundle.getBy(Constants.PLUGIN_ID, "temperature"),
        labelWidth = labelWidth,
        value = temperature,
        valueRange = 0f..1f,
        preContent = {
            SettingLabelText("%.2f".format(temperature), null)
        },
        update = {
            temperature = it
            AppInfo.runtimeProps.paramMap[Constants.SPARK_TEMPERATURE_KEY] = it
        },
    )
}