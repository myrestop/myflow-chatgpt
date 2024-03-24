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
import top.myrest.myflow.action.ActionResult
import top.myrest.myflow.action.BaseDigestActionHandler
import top.myrest.myflow.action.basicCopyResult
import top.myrest.myflow.ai.Constants
import top.myrest.myflow.component.FuncPageScope
import top.myrest.myflow.component.SettingCombo
import top.myrest.myflow.component.SettingHorizontalDivider
import top.myrest.myflow.component.SettingInputText
import top.myrest.myflow.component.SettingItemRow
import top.myrest.myflow.component.SettingsContent
import top.myrest.myflow.enumeration.ActionArgMode
import top.myrest.myflow.enumeration.ActionArgType
import top.myrest.myflow.language.LanguageBundle
import top.myrest.myflow.util.singleList

class SparkTranslateActionHandler : BaseDigestActionHandler() {

    override val argRequireMode = ActionArgMode.REQUIRE_NOT_EMPTY to ActionArgType.STRING.singleList()

    override fun getCustomizeSettingContent(): SettingsContent = TranslateSettingsContent()

    override fun queryDigestAction(content: String): ActionResult {
        return basicCopyResult(actionId = "spark.translation", score = 100, result = SparkTranslations.translate(content))
    }
}

internal class TranslateSettingsContent : SettingsContent {
    override val settingsContent: @Composable FuncPageScope.(pluginId: String) -> Unit
        get() = {
            Column(
                modifier = Modifier.width(400.dp).background(MaterialTheme.colors.secondary).padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                var source by remember { mutableStateOf(SparkLanguageType.valueOf(Constants.sparkTranslationSourceLanguage)) }
                SettingCombo(
                    label = LanguageBundle.getBy(Constants.PLUGIN_ID, "source-language"),
                    labelWidth = 120,
                    value = source.type.localeName,
                    menus = SparkLanguageType.entries,
                    valueMapper = { it.type.localeName },
                    onMenuClick = {
                        source = it
                        Constants.sparkTranslationSourceLanguage = it.name
                    },
                )

                var target by remember { mutableStateOf(SparkLanguageType.valueOf(Constants.sparkTranslationTargetLanguage)) }
                SettingCombo(
                    label = LanguageBundle.getBy(Constants.PLUGIN_ID, "target-language"),
                    labelWidth = 120,
                    value = target.type.localeName,
                    menus = SparkLanguageType.values().toList(),
                    valueMapper = { it.type.localeName },
                    onMenuClick = {
                        target = it
                        Constants.sparkTranslationTargetLanguage = it.name
                    },
                )

                SettingHorizontalDivider()
                SparkCommonSettings()
            }
        }
}

@Composable
@Suppress("FunctionName")
internal fun SparkCommonSettings() {
    val labelWidth = 90
    SettingItemRow {
        SettingInputText(
            label = "App ID",
            labelWidth = labelWidth,
            value = Constants.sparkAppId,
            placeholder = "app id",
            update = { Constants.sparkAppId = it },
        )
    }
    SettingItemRow {
        SettingInputText(
            label = "API Secret",
            labelWidth = labelWidth,
            value = Constants.sparkApiSecret,
            placeholder = "app secret",
            update = { Constants.sparkApiSecret = it },
        )
    }
    SettingItemRow {
        SettingInputText(
            label = "API Key",
            labelWidth = labelWidth,
            value = Constants.sparkApiKey,
            placeholder = "app key",
            update = { Constants.sparkApiKey = it },
        )
    }
}
