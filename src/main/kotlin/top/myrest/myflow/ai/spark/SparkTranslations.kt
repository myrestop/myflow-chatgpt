package top.myrest.myflow.ai.spark

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import cn.hutool.core.codec.Base64
import cn.hutool.core.date.DateUtil
import cn.hutool.core.net.url.UrlBuilder
import cn.hutool.core.net.url.UrlPath
import cn.hutool.crypto.digest.DigestAlgorithm
import io.ktor.utils.io.core.toByteArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import top.myrest.myflow.ai.Constants
import top.myrest.myflow.enumeration.LanguageType
import top.myrest.myflow.language.Translator
import top.myrest.myflow.util.Jackson
import top.myrest.myflow.util.Jackson.toJsonString

internal enum class SparkLanguageType(val code: String, val type: LanguageType) {
    ZH_CN("cn", LanguageType.ZH_CN),
    ZH_TW("cht", LanguageType.ZH_TW),
    HU_HU("hu", LanguageType.HU_HU),
    EN_US("en", LanguageType.EN_US),
    EN_UK("en", LanguageType.EN_UK),
    JA_JP("ja", LanguageType.JA_JP),
    ID_ID("id", LanguageType.ID_ID),
    KO_KR("ko", LanguageType.KO_KR),
    RU_RU("ru", LanguageType.RU_RU),
    IS_IS("is", LanguageType.IS_IS),
    FR_FR("fr", LanguageType.FR_FR),
    IT_IT("it", LanguageType.IT_IT),
    ES_ES("es", LanguageType.ES_ES),
    SK_SK("sk", LanguageType.SK_SK),
    PT_PT("pt", LanguageType.PT_PT),
    CS_CZ("cs", LanguageType.CS_CZ),
    DA_DK("da", LanguageType.DA_DK),
    DE_DE("de", LanguageType.DE_DE),
    TR_TR("tr", LanguageType.TR_TR),
    EL_GR("el", LanguageType.EL_GR),
    MN_MN("mn", LanguageType.MN_MN),
    FI_FI("fi", LanguageType.FI_FI),
    UK_UA("uk", LanguageType.UK_UA),
    NL_NL("nl", LanguageType.NL_NL),
    VI_VN("vi", LanguageType.VI_VN),
    NB_BO("no", LanguageType.NB_NO),
    PL_PL("pl", LanguageType.PL_PL),
}

class SparkTranslator : Translator {

    private val types = SparkLanguageType.values().associateBy { it.type }

    override fun getSupportLanguages(): List<LanguageType> = types.keys.toList()

    override fun translate(text: String, sourceLanguage: LanguageType, targetLanguage: LanguageType): String {
        val sourceType = types[sourceLanguage]
        val targetType = types[targetLanguage]
        if (sourceType == null || targetType == null) {
            return text
        }
        return SparkTranslations.translate(text, sourceType, targetType)
    }
}

internal object SparkTranslations {

    private val log = LoggerFactory.getLogger(SparkTranslations::class.java)

    private val urlBuilder = UrlBuilder().setScheme("https").setHost("ntrans.xfyun.cn").setPath(UrlPath().add("/v2").add("/ots"))

    private var dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    init {
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
    }

    fun translate(text: String): String {
        val sourceType = SparkLanguageType.valueOf(Constants.sparkTranslationSourceLanguage)
        val targetType = SparkLanguageType.valueOf(Constants.sparkTranslationTargetLanguage)
        return translate(text, sourceType, targetType)
    }

    fun translate(text: String, sourceType: SparkLanguageType, targetType: SparkLanguageType): String {
        if (Constants.sparkAppId.isBlank() || Constants.sparkApiSecret.isBlank() || Constants.sparkApiKey.isBlank() || sourceType == targetType) {
            return text
        }

        val body = buildBody(text, sourceType, targetType)
        val headers = buildHeaders(urlBuilder, body)
        val request = Request.Builder().url(urlBuilder.toURL()).post(body.toRequestBody(jsonType))
        headers.forEach { (k, v) ->
            request.addHeader(k, v)
        }

        val response = SparkStreamResults.client.okHttpClient.newCall(request.build()).execute()
        val responseBody = response.body?.string()
        if (!response.isSuccessful) {
            log.error("get translate error: {}", response.toString())
            return ""
        }

        log.info("response body: {}", responseBody)
        val node = Jackson.jsonMapper.readTree(responseBody)
        return node.at("/data/result/trans_result/dst").asText()
    }

    private fun buildBody(text: String, sourceType: SparkLanguageType, targetType: SparkLanguageType): String {
        return mapOf(
            "common" to mapOf("app_id" to Constants.sparkAppId),
            "business" to mapOf("from" to sourceType.code, "to" to targetType.code),
            "data" to mapOf("text" to Base64.encode(text)),
        ).toJsonString()
    }

    private fun buildHeaders(urlBuilder: UrlBuilder, body: String): Map<String, String> {
        val date = DateUtil.format(Date(), dateFormat)
        val digest = "SHA-256=" + signBody(body)

        var content = "host: " + urlBuilder.host + "\n"
        content += "date: $date\n"
        content += "POST " + urlBuilder.path + " HTTP/1.1\n"
        content += "digest: $digest"
        val sign = hmacSign(content)
        val authorization = "api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"".format(Constants.sparkApiKey, "hmac-sha256", "host date request-line digest", sign)

        return mapOf(
            "Authorization" to authorization,
            "Content-Type" to "application/json",
            "Accept" to "application/json,version=1.0",
            "Host" to urlBuilder.host,
            "Date" to date,
            "Digest" to digest,
        )
    }

    private fun hmacSign(content: String): String {
        val mac = Mac.getInstance("hmacsha256")
        val spec = SecretKeySpec(Constants.sparkApiSecret.toByteArray(Charsets.UTF_8), "hmacsha256")
        mac.init(spec)
        val bytes = mac.doFinal(content.toByteArray(Charsets.UTF_8))
        return Base64.encode(bytes)
    }

    private fun signBody(body: String): String {
        val digest = MessageDigest.getInstance(DigestAlgorithm.SHA256.value)
        digest.update(body.toByteArray(Charsets.UTF_8))
        return Base64.encode(digest.digest())
    }
}