package top.myrest.myflow.chatgpt

import org.dizitart.no2.IndexType
import org.dizitart.no2.objects.Index
import org.dizitart.no2.objects.Indices
import org.dizitart.no2.objects.InheritIndices
import top.myrest.myflow.db.AutoIncrementDoc
import top.myrest.myflow.db.BaseRepo
import top.myrest.myflow.db.MyDb

internal enum class ContentType {
    TEXT, IMAGES, FILE
}

@InheritIndices
@Indices(
    value = [
        Index(value = "session", type = IndexType.NonUnique),
        Index(value = "content", type = IndexType.Fulltext),
    ]
)
internal data class ChatHistoryDoc(
    val session: String,
    val role: String,
    val content: String,
    val value: String = "",
    val type: ContentType = ContentType.TEXT,
    val at: Long = System.currentTimeMillis(),
) : AutoIncrementDoc()

internal object ChatHistoryRepo : BaseRepo() {

    fun addChat(userDoc: ChatHistoryDoc, chatDoc: ChatHistoryDoc) {
        if (userDoc.id != null || chatDoc.id != null || userDoc.session.isBlank() || chatDoc.session.isBlank()) {
            return
        }
        MyDb.insertDoc(chatDoc)
        MyDb.insertDoc(userDoc)
    }
}