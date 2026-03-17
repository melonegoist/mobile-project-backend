package com.itmo.dbhandler.repository

import com.itmo.dbhandler.entity.UserStats
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserStatsRepository : JpaRepository<UserStats, Long> {
    fun findByUserId(userId: Long): UserStats?
}