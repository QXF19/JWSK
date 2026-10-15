package me.weishu.kernelsu.jwsk.alpha

import android.content.Context
import android.os.Process

internal data class AlphaPolicy(val uid: Int, val policy: Int, val until: Long,
    val logging: Boolean, val notification: Boolean) {
    val granted: Boolean get() = policy >= 2 && (until == 0L || until > System.currentTimeMillis() / 1000)
}

/** SQL values are bounded numeric types; shell arguments always remain quoted. */
internal class AlphaPolicyStore(context: Context) {
    private val client = AlphaClient(context)
    suspend fun sql(query: String): List<Map<String, String>> {
        val result = client.root("magisk --sqlite ${AlphaClient.quote(query)}", 15000)
        check(result.exitCode() == 0 && !result.truncated()) { "Alpha database operation failed" }
        return result.output().lineSequence().filter { it.contains('=') }.map { line ->
            line.split('|').mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
            }.toMap()
        }.toList()
    }
    suspend fun policies(): List<AlphaPolicy> = sql("SELECT uid,policy,until,logging,notification FROM policies WHERE uid/100000=${Process.myUid() / 100000} ORDER BY uid")
        .map { row -> AlphaPolicy(row.getValue("uid").toInt(), row.getValue("policy").toInt(),
            row.getValue("until").toLong(), row["logging"] == "1", row["notification"] == "1") }

    suspend fun save(value: AlphaPolicy) {
        require(value.uid > 0 && value.uid / 100000 == Process.myUid() / 100000)
        require(value.policy in 0..3 && value.until >= 0)
        sql("REPLACE INTO policies(uid,policy,until,logging,notification) VALUES(${value.uid},${value.policy},${value.until},${if(value.logging) 1 else 0},${if(value.notification) 1 else 0})")
    }
    suspend fun remove(uid: Int) {
        require(uid > 0 && uid / 100000 == Process.myUid() / 100000)
        sql("DELETE FROM policies WHERE uid=$uid")
    }
}
