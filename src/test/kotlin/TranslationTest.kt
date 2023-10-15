import top.myrest.myflow.ai.spark.SparkTranslations
import top.myrest.myflow.baseimpl.enableDevEnv
import top.myrest.myflow.config.ConfigProps

fun main() {
    enableDevEnv()
    ConfigProps.load()
    println(SparkTranslations.translate("中国"))
}