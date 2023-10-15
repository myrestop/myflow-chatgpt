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
import top.myrest.myflow.ai.Constants
import top.myrest.myflow.util.Jackson.toJsonString


internal object SparkTranslations {

    private val urlBuilder = UrlBuilder().setScheme("https").setHost("ntrans.xfyun.cn").setPath(UrlPath().add("/v2").add("/ots"))

    private var dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    init {
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
    }

    fun translate(text: String): String {
        if (Constants.sparkAppId.isBlank() || Constants.sparkApiSecret.isBlank() || Constants.sparkApiKey.isBlank()) {
            return ""
        }

        val body = buildBody(text)
        val headers = buildHeaders(urlBuilder, body)
        val request = Request.Builder().url(urlBuilder.toURL()).post(body.toRequestBody(jsonType))
        headers.forEach { (k, v) ->
            request.addHeader(k, v)
        }
        val response = SparkStreamResults.client.okHttpClient.newCall(request.build()).execute()
        println(response.body?.string())
        return ""
    }

    private fun buildBody(text: String): String {
        return mapOf(
            "common" to mapOf("app_id" to Constants.sparkAppId),
            "business" to mapOf("from" to "cn", "to" to "en"),
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