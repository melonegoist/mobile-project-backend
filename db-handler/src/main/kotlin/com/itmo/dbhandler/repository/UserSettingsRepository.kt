package com.itmo.dbhandler.repository

import com.itmo.dbhandler.entity.UserSettings
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserSettingsRepository : JpaRepository<UserSettings, Long> {
    fun findByUserId(userId: Long): UserSettings?
}