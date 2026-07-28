package com.nebras.mobile.feature.community

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.nebras.mobile.core.data.ContentCompliance
import com.nebras.mobile.core.data.LocalStore
import com.nebras.mobile.core.data.LocalStoreKeys
import com.nebras.mobile.core.firebase.FirestoreSyncConfig
import com.nebras.mobile.core.model.ContentType
import com.nebras.mobile.core.service.HiddenContentService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * خدمة مجتمع نبراس (UGC) — نقل كامل لـ `community/service/ugc_service.dart`:
 * القنوات، النشر، التعليقات، التفاعلات، الحظر/المتابعة المحليّان،
 * وعدّادات المشاهدة الأسبوعيّة.
 */
object UgcService {

    private const val TAG = "UgcService"

    const val CHANNELS_COLLECTION = "ugc_channels"
    const val CONTENTS_COLLECTION = "ugc_contents"

    const val MAX_CHANNEL_SECTIONS = 50
    const val MAX_FILE_SIZE_BYTES = 300L * 1024 * 1024

    private val db get() = FirestoreSyncConfig.instance

    private lateinit var store: LocalStore

    private val _blockedChannels = MutableStateFlow<Set<String>>(emptySet())

    /** القنوات المحظورة محليّاً (على هذا الجهاز فقط). */
    val blockedChannels: StateFlow<Set<String>> = _blockedChannels.asStateFlow()

    private val _followedChannels = MutableStateFlow<Set<String>>(emptySet())

    /** القنوات المتابَعة (اشتراك FCM topic لكلّ قناة). */
    val followedChannels: StateFlow<Set<String>> = _followedChannels.asStateFlow()

    private val lastViewAt = ConcurrentHashMap<String, Long>()

    fun init(localStore: LocalStore) {
        store = localStore
        runCatching {
            _blockedChannels.value =
                store.getStringList(LocalStoreKeys.UGC_BLOCKED_CHANNELS).toSet()
            _followedChannels.value =
                store.getStringList(LocalStoreKeys.UGC_FOLLOWED_CHANNELS).toSet()
        }.onFailure { Log.d(TAG, "load prefs failed: ${it.message}") }
    }

    // ── الحظر المحلّيّ ──────────────────────────────────────────────

    fun setChannelBlocked(channelId: String, blocked: Boolean) {
        val next = _blockedChannels.value.toMutableSet()
        if (blocked) next.add(channelId) else next.remove(channelId)
        _blockedChannels.value = next
        runCatching { store.putStringList(LocalStoreKeys.UGC_BLOCKED_CHANNELS, next) }
            .onFailure { Log.d(TAG, "persist blocked failed: ${it.message}") }
    }

    fun isChannelBlocked(channelId: String): Boolean =
        _blockedChannels.value.contains(channelId)

    // ── المتابعة (FCM topics) ───────────────────────────────────────

    fun isChannelFollowed(channelId: String): Boolean =
        _followedChannels.value.contains(channelId)

    fun channelTopic(channelId: String): String = "nebras_channel_$channelId"

    fun publisherTopic(channelId: String): String = "nebras_publisher_$channelId"

    private suspend fun subscribePublisherTopic(channelId: String) {
        if (channelId.isEmpty()) return
        runCatching {
            FirebaseMessaging.getInstance().subscribeToTopic(publisherTopic(channelId)).await()
        }.onFailure { Log.d(TAG, "publisher topic subscribe failed: ${it.message}") }
    }

    /** تبديل متابعة القناة. يُرجع الحالة الجديدة (true = صار متابِعاً). */
    suspend fun toggleChannelFollow(channelId: String): Boolean {
        val next = _followedChannels.value.toMutableSet()
        val following = !next.contains(channelId)
        if (following) {
            next.add(channelId)
            runCatching {
                FirebaseMessaging.getInstance().subscribeToTopic(channelTopic(channelId)).await()
            }
        } else {
            next.remove(channelId)
            runCatching {
                FirebaseMessaging.getInstance()
                    .unsubscribeFromTopic(channelTopic(channelId)).await()
            }
        }
        _followedChannels.value = next
        runCatching { store.putStringList(LocalStoreKeys.UGC_FOLLOWED_CHANNELS, next) }
            .onFailure { Log.d(TAG, "persist follows failed: ${it.message}") }
        return following
    }

    // ── القنوات ─────────────────────────────────────────────────────

