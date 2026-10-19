package com.only.human.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** A post as it lives on disk. */
@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey val id: String,
    val authorId: String,
    val text: String,
    val createdAt: Long,
    val likes: Int,
    val replies: Int,
    val likedByYou: Boolean,
)

/** Follow state for one persona, both directions. */
@Entity(tableName = "follows")
data class FollowEntity(
    @PrimaryKey val personaId: String,
    /** You follow them. */
    val following: Boolean,
    /** They follow you. */
    val followsYou: Boolean,
)

/** One row of the Activity tab, as it lives on disk. */
@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val boldPart: String,
    val rest: String,
    val at: Long,
    val kind: String,
)

/** A reply under a post, as it lives on disk. */
@Entity(tableName = "replies")
data class ReplyEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val authorId: String,
    val text: String,
    val createdAt: Long,
)

/** A direct message, as it lives on disk. */
@Entity(tableName = "dms")
data class DmEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val fromMe: Boolean,
    val text: String,
    val at: Long,
)

/** A group chat, as it lives on disk. */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val topic: String,
    /** The member persona ids, comma-joined. */
    val members: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One group message, as it lives on disk. */
@Entity(tableName = "group_msgs")
data class GroupMsgEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val authorId: String,
    val text: String,
    val at: Long,
)

/** A persona you created, as it lives on disk. */
@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val handle: String,
    val occupation: String,
    val location: String,
    val bio: String,
    val voice: String,
    /** The two gradient colors, as ARGB ints joined by a comma. */
    val gradient: String,
    val joinedYear: String,
    val followerBoost: Int,
    val createdAt: Long,
)

/** A story — the ephemeral, right-now line, good for a day. */
@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val text: String,
    val createdAt: Long,
    val seen: Boolean,
)

@Dao
interface OhDao {

    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    suspend fun posts(): List<PostEntity>

    @Query("SELECT COUNT(*) FROM posts")
    suspend fun postCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosts(posts: List<PostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPost(post: PostEntity)

    @Query("SELECT * FROM replies ORDER BY createdAt ASC")
    suspend fun replies(): List<ReplyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReplies(items: List<ReplyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReply(item: ReplyEntity)

    @Query("SELECT * FROM activities ORDER BY at DESC")
    suspend fun activities(): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivities(items: List<ActivityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivity(item: ActivityEntity)

    @Query("SELECT * FROM follows")
    suspend fun follows(): List<FollowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFollow(follow: FollowEntity)

    @Query("SELECT * FROM dms ORDER BY at ASC")
    suspend fun dms(): List<DmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDms(items: List<DmEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDm(item: DmEntity)

    @Query("SELECT * FROM groups ORDER BY createdAt ASC")
    suspend fun groups(): List<GroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(item: GroupEntity)

    @Query("SELECT * FROM group_msgs ORDER BY at ASC")
    suspend fun groupMsgs(): List<GroupMsgEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroupMsg(item: GroupMsgEntity)

    @Query("SELECT * FROM personas ORDER BY createdAt ASC")
    suspend fun personas(): List<PersonaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersona(persona: PersonaEntity)

    @Query("SELECT * FROM stories ORDER BY createdAt DESC")
    suspend fun stories(): List<StoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStory(story: StoryEntity)

    @Query("UPDATE stories SET seen = 1 WHERE id = :id")
    suspend fun markStorySeen(id: String)

    // ---- adopting a server world -----------------------------------------
    // When this phone becomes a window, its local world steps aside for the
    // server's. These wipe the cache so cursor-zero can rebuild it cleanly.

    @Query("DELETE FROM posts")
    suspend fun wipePosts()

    @Query("DELETE FROM replies")
    suspend fun wipeReplies()

    @Query("DELETE FROM activities")
    suspend fun wipeActivities()

    @Query("DELETE FROM follows")
    suspend fun wipeFollows()

    @Query("DELETE FROM dms")
    suspend fun wipeDms()

    @Query("DELETE FROM groups")
    suspend fun wipeGroups()

    @Query("DELETE FROM group_msgs")
    suspend fun wipeGroupMsgs()

    @Query("DELETE FROM personas")
    suspend fun wipePersonas()

    @Query("DELETE FROM stories")
    suspend fun wipeStories()
}

@Database(
    entities = [PostEntity::class, FollowEntity::class, ActivityEntity::class, ReplyEntity::class, DmEntity::class, PersonaEntity::class, StoryEntity::class, GroupEntity::class, GroupMsgEntity::class],
    version = 7,
    exportSchema = false,
)
abstract class OhDatabase : RoomDatabase() {

    abstract fun dao(): OhDao

    companion object {
        @Volatile private var instance: OhDatabase? = null

        /** v2: activities got real timestamps on disk, follows gained a direction. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE follows ADD COLUMN followsYou INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE follows SET followsYou = 0 WHERE personaId IN ('theo', 'rosa')")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS activities (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "personaId TEXT NOT NULL, " +
                        "boldPart TEXT NOT NULL, " +
                        "rest TEXT NOT NULL, " +
                        "at INTEGER NOT NULL, " +
                        "kind TEXT NOT NULL)",
                )
            }
        }

        /** v3: replies moved out of counters into an actual table. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS replies (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "postId TEXT NOT NULL, " +
                        "authorId TEXT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)",
                )
            }
        }

        /** v4: direct messages — the fifteen can write to you privately now. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS dms (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "personaId TEXT NOT NULL, " +
                        "fromMe INTEGER NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "at INTEGER NOT NULL)",
                )
            }
        }

        /** v5: the cast became yours — personas you create live here. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS personas (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "handle TEXT NOT NULL, " +
                        "occupation TEXT NOT NULL, " +
                        "location TEXT NOT NULL, " +
                        "bio TEXT NOT NULL, " +
                        "voice TEXT NOT NULL, " +
                        "gradient TEXT NOT NULL, " +
                        "joinedYear TEXT NOT NULL, " +
                        "followerBoost INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)",
                )
            }
        }

        /** v6: stories — the ephemeral, right-now text they post for a day. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS stories (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "personaId TEXT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "seen INTEGER NOT NULL)",
                )
            }
        }

        /** v7: group chats — small rooms where the cast answers together. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS groups (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "topic TEXT NOT NULL, " +
                        "members TEXT NOT NULL DEFAULT '', " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS group_msgs (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "groupId TEXT NOT NULL, " +
                        "authorId TEXT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "at INTEGER NOT NULL)",
                )
            }
        }

        fun get(context: Context): OhDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, OhDatabase::class.java, "only-human.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    // Nightlies may change the schema freely; pre-1.0 nothing on disk is precious.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
