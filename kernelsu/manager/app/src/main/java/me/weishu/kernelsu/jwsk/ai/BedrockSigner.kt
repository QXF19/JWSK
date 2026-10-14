package me.weishu.kernelsu.jwsk.ai

import okhttp3.Request
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** SigV4 for the narrowly scoped Bedrock Converse POST; no discovery/instance credentials. */
internal object BedrockSigner {
    private val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun sign(request: Request, payload: ByteArray, region: String, credentials: AiCredentials, now: Instant): Request {
        require(request.method == "POST" && request.url.encodedQuery == null)
        val date = timestamp.format(now)
        val day = date.take(8)
        val hash = sha256(payload)
        val host = request.url.host + if (request.url.port != 443) ":${request.url.port}" else ""
        val headers = sortedMapOf(
            "content-type" to "application/json; charset=utf-8", "host" to host,
            "x-amz-content-sha256" to hash, "x-amz-date" to date,
        )
        if (credentials.sessionToken.isNotBlank()) headers["x-amz-security-token"] = credentials.sessionToken
        val canonicalHeaders = headers.entries.joinToString("") { "${it.key}:${it.value.trim().replace(Regex("\\s+"), " ")}\n" }
        val signedHeaders = headers.keys.joinToString(";")
        // Standard non-S3 SigV4 double-encodes existing percent escapes in the path.
        val path = request.url.encodedPath.split('/').joinToString("/") { encode(it) }
        val canonical = "POST\n$path\n\n$canonicalHeaders\n$signedHeaders\n$hash"
        val scope = "$day/$region/bedrock/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$date\n$scope\n${sha256(canonical.toByteArray())}"
        var key = hmac(("AWS4" + credentials.secretKey).toByteArray(), day)
        key = hmac(key, region)
        key = hmac(key, "bedrock")
        key = hmac(key, "aws4_request")
        val signature = hex(hmac(key, stringToSign))
        key.fill(0)
        return request.newBuilder().apply {
            headers.forEach { (name, value) -> header(name, value) }
            header("Authorization", "AWS4-HMAC-SHA256 Credential=${credentials.accessKey}/$scope, SignedHeaders=$signedHeaders, Signature=$signature")
        }.build()
    }

    private fun encode(value: String) = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val n = byte.toInt() and 255
            if (n in 65..90 || n in 97..122 || n in 48..57 || n == 45 || n == 46 || n == 95 || n == 126) append(n.toChar())
            else { append('%'); append("0123456789ABCDEF"[n shr 4]); append("0123456789ABCDEF"[n and 15]) }
        }
    }
    private fun hmac(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(value.toByteArray(Charsets.UTF_8))
    }
    private fun sha256(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
