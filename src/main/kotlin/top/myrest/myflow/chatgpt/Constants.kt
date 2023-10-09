package top.myrest.myflow.chatgpt

import top.myrest.myflow.component.Composes

internal object Constants {

    const val PLUGIN_ID = "top.myrest.myflow.chatgpt"

    val chatGptLogo = Composes.resolveLogo(PLUGIN_ID, ChatGptActionHandler::class.java.name, "./logos/chatgpt.png").first

    val userLogo = Composes.resolveLogo(PLUGIN_ID, ChatGptActionHandler::class.java.name, "./logos/user.png").first
}