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

    const val PROVIDER_KEY = "$PLUGIN_ID.Provider"

    const val OPEN_API_KEY = "$PLUGIN_ID.openai.ApiKey"

    const val OPEN_TEMPERATURE_KEY = "$PLUGIN_ID.chatgpt.Temperature"

    const val OPEN_MODEL_KEY = "$PLUGIN_ID.chatgpt.Model"

    const val OPENAI_PROVIDER = "OpenAI"

    const val SPARK_PROVIDER = "Spark"

    const val SPARK_APP_ID_KEY = "$PLUGIN_ID.spark.AppId"

    const val SPARK_API_SECRET_KEY = "$PLUGIN_ID.spark.ApiSecret"

    const val SPARK_API_KEY = "$PLUGIN_ID.spark.ApiKey"

    const val SPARK_TEMPERATURE_KEY = "$PLUGIN_ID.spark.Temperature"

    const val SPARK_TRANSLATION_SOURCE_LANGUAGE_KEY = "spark.translation.SourceLanguage"

    const val SPARK_TRANSLATION_TARGET_LANGUAGE_KEY = "spark.translation.TargetLanguage"

    val provider: String get() = AppInfo.runtimeProps.getParam(PROVIDER_KEY, OPENAI_PROVIDER)

    val openaiApiKey: String get() = AppInfo.runtimeProps.getParam(OPEN_API_KEY, "")

    val openaiTemperature: Float get() = AppInfo.runtimeProps.getParam(OPEN_TEMPERATURE_KEY, 0.6f)

    val openaiModel: String get() = AppInfo.runtimeProps.getParam(OPEN_MODEL_KEY, ChatCompletion.Model.GPT_3_5_TURBO.getName())

    val sparkAppId: String get() = AppInfo.runtimeProps.getParam(SPARK_APP_ID_KEY, "")

    val sparkApiSecret: String get() = AppInfo.runtimeProps.getParam(SPARK_API_SECRET_KEY, "")

    val sparkApiKey: String get() = AppInfo.runtimeProps.getParam(SPARK_API_KEY, "")

    val sparkTemperature: Float get() = AppInfo.runtimeProps.getParam(SPARK_TEMPERATURE_KEY, 0.3f)

    val sparkTranslationSourceLanguage: String get() = AppInfo.runtimeProps.getParam(SPARK_TRANSLATION_SOURCE_LANGUAGE_KEY, SparkLanguageType.EN_US.name)

    val sparkTranslationTargetLanguage: String get() = AppInfo.runtimeProps.getParam(SPARK_TRANSLATION_TARGET_LANGUAGE_KEY, SparkLanguageType.ZH_CN.name)
}