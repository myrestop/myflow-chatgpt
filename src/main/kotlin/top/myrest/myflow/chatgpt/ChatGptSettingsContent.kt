package top.myrest.myflow.chatgpt

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import top.myrest.myflow.component.FuncPageScope
import top.myrest.myflow.component.SettingsContent

internal class ChatGptSettingsContent : SettingsContent {
    override val settingsContent: @Composable FuncPageScope.(pluginId: String) -> Unit
        get() = {
            Text(text = "test")
        }
}