    val currentUid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    suspend fun getMyChannel(): UgcChannel? {
        val uid = currentUid ?: return null
        val doc = db.collection(CHANNELS_COLLECTION).document(uid).get().await()
        if (!doc.exists()) return null
        subscribePublisherTopic(uid)
        return UgcChannel.fromDoc(doc)
    }

    suspend fun getChannel(channelId: String): UgcChannel? {
        val doc = db.collection(CHANNELS_COLLECTION).document(channelId).get().await()
        if (!doc.exists()) return null
        return UgcChannel.fromDoc(doc)
    }

    suspend fun upsertMyChannel(
        name: String,
        bio: String = "",
        sections: List<ChannelSection>? = null,
        acceptedTerms: Boolean = false,
    ): UgcChannel {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("ugc_requires_sign_in")
        val ref = db.collection(CHANNELS_COLLECTION).document(user.uid)
        val existing = ref.get().await()

        val data = mutableMapOf<String, Any>(
            "ownerUid" to user.uid,
            "name" to name.trim(),
            "bio" to bio.trim(),
            "photoUrl" to (user.photoUrl?.toString() ?: ""),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        sections?.let {
            data["sections"] = it.take(MAX_CHANNEL_SECTIONS).map(ChannelSection::toMap)
        }
        if (acceptedTerms) data["termsAcceptedAt"] = FieldValue.serverTimestamp()
        if (!existing.exists()) {
            data["createdAt"] = FieldValue.serverTimestamp()
            data["status"] = "active"
        }
        ref.set(data, SetOptions.merge()).await()
        subscribePublisherTopic(user.uid)
        return UgcChannel.fromDoc(ref.get().await())
    }

    suspend fun addChannelSection(name: String): Pair<UgcChannel, ChannelSection> {
        if (FirebaseAuth.getInstance().currentUser == null) {
            throw IllegalStateException("ugc_requires_sign_in")
        }
        val channel = getMyChannel() ?: throw IllegalStateException("ugc_channel_unavailable")
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "empty section name" }
        channel.sections.firstOrNull { it.name == trimmed }?.let { return channel to it }

        val section = ChannelSection(id = "cs_${System.currentTimeMillis()}", name = trimmed)
        val updated = upsertMyChannel(
            name = channel.name,
            bio = channel.bio,
            sections = channel.sections + section,
        )
        return updated to section
    }

    // ── النشر ───────────────────────────────────────────────────────

    /**
     * ينشر محتوى جديداً في قناة المستخدم. المقال يُرفع نصّاً إلى Storage،
     * والملفّات تُرفع كما هي مع صورة مصغّرة مستخرَجة عند الإمكان.
     * يُعيد معرّف المنشور.
     */
    suspend fun publish(
        context: Context,
        channel: UgcChannel,
        title: String,
        contentType: String,
        description: String = "",
        file: File? = null,
        articleBody: String? = null,
        channelSectionId: String = "",
        channelSectionName: String = "",
        onProgress: ((Double) -> Unit)? = null,
    ): String {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.uid != channel.uid) {
            throw IllegalStateException("ugc_requires_sign_in")
        }
        // إعادة التحقّق من حالة القناة لحظة النشر (قد تكون عُلّقت).
        val currentChannel = getMyChannel()
        if (currentChannel == null || !currentChannel.isActive) {
            throw IllegalStateException("ugc_channel_unavailable")
        }

        val id = "ugc_${System.currentTimeMillis()}_${user.uid.take(6)}"
        val sourceUrl: String
        val storagePath: String
        var thumbnailUrl = ""
        val sizeInBytes: Long

        if (contentType == "article") {
            val body = (articleBody ?: "").trim()
            require(body.isNotEmpty()) { "empty article body" }
            storagePath = "ugc/${user.uid}/$id/article.txt"
            val ref = FirebaseStorage.getInstance().reference.child(storagePath)
            val bytes = body.toByteArray(Charsets.UTF_8)
            val metadata = StorageMetadata.Builder()
                .setContentType("text/plain; charset=utf-8")
                .build()
            val task = ref.putBytes(bytes, metadata)
            task.addOnProgressListener { s ->
                if (s.totalByteCount > 0) {
                    onProgress?.invoke(s.bytesTransferred.toDouble() / s.totalByteCount)
                }
            }
            task.await()
            sourceUrl = ref.downloadUrl.await().toString()
            sizeInBytes = bytes.size.toLong()
        } else {
            val f = file ?: throw IllegalArgumentException("missing file")
            sizeInBytes = f.length()
            if (sizeInBytes > MAX_FILE_SIZE_BYTES) {
                throw IllegalStateException("ugc_file_too_large")
            }
            val fileName = f.name.ifEmpty { "file" }
            storagePath = "ugc/${user.uid}/$id/$fileName"
            val ref = FirebaseStorage.getInstance().reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType(mimeFor(contentType, fileName))
                .build()
            val task = ref.putFile(android.net.Uri.fromFile(f), metadata)
            task.addOnProgressListener { s ->
                if (s.totalByteCount > 0) {
                    onProgress?.invoke(s.bytesTransferred.toDouble() / s.totalByteCount)
                }
            }
            task.await()
            sourceUrl = ref.downloadUrl.await().toString()
        }

