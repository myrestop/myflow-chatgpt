package top.myrest.myflow.ai

import com.unfbx.chatgpt.entity.chat.ChatCompletion
import top.myrest.myflow.AppInfo
import top.myrest.myflow.ai.spark.SparkLanguageType
import top.myrest.myflow.component.Composes

internal object Constants {

    const val PLUGIN_ID = "top.myrest.myflow.ai"

    val chatgptLogo = Composes.resolveLogo(PLUGIN_ID, null, "./logos/chatgpt.png").first

    val userLogo = Composes.resolveLogo(PLUGIN_ID, null, "./logos/user.png").first

    val robotLogo = Composes.resolveLogo(PLUGIN_ID, null, "./logos/robot.png").first

    val sparkLogo = Composes.resolveLogo(PLUGIN_ID, null, "./logos/spark.png").first

    const val OPENAI_PROVIDER = "OpenAI"

    const val SPARK_PROVIDER = "Spark"

    private const val PROVIDER_KEY = "$PLUGIN_ID.Provider"

    private const val OPEN_API_KEY = "$PLUGIN_ID.openai.ApiKey"

    private const val OPEN_TEMPERATURE_KEY = "$PLUGIN_ID.chatgpt.Temperature"

    private const val OPEN_MODEL_KEY = "$PLUGIN_ID.chatgpt.Model"

    private const val SPARK_APP_ID_KEY = "$PLUGIN_ID.spark.AppId"

    private const val SPARK_API_SECRET_KEY = "$PLUGIN_ID.spark.ApiSecret"

    private const val SPARK_API_KEY = "$PLUGIN_ID.spark.ApiKey"

    private const val SPARK_TEMPERATURE_KEY = "$PLUGIN_ID.spark.Temperature"

    private const val SPARK_TRANSLATION_SOURCE_LANGUAGE_KEY = "spark.translation.SourceLanguage"

    private const val SPARK_TRANSLATION_TARGET_LANGUAGE_KEY = "spark.translation.TargetLanguage"

    var provider: String = AppInfo.runtimeProps.getParam(PROVIDER_KEY, OPENAI_PROVIDER)
        set(value) {
            field = value
            AppInfo.runtimeProps.paramMap[PROVIDER_KEY] = value
        }

    var openaiApiKey: String = AppInfo.runtimeProps.getParam(OPEN_API_KEY, "").decrypt()
        set(value) {
            field = value
            AppInfo.runtimeProps.paramMap[OPEN_API_KEY] = value.encrypt()
        }

    var openaiTemperature: Float = AppInfo.runtimeProps.getParam(OPEN_TEMPERATURE_KEY, 0.6f)
        set(value) {
            field = value
            AppInfo.runtimeProps.paramMap[OPEN_TEMPERATURE_KEY] = value
        }

    var openaiModel: String = AppInfo.runtimeProps.getParam(OPEN_MODEL_KEY, ChatCompletion.Model.GPT_3_5_TURBO.getName())
        set(value) {
            field = value
            AppInfo.runtimeProps.paramMap[OPEN_MODEL_KEY] = value
        }

    var sparkAppId: String = AppInfo.runtimeProps.getParam(SPARK_APP_ID_KEY, "").decrypt()
        set(value) {
            field = value
            AppInfo.runtimeProps.paramMap[SPARK_APP_ID_KEY] = value.encrypt()
        }

    var sparkApiSecret: String = AppInfo.runtimeProps.getParam(SPARK_API_SECRET_KEY, "").decrypt()
        set(value) {
            field = value
            AppInfo.runtimeProps.paramMap[SPARK_API_SECRET_KEY] = value.encrypt()
        }

    var sparkApiKey: String = AppInfo.runtimeProps.getParam(SPARK_API_KEY, "").decrypt()
        set(value) {
            field = value
            AppInfo.runtimeProps.paramMap[SPARK_API_KEY] = value.encrypt()
        }


    var sparkTemperature: Float = AppInfo.runtimeProps.getParam(SPARK_TEMPERATURE_KEY, 0.5f)
        set(value) {
            field = value
            AppInfo.runtimeProps.paramMap[SPARK_TEMPERATURE_KEY] = value
        }

    var sparkTranslationSourceLanguage: String = AppInfo.runtimeProps.getParam(SPARK_TRANSLATION_SOURCE_LANGUAGE_KEY, SparkLanguageType.EN_US.name)
        set(value) {
            field = value
            AppInfo.runtimeProps.paramMap[SPARK_TRANSLATION_SOURCE_LANGUAGE_KEY] = value
        }

    var sparkTranslationTargetLanguage: String = AppInfo.runtimeProps.getParam(SPARK_TRANSLATION_TARGET_LANGUAGE_KEY, SparkLanguageType.ZH_CN.name)
        set(value) {
            field = value
            AppInfo.runtimeProps.paramMap[SPARK_TRANSLATION_TARGET_LANGUAGE_KEY] = value
        }
}