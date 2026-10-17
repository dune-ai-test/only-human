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

/** Your follow state for one persona. */
@Entity(tableName = "follows")
data class FollowEntity(
    @PrimaryKey val personaId: String,
    val following: Boolean,
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

    @Query("SELECT * FROM follows")
    suspend fun follows(): List<FollowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFollow(follow: FollowEntity)
}

@Database(entities = [PostEntity::class, FollowEntity::class], version = 1, exportSchema = false)
abstract class OhDatabase : RoomDatabase() {

    abstract fun dao(): OhDao

    companion object {
        @Volatile private var instance: OhDatabase? = null

        fun get(context: Context): OhDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, OhDatabase::class.java, "only-human.db")
                    // Nightlies may change the schema freely; pre-1.0 there is nothing precious on disk.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
