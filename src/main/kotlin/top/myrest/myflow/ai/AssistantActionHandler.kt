package top.myrest.myflow.ai

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
import top.myrest.myflow.ai.openai.ChatgptStreamResults
import top.myrest.myflow.component.ActionKeywordPin
import top.myrest.myflow.component.Composes
import top.myrest.myflow.component.SettingsContent
import top.myrest.myflow.enumeration.ActionWindowBehavior
import top.myrest.myflow.language.LanguageBundle
import top.myrest.myflow.util.singleList

class AssistantActionHandler : ActionFocusedKeywordHandler() {

    override fun getCustomizeSettingContent(): SettingsContent {
        return AssistantSettingsContent()
    }

    override fun enterFocusMode(pin: ActionKeywordPin): ActionFocusedSession {
        return AssistantFocusedSession(pin)
    }

    companion object {

        const val OPEN_API_KEY = Constants.PLUGIN_ID + ".openai.ApiKey"

        const val OPEN_TEMPERATURE_KEY = Constants.PLUGIN_ID + ".chatgpt.Temperature"

        const val OPEN_MODEL_KEY = Constants.PLUGIN_ID + ".chatgpt.Model"

        val openaiApiKey: String get() = AppInfo.runtimeProps.getParam(OPEN_API_KEY, "")

        val openaiTemperature: Float get() = AppInfo.runtimeProps.getParam(OPEN_TEMPERATURE_KEY, 0.6f)

        val openaiModel: String get() = AppInfo.runtimeProps.getParam(OPEN_MODEL_KEY, ChatCompletion.Model.GPT_3_5_TURBO.getName())
    }
}

internal class AssistantFocusedSession(pin: ActionKeywordPin) : ActionFocusedSession(pin) {

    internal val results = AtomicReference(emptyList<ActionResult>())

    private val sendMessageTip = AppInfo.currLanguageBundle.shared.send + AppInfo.currLanguageBundle.wordSep + AppInfo.currLanguageBundle.shared.message

    var chatHistoryWindow: ChatHistoryWindow? = ChatHistoryWindow(this, pin)

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
        if (AssistantActionHandler.openaiApiKey.isBlank()) {
            return LanguageBundle.getBy(Constants.PLUGIN_ID, "input-openai-api-key")
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
        if (AssistantActionHandler.openaiApiKey.isBlank()) {
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

        val modelList = mutableListOf(AssistantActionHandler.openaiModel)
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
                                prepareChat()
                                Composes.actionWindowProvider?.updateActionResultList(pin, ChatgptStreamResults.getStreamChatResult(this, it, model).singleList())
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
                                prepareChat()
                                Composes.actionWindowProvider?.updateActionResultList(pin, ChatgptStreamResults.getGenerateImageResult(this, it))
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
                                    prepareChat()
                                    Composes.actionWindowProvider?.updateActionResultList(pin, ChatgptStreamResults.getVariationImageResult(this, it))
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
    private fun prepareChat() {
        Composes.actionWindowProvider?.setAction(pin, "", false)
    }

    private fun setApiKeyResult(action: String) = ActionResult(
        actionId = "",
        title = listOf(action.plain),
        subtitle = LanguageBundle.getBy(Constants.PLUGIN_ID, "set-openai-api-key"),
        result = action,
        callbacks = singleCallback(
            actionWindowBehavior = ActionWindowBehavior.EMPTY_LIST,
        ) {
            if (it is String && it.startsWith("sk-")) {
                AppInfo.runtimeProps.paramMap[AssistantActionHandler.OPEN_API_KEY] = it
            }
        },
    )
}