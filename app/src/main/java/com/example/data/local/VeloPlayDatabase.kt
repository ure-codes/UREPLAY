package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.VideoSourceType

class VideoTypeConverters {
    @TypeConverter
    fun fromSourceType(type: VideoSourceType): String = type.name

    @TypeConverter
    fun toSourceType(value: String): VideoSourceType =
        runCatching { VideoSourceType.valueOf(value) }.getOrDefault(VideoSourceType.WEB_LINK)
}

@Database(entities = [VideoEntity::class], version = 1, exportSchema = false)
@TypeConverters(VideoTypeConverters::class)
abstract class VeloPlayDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao

    companion object {
        @Volatile
        private var INSTANCE: VeloPlayDatabase? = null

        fun getInstance(context: Context): VeloPlayDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VeloPlayDatabase::class.java,
                    "veloplay_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
