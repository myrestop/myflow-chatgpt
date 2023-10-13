package top.myrest.myflow.ai

import top.myrest.myflow.component.Composes

internal object Constants {

    const val PLUGIN_ID = "top.myrest.myflow.ai"

    val chatgptLogo = Composes.resolveLogo(PLUGIN_ID, AssistantActionHandler::class.java.name, "./logos/chatgpt.png").first

    val userLogo = Composes.resolveLogo(PLUGIN_ID, AssistantActionHandler::class.java.name, "./logos/user.png").first
}