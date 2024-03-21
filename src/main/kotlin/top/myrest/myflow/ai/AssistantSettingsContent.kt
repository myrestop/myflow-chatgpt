package top.myrest.myflow.ai

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unfbx.chatgpt.entity.chat.BaseChatCompletion
import top.myrest.myflow.AppInfo
import top.myrest.myflow.ai.spark.SparkCommonSettings
import top.myrest.myflow.component.FuncPageScope
import top.myrest.myflow.component.MyHoverable
import top.myrest.myflow.component.SettingCheckBox
import top.myrest.myflow.component.SettingCombo
import top.myrest.myflow.component.SettingInputText
import top.myrest.myflow.component.SettingItemRow
import top.myrest.myflow.component.SettingLabelText
import top.myrest.myflow.component.SettingSlider
import top.myrest.myflow.component.SettingsContent
import top.myrest.myflow.language.LanguageBundle

internal class AssistantSettingsContent : SettingsContent {

    @OptIn(ExperimentalFoundationApi::class)
    override val settingsContent: @Composable FuncPageScope.(pluginId: String) -> Unit
        get() = {
            Column(
                modifier = Modifier.width(400.dp).background(MaterialTheme.colors.secondary).padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                var provider by remember { mutableStateOf(Constants.provider) }
                var currentIsDefault by remember { mutableStateOf(false) }
                currentIsDefault = provider == Constants.provider

                Row {
                    listOf(
                        Constants.OPENAI_PROVIDER to "OpenAI",
                        Constants.SPARK_PROVIDER to LanguageBundle.getBy(Constants.PLUGIN_ID, "spark-desk"),
                    ).forEach { pair ->
                        var modifier = Modifier.height(30.dp)
                        if (provider == pair.first) {
                            modifier = modifier.background(MaterialTheme.colors.secondaryVariant)
                        }
                        MyHoverable(
                            contentAlignment = Alignment.Center,
                            modifier = modifier.onClick { provider = pair.first },
                            hoveredBackground = MaterialTheme.colors.secondaryVariant,
                        ) {
                            Text(
                                text = pair.second,
                                fontSize = MaterialTheme.typography.subtitle1.fontSize,
                                color = MaterialTheme.colors.onSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
                SettingCheckBox(
                    label = LanguageBundle.getBy(Constants.PLUGIN_ID, "set-as-default"),
                    checked = currentIsDefault,
                    update = {
                        currentIsDefault = it
                        Constants.provider = if (it) provider else ""
                    },
                )
                when (provider) {
                    Constants.OPENAI_PROVIDER -> OenAiSettings()
                    Constants.SPARK_PROVIDER -> SparkDeskSettings()
                }
            }
        }

    @Composable
    @Suppress("FunctionName")
    private fun SparkDeskSettings() {
        SparkCommonSettings()
        var temperature: Float by remember { mutableStateOf(Constants.sparkTemperature) }
        SettingSlider(
            label = LanguageBundle.getBy(Constants.PLUGIN_ID, "temperature"),
            labelWidth = 90,
            value = temperature,
            valueRange = 0f..1f,
            preContent = {
                SettingLabelText("%.2f".format(temperature), null)
            },
            update = {
                temperature = it
                Constants.sparkTemperature = it
            },
        )
    }

    @Composable
    @Suppress("FunctionName")
    private fun OenAiSettings() {
        val labelWidth = 120
        SettingItemRow {
            SettingInputText(
                label = LanguageBundle.getBy(Constants.PLUGIN_ID, "openai-api-host"),
                labelWidth = labelWidth,
                value = Constants.openaiApiHost,
                placeholder = Constants.OPENAI_API_HOST,
                update = { Constants.openaiApiHost = it },
            )
        }
        SettingItemRow {
            SettingInputText(
                label = "API Key",
                labelWidth = labelWidth,
                value = Constants.openaiApiKey,
                placeholder = LanguageBundle.getBy(Constants.PLUGIN_ID, "input-openai-api-key"),
                update = { Constants.openaiApiKey = it },
            )
        }

        var model by remember { mutableStateOf(Constants.openaiModel) }
        SettingCombo(
            label = "ChatGPT" + AppInfo.currLanguageBundle.wordSep + LanguageBundle.getBy(Constants.PLUGIN_ID, "model"),
            labelWidth = labelWidth,
            value = model,
            menus = BaseChatCompletion.Model.entries.map { it.getName() },
            valueMapper = { it },
            onMenuClick = {
                model = it
                Constants.openaiModel = it
            },
        )

        var temperature: Float by remember { mutableStateOf(Constants.openaiTemperature) }
        SettingSlider(
            label = LanguageBundle.getBy(Constants.PLUGIN_ID, "temperature"),
            labelWidth = labelWidth,
            value = temperature,
            valueRange = 0f..2f,
            preContent = {
                SettingLabelText("%.2f".format(temperature), null)
            },
            update = {
                temperature = it
                Constants.openaiTemperature = it
            },
        )
    }
}