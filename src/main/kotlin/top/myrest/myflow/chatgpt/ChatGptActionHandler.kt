package top.myrest.myflow.chatgpt

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import cn.hutool.core.io.FileUtil
import com.unfbx.chatgpt.entity.chat.ChatCompletion
import top.myrest.myflow.AppInfo
import top.myrest.myflow.action.ActionFocusedKeywordHandler
import top.myrest.myflow.action.ActionFocusedSession
import top.myrest.myflow.action.ActionResult
import top.myrest.myflow.action.ActionResultCallback
import top.myrest.myflow.action.plain
import top.myrest.myflow.action.singleCallback
import top.myrest.myflow.component.ActionKeywordPin
import top.myrest.myflow.component.Composes
import top.myrest.myflow.component.SettingsContent
import top.myrest.myflow.enumeration.ActionWindowBehavior
import top.myrest.myflow.language.LanguageBundle
import top.myrest.myflow.util.singleList

class ChatGptActionHandler : ActionFocusedKeywordHandler() {

    override fun getThisHandler(): ActionFocusedKeywordHandler = this

    override fun getCustomizeSettingContent(): SettingsContent {
        return ChatGptSettingsContent()
    }

    override fun enterFocusMode(pin: ActionKeywordPin): ActionFocusedSession {
        return ChatGptFocusedSession(pin)
    }

    companion object {

        const val API_KEY = Constants.PLUGIN_ID + ".ApiKey"

        const val TEMPERATURE_KEY = Constants.PLUGIN_ID + ".Temperature"

        const val MODEL_KEY = Constants.PLUGIN_ID + ".Model"

        var apiKey: String = AppInfo.runtimeProps.getParam(API_KEY, "")

        var temperature: Double = AppInfo.runtimeProps.getParam(TEMPERATURE_KEY, 0.6)

        var model: String = AppInfo.runtimeProps.getParam(MODEL_KEY, ChatCompletion.Model.GPT_3_5_TURBO.getName())
    }
}

internal class ChatGptFocusedSession(pin: ActionKeywordPin) : ActionFocusedSession(pin) {

    internal val results = AtomicReference(emptyList<ActionResult>())

    private val flag = AtomicReference("")

    private val sendMessageTip = AppInfo.currLanguageBundle.shared.send + AppInfo.currLanguageBundle.wordSep + AppInfo.currLanguageBundle.shared.message

    private var chatHistoryWindow: ChatHistoryWindow? = ChatHistoryWindow(this, pin)

    private val inactive = AtomicBoolean(true)

    init {
        SwingUtilities.invokeLater {
            chatHistoryWindow?.attach()
            Composes.actionWindowProvider?.setAction(pin, "", false)
            inactive.set(false)
        }
    }

    override fun exitFocusMode() {
        chatHistoryWindow?.dispose()
        chatHistoryWindow = null
    }

    override fun getWorkDir(): File = FileUtil.getUserHomeDir()

    override fun getLabel(): String {
        if (ChatGptActionHandler.apiKey.isBlank()) {
            return LanguageBundle.getBy(Constants.PLUGIN_ID, "input-api-key")
        }
        return sendMessageTip
    }

    override fun queryAction(action: String): List<ActionResult> {
        if (inactive.get()) {
            return emptyList()
        }
        if (action.isBlank()) {
            return results.get()
        }
        if (ChatGptActionHandler.apiKey.isBlank()) {
            return setApiKeyResult(action).singleList()
        }

        return messageResults(action)
    }

