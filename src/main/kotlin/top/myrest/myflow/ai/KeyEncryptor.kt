package top.myrest.myflow.ai

import top.myrest.myflow.data.DataEncryptor

internal object KeyEncryptor : DataEncryptor {

    private val chars: List<Char> = "MNBVCXZASDFGHJKLPOIUYTREWQzaq12wsxcde34rfvbgt56yhnmju78iklo90p".toList()

    override fun getAlgorithmName(): String = "ai key encryptor"

    override fun decrypt(content: String): String {
        if (content.isBlank()) {
            return content
        }

        val sb = StringBuilder()
        content.forEachIndexed { i, it ->
            val idx: Int = if (it.isDigit()) {
                it - '0'
            } else if (it.isLowerCase()) {
                it - 'a' + 10
            } else if (it.isUpperCase()) {
                it - 'A' + 36
            } else -1
            if (idx < 0) {
                sb.append(it)
            } else {
                sb.append(chars[(idx + i) % chars.size])
            }
        }
        return sb.toString()
    }

    override fun encrypt(content: String): String {
        if (content.isBlank()) {
            return content
        }

        val sb = StringBuilder()
        val charIdxMap = mutableMapOf<Char, Int>()
        chars.forEachIndexed { i, c ->
            charIdxMap[c] = i
        }
        content.forEachIndexed { i, c ->
            val idx = charIdxMap[c]
            if (idx == null) {
                sb.append(c)
            } else {
                val oldIdx = (chars.size + idx - i) % chars.size
                val char: Char = if (oldIdx >= 36) {
                    'A' + oldIdx - 36
                } else if (oldIdx >= 10) {
                    'a' + oldIdx - 10
                } else '0' + oldIdx
                sb.append(char)
            }
        }
        return sb.toString()
    }
}


internal fun String.encrypt() = KeyEncryptor.encrypt(this)

internal fun String.decrypt() = KeyEncryptor.decrypt(this)
