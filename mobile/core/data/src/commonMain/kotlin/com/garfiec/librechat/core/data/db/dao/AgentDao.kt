package com.garfiec.librechat.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.garfiec.librechat.core.data.db.entity.AgentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents ORDER BY isPromoted DESC, name ASC")
    fun getAll(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id")
    suspend fun getById(id: String): AgentEntity?

    @Upsert
    suspend fun upsert(agent: AgentEntity)

    @Upsert
    suspend fun upsertAll(agents: List<AgentEntity>)

    @Query("DELETE FROM agents")
    suspend fun deleteAll()
}
