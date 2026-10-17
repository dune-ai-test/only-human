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
}

@Database(
    entities = [PostEntity::class, FollowEntity::class, ActivityEntity::class, ReplyEntity::class, DmEntity::class],
    version = 4,
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

        fun get(context: Context): OhDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, OhDatabase::class.java, "only-human.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    // Nightlies may change the schema freely; pre-1.0 nothing on disk is precious.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
