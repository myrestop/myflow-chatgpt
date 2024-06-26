package top.myrest.myflow.ai

import java.io.File
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import cn.hutool.core.io.FileUtil
import com.unfbx.chatgpt.entity.chat.BaseChatCompletion.Model
import org.slf4j.LoggerFactory
import top.myrest.myflow.AppInfo
import top.myrest.myflow.action.ActionFocusedSession
import top.myrest.myflow.action.ActionResult
import top.myrest.myflow.action.ActionResultCallback
import top.myrest.myflow.action.plain
import top.myrest.myflow.action.singleCallback
import top.myrest.myflow.ai.openai.ChatgptStreamResults
import top.myrest.myflow.ai.spark.SparkStreamResults
import top.myrest.myflow.component.ActionKeywordPin
import top.myrest.myflow.component.Composes
import top.myrest.myflow.enumeration.ActionWindowBehavior
import top.myrest.myflow.language.LanguageBundle
import top.myrest.myflow.util.singleList

internal class AssistantFocusedSession(pin: ActionKeywordPin) : ActionFocusedSession(pin) {

    private val log = LoggerFactory.getLogger(AssistantFocusedSession::class.java)

    internal val results = AtomicReference(emptyList<ActionResult>())

    private val sendMessageTip = AppInfo.currLanguageBundle.shared.send + AppInfo.currLanguageBundle.wordSep + AppInfo.currLanguageBundle.shared.message

    var chatHistoryWindow: ChatHistoryWindow? = ChatHistoryWindow(this, pin)

    private val activeAt = System.currentTimeMillis() + Duration.ofSeconds(1).toMillis()

    private val providers = mapOf<String, (MutableList<ActionResult>, ActionResult, String) -> Unit>(
        Constants.OPENAI_PROVIDER to { list, result, action -> addOpenaiResults(list, result, action) },
        Constants.SPARK_PROVIDER to { list, result, action -> addSparkResults(list, result, action) },
    )

    init {
        log.info("enter ai assistant focused mode: {}", pin.getPinId())
        SwingUtilities.invokeLater {
            chatHistoryWindow?.attach()
        }
    }

    override fun exitFocusMode() {
        chatHistoryWindow?.dispose()
        chatHistoryWindow = null
        log.info("exit ai assistant focused mode: {}", pin.getPinId())
    }

    override fun getWorkDir(): File = FileUtil.getUserHomeDir()

    override fun getLabel(): String {
        if (Constants.openaiApiKey.isBlank()) {
            return LanguageBundle.getBy(Constants.PLUGIN_ID, "input-openai-api-key")
        }
        return sendMessageTip
    }

    override fun queryAction(action: String): List<ActionResult> {
        if (System.currentTimeMillis() < activeAt) {
            Composes.actionWindowProvider?.setAction(pin, "")
            return emptyList()
        }
        if (action.isBlank()) {
            return results.get()
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
                label = AppInfo.currLanguageBundle.shared.send,
                actionWindowBehavior = ActionWindowBehavior.NOTHING,
            ),
        )

        val list = mutableListOf<ActionResult>()
        list.addAll(getHistories(action, 10))
        addSuggestFileResults(action, list)

        val provider = Constants.provider
        providers[provider]?.invoke(list, defaultResult, action)
        providers.forEach { (k, v) ->
            if (k != provider) {
                v(list, defaultResult, action)
            }
        }
        return list
    }

    private fun addSparkResults(list: MutableList<ActionResult>, defaultResult: ActionResult, action: String) {
        if (Constants.sparkAppId.isBlank() || Constants.sparkApiSecret.isBlank() || Constants.sparkApiKey.isBlank()) {
            return
        }
        list.add(
            defaultResult.copy(
                logo = Constants.sparkLogo,
                subtitle = LanguageBundle.getBy(Constants.PLUGIN_ID, "spark-desk"),
                callbacks = singleCallback(
                    showNotify = false,
                    label = AppInfo.currLanguageBundle.shared.send,
                    actionWindowBehavior = ActionWindowBehavior.NOTHING,
                    actionCallback = {
                        if (it is String) {
                            prepareChat()
                            Composes.actionWindowProvider?.updateActionResultList(pin, SparkStreamResults.getStreamChatResult(this, it).singleList())
                        }
                    },
                ),
            )
        )
    }

    private fun addOpenaiResults(list: MutableList<ActionResult>, defaultResult: ActionResult, action: String) {
        if (Constants.openaiApiKey.isBlank()) {
            list.add(
                ActionResult(
                    actionId = "",
                    logo = Constants.chatgptLogo,
                    title = listOf(action.plain),
                    subtitle = LanguageBundle.getBy(Constants.PLUGIN_ID, "set-openai-api-key"),
                    result = action,
                    callbacks = singleCallback(
                        label = AppInfo.currLanguageBundle.shared.activate,
                        actionWindowBehavior = ActionWindowBehavior.EMPTY_LIST,
                    ) {
                        if (it is String && it.startsWith("sk-")) {
                            Constants.openaiApiKey = it
                        }
                    },
                )
            )
            return
        }
        val modelList = mutableListOf(Constants.openaiModel)
        modelList.addAll(Model.entries.map { it.getName() })
        list.add(
            defaultResult.copy(
                logo = Constants.chatgptLogo,
                subtitle = "OpenAI ChatGPT",
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
                logo = Constants.chatgptLogo,
                subtitle = "OpenAI" + AppInfo.currLanguageBundle.wordSep + AppInfo.currLanguageBundle.shared.image + AppInfo.currLanguageBundle.wordSep + AppInfo.currLanguageBundle.shared.generator,
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
                    logo = Constants.chatgptLogo,
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
    }

    private fun addSuggestFileResults(action: String, list: MutableList<ActionResult>) {
        val (offset, files) = getSuggestFiles(action)
        files.forEach {
            list.add(
                it.mapFile(offset).copy(
                    callbacks = singleCallback(
                        showNotify = false,
                        label = AppInfo.currLanguageBundle.shared.pick,
                        actionWindowBehavior = ActionWindowBehavior.NOTHING,
                    ) { f ->
                        if (f is File) {
                            Composes.actionWindowProvider?.setAction(pin, it.canonicalPath, true)
                        }
                    },
                )
            )
        }
    }

    private fun prepareChat() {
        Composes.actionWindowProvider?.setAction(pin, "", false)
    }
}