        if (file != null) {
            runCatching {
                val thumbnail = UgcThumbnailService.create(context, file, contentType)
                if (thumbnail != null) {
                    val thumbnailRef = FirebaseStorage.getInstance().reference
                        .child("ugc/${user.uid}/$id/thumbnail.jpg")
                    val metadata = StorageMetadata.Builder()
                        .setContentType("image/jpeg")
                        .build()
                    thumbnailRef.putFile(android.net.Uri.fromFile(thumbnail), metadata).await()
                    thumbnailUrl = thumbnailRef.downloadUrl.await().toString()
                    runCatching { thumbnail.delete() }
                }
            }.onFailure { Log.d(TAG, "thumbnail upload failed: ${it.message}") }
        }

        val doc = mapOf(
            "id" to id,
            "title" to title.trim(),
            "description" to if (contentType == "article") {
                (articleBody ?: "").trim()
            } else {
                description.trim()
            },
            "content_type" to contentType,
            "source_url" to sourceUrl,
            "fileSize" to sizeInBytes,
            "thumbnail" to thumbnailUrl,
            "created_by" to channel.name,
            "author" to channel.name,
            "source_name" to channel.name,
            "created_at" to FieldValue.serverTimestamp(),
            "updated_at" to FieldValue.serverTimestamp(),
            "is_ugc" to true,
            "channelId" to channel.uid,
            "channelName" to channel.name,
            "channelPhotoUrl" to channel.photoUrl,
            "channelStatus" to channel.status,
            "channel_section_id" to channelSectionId,
            "channel_section_name" to channelSectionName,
            "storage_path" to storagePath,
            "commentsCount" to 0,
            "reactionsCount" to 0,
            "status" to "published",
        )
        db.collection(CONTENTS_COLLECTION).document(id).set(doc).await()
        return id
    }

    /**
     * تعديل منشور — نفس قيود القواعد حرفياً: العنوان 3–120، الوصف ≤20000،
     * الأنواع المسموحة فقط، وتغيير النوع يستلزم ملفّاً بديلاً. عند استبدال
     * الأصل تُحذف الملفّات القديمة من المجلد (عدا الجديد والمصغّرة).
     */
    suspend fun updateMyPost(
        context: Context,
        post: UgcPost,
        title: String,
        contentType: String,
        description: String,
        articleBody: String? = null,
        replacementFile: File? = null,
        channelSectionId: String = "",
        channelSectionName: String = "",
        onProgress: ((Double) -> Unit)? = null,
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        val uid = user?.uid
        val cleanTitle = title.trim()
        if (uid == null || uid != post.channelId) {
            throw IllegalStateException("ugc_not_post_owner")
        }
        require(cleanTitle.length in 3..120) { "ugc_invalid_title" }
        require(description.trim().length <= 20000) { "ugc_description_too_long" }
        require(contentType in setOf("audio", "video", "document", "article")) {
            "ugc_invalid_content_type"
        }
        require(channelSectionId.length <= 100 && channelSectionName.length <= 60) {
            "ugc_invalid_channel_section"
        }
        val channel = getMyChannel()
        if (channel == null || !channel.isActive) {
            throw IllegalStateException("ugc_channel_unavailable")
        }

        val previousType = storedContentType(post.content.type)
        val folder = "ugc/$uid/${post.content.id}"
        var sourceUrl = post.content.sourceUrl.orEmpty()
        var storagePath = post.storagePath
        var thumbnailUrl = post.content.thumbnailUrl
        var sizeInBytes = (post.content.sizeInBytes ?: 0).toLong()
        var replacedAsset = false

        if (contentType == "article") {
            val body = (articleBody ?: "").trim()
            require(body.length in 30..20000) { "ugc_invalid_article_body" }
            storagePath = "$folder/article.txt"
            val ref = FirebaseStorage.getInstance().reference.child(storagePath)
            val bytes = body.toByteArray(Charsets.UTF_8)
            val task = ref.putBytes(
                bytes,
                StorageMetadata.Builder().setContentType("text/plain; charset=utf-8").build(),
            )
            task.addOnProgressListener { s ->
                if (s.totalByteCount > 0) {
                    onProgress?.invoke(s.bytesTransferred.toDouble() / s.totalByteCount)
                }
            }
            task.await()
            sourceUrl = ref.downloadUrl.await().toString()
            sizeInBytes = bytes.size.toLong()
            thumbnailUrl = ""
            replacedAsset = true
        } else if (replacementFile != null) {
            sizeInBytes = replacementFile.length()
            if (sizeInBytes > MAX_FILE_SIZE_BYTES) {
                throw IllegalStateException("ugc_file_too_large")
            }
            val fileName = replacementFile.name.ifEmpty { "file" }
            storagePath = "$folder/$fileName"
            val ref = FirebaseStorage.getInstance().reference.child(storagePath)
            val task = ref.putFile(
                android.net.Uri.fromFile(replacementFile),
                StorageMetadata.Builder()
                    .setContentType(mimeFor(contentType, fileName))
                    .build(),
            )
            task.addOnProgressListener { s ->
                if (s.totalByteCount > 0) {
                    onProgress?.invoke(s.bytesTransferred.toDouble() / s.totalByteCount)
                }
            }
            task.await()
            sourceUrl = ref.downloadUrl.await().toString()
            thumbnailUrl = ""
            runCatching {
                val thumbnail = UgcThumbnailService.create(context, replacementFile, contentType)
                if (thumbnail != null) {
                    val thumbnailRef = FirebaseStorage.getInstance().reference
                        .child("$folder/thumbnail.jpg")
                    thumbnailRef.putFile(
                        android.net.Uri.fromFile(thumbnail),
                        StorageMetadata.Builder().setContentType("image/jpeg").build(),
                    ).await()
                    thumbnailUrl = thumbnailRef.downloadUrl.await().toString()
                    runCatching { thumbnail.delete() }
                }
            }.onFailure { Log.d(TAG, "replacement thumbnail failed: ${it.message}") }
            replacedAsset = true
        } else if (contentType != previousType) {
            throw IllegalArgumentException("ugc_replacement_file_required")
        }

        db.collection(CONTENTS_COLLECTION).document(post.content.id).update(
            mapOf(
                "title" to cleanTitle,
                "description" to if (contentType == "article") {
                    (articleBody ?: "").trim()
                } else {
                    description.trim()
                },
                "content_type" to contentType,
                "source_url" to sourceUrl,
                "fileSize" to sizeInBytes,
                "thumbnail" to thumbnailUrl,
                "storage_path" to storagePath,
                "channel_section_id" to channelSectionId,
                "channel_section_name" to channelSectionName,
                "updated_at" to FieldValue.serverTimestamp(),
            ),
        ).await()

        if (replacedAsset) {
            removeStalePostFiles(
                folder = folder,
                keep = buildSet {
                    add(storagePath)
                    if (thumbnailUrl.isNotEmpty()) add("$folder/thumbnail.jpg")
                },
            )
        }
    }

    /** حذف منشوري: التعليقات والتفاعلات على دفعات، ثمّ الوثيقة، ثمّ الملفّات. */
    suspend fun deleteMyPost(post: UgcPost) {
        val uid = currentUid
        if (uid == null || uid != post.channelId) return

        for (child in listOf(comments(post.content.id), reactions(post.content.id))) {
            while (true) {
                val snapshot = child.limit(400).get().await()
                if (snapshot.isEmpty) break
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        db.collection(CONTENTS_COLLECTION).document(post.content.id).delete().await()

        if (post.storagePath.isNotEmpty()) {
            runCatching {
                val fileRef = FirebaseStorage.getInstance().reference.child(post.storagePath)
                val folder = fileRef.parent
                if (folder == null) {
                    fileRef.delete().await()
                } else {
                    val listed = folder.listAll().await()
                    listed.items.forEach { runCatching { it.delete().await() } }
                }
            }.onFailure { Log.d(TAG, "storage delete failed: ${it.message}") }
        }
    }

    // ── القراءة والبثّ ──────────────────────────────────────────────

    /** بثّ حيّ لأحدث المنشورات (المنشورة من قنوات نشطة غير محظورة). */
    fun watchLatest(limit: Int = 60): Flow<List<UgcPost>> =
        watchQuery(
            db.collection(CONTENTS_COLLECTION)
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(limit.toLong()),
            includeBlocked = false,
        )

    suspend fun getPost(id: String): UgcPost? {
        if (id.isEmpty()) return null
        val doc = db.collection(CONTENTS_COLLECTION).document(id).get().await()
        if (!doc.exists()) return null
        val post = UgcPost.fromDoc(doc)
        if (!post.isPublished || !post.isChannelActive) return null
        if (HiddenContentService.isHidden(post.content.id)) return null
        return post
    }

    /** منشورات قناة معيّنة — المحظورة تبقى ظاهرة داخل صفحة القناة نفسها. */
    fun watchChannel(channelId: String, limit: Int = 100): Flow<List<UgcPost>> =
        watchQuery(
            db.collection(CONTENTS_COLLECTION)
                .whereEqualTo("channelId", channelId)
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(limit.toLong()),
            includeBlocked = true,
        )

    private fun watchQuery(query: Query, includeBlocked: Boolean): Flow<List<UgcPost>> =
        callbackFlow {
            val registration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                trySend(filterPosts(snapshot.documents.map(UgcPost::fromDoc), includeBlocked))
            }
            awaitClose { registration.remove() }
        }

    /**
     * عدّاد مشاهدات أسبوعيّ مستقلّ عن عدّادات المحتوى الرسميّ — يُقلَّم
     * لآخر 7 أيّام داخل معاملة (debounce دقيقة/منشور).
     */
    suspend fun recordPostOpened(post: UgcPost) {
        val id = post.content.id.trim()
        if (id.isEmpty()) return
        val now = System.currentTimeMillis()
        val last = lastViewAt[id]
        if (last != null && now - last < 60_000) return
        lastViewAt[id] = now

        val today = UgcPost.dayKey(Date(now))
        val cutoff = UgcPost.dayKey(UgcPost.daysAgo(6))
        val ref = db.collection(CONTENTS_COLLECTION).document(id)

        runCatching {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(ref)
                if (!snapshot.exists()) return@runTransaction
                val rawDays = snapshot.get("view_days")
                val days = mutableMapOf<String, Int>()
                if (rawDays is Map<*, *>) {
                    for ((key, value) in rawDays) {
                        val k = key.toString()
                        if (k >= cutoff && value is Number) days[k] = value.toInt()
                    }
                }
                days[today] = (days[today] ?: 0) + 1
                transaction.update(
                    ref,
                    mapOf(
                        "view_count" to FieldValue.increment(1),
                        "view_days" to days,
                        "last_played_at" to FieldValue.serverTimestamp(),
                    ),
                )
            }.await()
        }.onFailure { Log.d(TAG, "view analytics failed: ${it.message}") }
    }

    private fun filterPosts(posts: Iterable<UgcPost>, includeBlocked: Boolean): List<UgcPost> {
        val blocked = _blockedChannels.value
        return posts.filter { p ->
            if (!p.isPublished || !p.isChannelActive) return@filter false
            if (!ContentCompliance.isRightsCompliant(p.content)) return@filter false
            if (HiddenContentService.isHidden(p.content.id)) return@filter false
            if (!includeBlocked && blocked.contains(p.channelId)) return@filter false
            true
        }
    }

    // ── التعليقات ───────────────────────────────────────────────────

    private fun comments(contentId: String): CollectionReference =
        db.collection(CONTENTS_COLLECTION).document(contentId).collection("comments")

    fun watchComments(contentId: String, limit: Int = 200): Flow<List<UgcComment>> =
        callbackFlow {
            val registration = comments(contentId)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(limit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    trySend(snapshot.documents.map(UgcComment::fromDoc))
                }
            awaitClose { registration.remove() }
        }

    suspend fun addComment(contentId: String, text: String) {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("ugc_requires_sign_in")
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val batch = db.batch()
        batch.set(
            comments(contentId).document(),
            mapOf(
                "uid" to user.uid,
                "name" to (
                    user.displayName?.trim()?.takeIf { it.isNotEmpty() }
                        ?: user.email?.substringBefore('@')
                        ?: "User"
                    ),
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "text" to trimmed,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.update(
            db.collection(CONTENTS_COLLECTION).document(contentId),
            mapOf("commentsCount" to FieldValue.increment(1)),
        )
        batch.commit().await()
    }

    /** الحذف مسموح لكاتب التعليق أو مالك المنشور. */
    suspend fun deleteComment(contentId: String, comment: UgcComment) {
        val uid = currentUid ?: return
        val post = getPost(contentId)
        if (uid != comment.uid && uid != post?.channelId) return

        val batch = db.batch()
        batch.delete(comments(contentId).document(comment.id))
        batch.update(
            db.collection(CONTENTS_COLLECTION).document(contentId),
            mapOf("commentsCount" to FieldValue.increment(-1)),
        )
        batch.commit().await()
    }

    // ── التفاعلات ───────────────────────────────────────────────────

    private fun reactions(contentId: String): CollectionReference =
        db.collection(CONTENTS_COLLECTION).document(contentId).collection("reactions")

    fun watchMyReaction(contentId: String): Flow<Boolean> {
        val uid = currentUid ?: return kotlinx.coroutines.flow.flowOf(false)
        return callbackFlow {
            val registration = reactions(contentId).document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.exists() == true)
                }
            awaitClose { registration.remove() }
        }
    }

    /** تبديل الإعجاب داخل معاملة. الضيوف لا يتفاعلون. */
    suspend fun toggleReaction(contentId: String): Boolean {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) {
            throw IllegalStateException("ugc_requires_sign_in")
        }
        val postRef = db.collection(CONTENTS_COLLECTION).document(contentId)
        val reactionRef = reactions(contentId).document(user.uid)

        return db.runTransaction { transaction ->
            val reaction = transaction.get(reactionRef)
            if (reaction.exists()) {
                transaction.delete(reactionRef)
                transaction.update(postRef, mapOf("reactionsCount" to FieldValue.increment(-1)))
                false
            } else {
                transaction.set(
                    reactionRef,
                    mapOf(
                        "uid" to user.uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                transaction.update(postRef, mapOf("reactionsCount" to FieldValue.increment(1)))
                true
            }
        }.await()
    }

    // ── أدوات ───────────────────────────────────────────────────────

    private fun mimeFor(contentType: String, fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (contentType) {
            "audio" -> "audio/${if (ext == "mp3") "mpeg" else ext.ifEmpty { "mpeg" }}"
            "video" -> "video/${ext.ifEmpty { "mp4" }}"
            "document" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    private fun storedContentType(type: ContentType): String = when (type) {
        ContentType.AUDIO -> "audio"
        ContentType.VIDEO -> "video"
        ContentType.ARTICLE -> "article"
        ContentType.BOOK, ContentType.YOUTUBE, ContentType.IMAGE -> "document"
    }

    private suspend fun removeStalePostFiles(folder: String, keep: Set<String>) {
        runCatching {
            val listed = FirebaseStorage.getInstance().reference.child(folder).listAll().await()
            listed.items
                .filter { it.path.trimStart('/') !in keep && it.path !in keep }
                .forEach { runCatching { it.delete().await() } }
        }.onFailure { Log.d(TAG, "stale post files cleanup failed: ${it.message}") }
    }
}

/**
 * استخراج الصور المصغّرة — نقل `ugc_thumbnail_service.dart`. في نسخة Flutter
 * كان عبر MethodChannel إلى كود Kotlin أصليّ؛ هنا ننفّذه مباشرة بـ
 * [MediaMetadataRetriever] (إطار من الفيديو). الصوت والمستندات بلا مصغّرة.
 */
object UgcThumbnailService {

    private const val TAG = "UgcThumbnail"

    fun create(context: Context, source: File, contentType: String): File? {
        if (!source.exists()) return null
        if (contentType != "video") return null
        return runCatching {
            val retriever = MediaMetadataRetriever()
            val bitmap: Bitmap? = try {
                retriever.setDataSource(source.absolutePath)
                retriever.getFrameAtTime(1_000_000) ?: retriever.frameAtTime
            } finally {
                runCatching { retriever.release() }
            }
            if (bitmap == null) return null
            val out = File(
                context.cacheDir,
                "ugc_thumb_${System.nanoTime()}.jpg",
            )
            FileOutputStream(out).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            }
            bitmap.recycle()
            if (out.length() > 0) out else null
        }.onFailure { Log.d(TAG, "extraction failed: ${it.message}") }.getOrNull()
    }
}