    private fun messageResults(action: String): List<ActionResult> {
        val defaultResult = ActionResult(
            actionId = "",
            title = listOf(action.plain),
            result = action,
            callbacks = singleCallback(
                showNotify = false,
                actionWindowBehavior = ActionWindowBehavior.NOTHING,
            ),
        )

        val list = mutableListOf<ActionResult>()
        val (offset, files) = getSuggestFiles(action)
        files.forEach {
            list.add(
                it.mapFile(offset).copy(
                    callbacks = singleCallback(
                        showNotify = false,
                        actionWindowBehavior = ActionWindowBehavior.NOTHING,
                    ) { f ->
                        if (f is File) {
                            Composes.actionWindowProvider?.setAction(pin, it.canonicalPath, true)
                        }
                    },
                )
            )
        }

        val modelList = mutableListOf(ChatGptActionHandler.model)
        modelList.addAll(ChatCompletion.Model.values().map { it.getName() })
        list.add(
            defaultResult.copy(
                subtitle = "ChatGPT",
                callbacks = modelList.distinct().map { model ->
                    ActionResultCallback(
                        label = model,
                        showNotify = false,
                        actionWindowBehavior = ActionWindowBehavior.NOTHING,
                        actionCallback = {
                            if (it is String) {
                                assignFlag("chat")
                                Composes.actionWindowProvider?.updateActionResultList(pin, ChatGptStreamResults.getStreamChatResult(this, it, model).singleList())
                            }
                        },
                    )
                },
            )
        )
        list.add(
            defaultResult.copy(
                subtitle = AppInfo.currLanguageBundle.shared.generate + AppInfo.currLanguageBundle.wordSep + AppInfo.currLanguageBundle.shared.image,
                callbacks = defaultResult.callbacks.map { callback ->
                    callback.copy(
                        actionCallback = {
                            if (it is String) {
                                assignFlag("image")
                                Composes.actionWindowProvider?.updateActionResultList(pin, ChatGptStreamResults.getGenerateImageResult(this, it))
                            }
                        },
                    )
                },
            )
        )

        var userFile: File? = try {
            val file = File(action)
            if (file.exists()) file else null
        } catch (e: Exception) {
            null
        }
        var file: File? = null
        var hasImage = false
        for (result in results.get()) {
            val finalResult = result.result
            if (finalResult is ChatHistoryDoc) {
                if (finalResult.type == ContentType.FILE) {
                    hasImage = true
                    file = File(finalResult.value)
                    break
                } else if (finalResult.type == ContentType.IMAGES) {
                    hasImage = true
                    break
                }
            }
        }

//        if (hasImage) {
//            list.add(
//                defaultResult.copy(
//                    title = listOf((file?.canonicalPath ?: action).plain),
//                    subtitle = AppInfo.currLanguageBundle.shared.modify + AppInfo.currLanguageBundle.wordSep + AppInfo.currLanguageBundle.shared.image,
//                    callbacks = defaultResult.callbacks.map { callback ->
//                        callback.copy(
//                            actionCallback = {
//                                if (it is String) {
//                                    assignFlag("image")
//                                    Composes.actionWindowProvider?.updateActionResultList(pin, ChatGptStreamResults.getModifyImageResult(this, it))
//                                }
//                            },
//                        )
//                    },
//                )
//            )
//        }

        userFile = userFile ?: file
        if (userFile != null && userFile.isFile) {
            list.add(
                defaultResult.copy(
                    title = listOf(userFile.canonicalPath.plain),
                    result = userFile,
                    subtitle = "OpenAI" + AppInfo.currLanguageBundle.wordSep + AppInfo.currLanguageBundle.shared.image + AppInfo.currLanguageBundle.wordSep + AppInfo.currLanguageBundle.shared.variation,
                    callbacks = defaultResult.callbacks.map { callback ->
                        callback.copy(
                            actionCallback = {
                                if (it is File) {
                                    assignFlag("image")
                                    Composes.actionWindowProvider?.updateActionResultList(pin, ChatGptStreamResults.getVariationImageResult(this, it))
                                }
                            },
                        )
                    },
                )
            )
        }

        return list
    }

    /**
     * 标记是否重新开启会话
     */
    private fun assignFlag(flag: String) {
        Composes.actionWindowProvider?.setAction(pin, "", false)
        if (flag.isEmpty()) {
            return
        }

        val preFlag = this.flag.get()
        this.flag.set(flag)
        if (preFlag.isEmpty()) {
            return
        }

        if (preFlag != flag) {
            // 开启新会话
            results.set(emptyList())
        }
    }

    private fun setApiKeyResult(action: String) = ActionResult(
        actionId = "",
        title = listOf(action.plain),
        subtitle = LanguageBundle.getBy(Constants.PLUGIN_ID, "set-api-key"),
        result = action,
        callbacks = singleCallback(
            actionWindowBehavior = ActionWindowBehavior.EMPTY_LIST,
        ) {
            if (it is String && it.startsWith("sk-")) {
                AppInfo.runtimeProps.paramMap[ChatGptActionHandler.API_KEY] = it
                ChatGptActionHandler.apiKey = it
            }
        },
    )
}