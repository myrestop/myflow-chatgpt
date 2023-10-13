package top.myrest.myflow.ai

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
import com.unfbx.chatgpt.entity.chat.ChatCompletion
import top.myrest.myflow.AppInfo
import top.myrest.myflow.component.FuncPageScope
import top.myrest.myflow.component.SettingCombo
import top.myrest.myflow.component.SettingInputText
import top.myrest.myflow.component.SettingItemRow
import top.myrest.myflow.component.SettingLabelText
import top.myrest.myflow.component.SettingSlider
import top.myrest.myflow.component.SettingsContent
import top.myrest.myflow.language.LanguageBundle

internal class AssistantSettingsContent : SettingsContent {
    override val settingsContent: @Composable FuncPageScope.(pluginId: String) -> Unit
        get() = {
            Column(
                modifier = Modifier.width(400.dp).background(MaterialTheme.colors.secondary).padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
//                val subscription = ChatGptStreamResults.client.subscription()
//                SettingItemRow(horizontalArrangement = Arrangement.SpaceBetween) {
//                    SettingLabelText(AppInfo.currLanguageBundle.shared.account)
//                    SettingConfigText(subscription.accountName)
//                }
//                SettingItemRow(horizontalArrangement = Arrangement.SpaceBetween) {
//                    SettingLabelText(AppInfo.currLanguageBundle.shared.email)
//                    SettingConfigText(subscription.billingEmail.toString())
//                }
//                SettingItemRow(horizontalArrangement = Arrangement.SpaceBetween) {
//                    SettingLabelText(LanguageBundle.getBy(Constants.PLUGIN_ID, "balance"))
//                    SettingConfigText("$" + subscription.hardLimitUsd)
//                }
                SettingItemRow {
                    SettingInputText(
                        label = "API Key",
                        labelWidth = 120,
                        value = AssistantActionHandler.openaiApiKey,
                        placeholder = LanguageBundle.getBy(Constants.PLUGIN_ID, "input-openai-api-key"),
                        update = {
                            AppInfo.runtimeProps.paramMap[AssistantActionHandler.OPEN_API_KEY] = it
                        },
                    )
                }

                var model by remember { mutableStateOf(AssistantActionHandler.openaiModel) }
                SettingCombo(
                    label = "ChatGPT" + AppInfo.currLanguageBundle.wordSep + LanguageBundle.getBy(Constants.PLUGIN_ID, "chatgpt-model"),
                    labelWidth = 120,
                    value = model,
                    menus = ChatCompletion.Model.values().map { it.getName() },
                    valueMapper = { it },
                    onMenuClick = {
                        model = it
                        AppInfo.runtimeProps.paramMap[AssistantActionHandler.OPEN_MODEL_KEY] = it
                    },
                )

                var temperature: Float by remember { mutableStateOf(AssistantActionHandler.openaiTemperature) }
                SettingSlider(
                    label = LanguageBundle.getBy(Constants.PLUGIN_ID, "chatgpt-temperature"),
                    labelWidth = 120,
                    value = temperature,
                    valueRange = 0f..2f,
                    preContent = {
                        SettingLabelText("%.2f".format(temperature), null)
                    },
                    update = {
                        temperature = it
                        AppInfo.runtimeProps.paramMap[AssistantActionHandler.OPEN_TEMPERATURE_KEY] = it
                    },
                )
            }
        }
}