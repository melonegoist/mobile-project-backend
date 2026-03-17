package com.itmo.dbhandler.service

import com.itmo.dbhandler.entity.User as UserEntity
import com.itmo.dbhandler.entity.UserSettings
import com.itmo.dbhandler.entity.UserStats as UserStatsEntity
import com.itmo.dbhandler.model.*
import com.itmo.dbhandler.repository.UserRepository
import com.itmo.dbhandler.repository.UserSettingsRepository
import com.itmo.dbhandler.repository.UserStatsRepository
import com.itmo.dbhandler.util.SecurityUtils
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.time.OffsetDateTime

@Service
class ProfileService(
    private val userRepository: UserRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val userStatsRepository: UserStatsRepository
) {

    fun getProfile(): User {
        val userId = SecurityUtils.getCurrentUserId()
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        return mapToApiUser(user)
    }

    @Transactional
    fun updateProfile(request: ProfilePutRequest): User {
        val userId = SecurityUtils.getCurrentUserId()
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        request.firstName?.let { user.firstName = it }
        request.lastName?.let { user.lastName = it }
        request.email?.let {
            if (userRepository.existsByEmail(it) && user.email != it) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Email already exists")
            }
            user.email = it
        }

        user.updatedAt = OffsetDateTime.now()
        userRepository.save(user)

        return mapToApiUser(user)
    }

    @Transactional
    fun uploadAvatar(file: MultipartFile): ProfileAvatarPost200Response {
        val userId = SecurityUtils.getCurrentUserId()
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty")
        }

        if (file.contentType?.startsWith("image/") != true) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed")
        }

        val fileName = "avatar_${userId}_${System.currentTimeMillis()}.${file.originalFilename?.substringAfterLast('.')}"
        val avatarUrl = "/avatars/$fileName"

        user.avatarUrl = avatarUrl
        user.updatedAt = OffsetDateTime.now()
        userRepository.save(user)

        return ProfileAvatarPost200Response(
            avatarUrl = URI(avatarUrl)
        )
    }

    fun getStats(): UserStats {
        val userId = SecurityUtils.getCurrentUserId()
        val stats = userStatsRepository.findByUserId(userId)
            ?: createEmptyStats(userId)

        return UserStats(
            totalTrades = stats.totalTrades,
            successfulTrades = stats.successfulTrades,
            winRate = stats.winRate.toDouble(),
            totalVolume = stats.totalVolume.toDouble(),
            bestTrade = stats.bestTrade,
            worstTrade = stats.worstTrade,
            averageHoldingTime = stats.averageHoldingTime?.toString(),
            lastUpdated = stats.lastUpdated
        )
    }

    fun getSettings(): SettingsGet200Response {
        val userId = SecurityUtils.getCurrentUserId()
        val settings = userSettingsRepository.findByUserId(userId)
            ?: createDefaultSettings(userId)

        return SettingsGet200Response(
            notifications = SettingsGet200ResponseNotifications(
                email = settings.notificationsEmail,
                push = settings.notificationsPush,
                priceAlerts = settings.notificationsPriceAlerts
            ),
            language = when (settings.language) {
                "en" -> SettingsGet200Response.Language.en
                "ru" -> SettingsGet200Response.Language.ru
                "es" -> SettingsGet200Response.Language.es
                "fr" -> SettingsGet200Response.Language.fr
                "de" -> SettingsGet200Response.Language.de
                else -> SettingsGet200Response.Language.en
            },
            theme = when (settings.theme) {
                "light" -> SettingsGet200Response.Theme.light
                "dark" -> SettingsGet200Response.Theme.dark
                "system" -> SettingsGet200Response.Theme.system
                else -> SettingsGet200Response.Theme.system
            },
            defaultOrderType = when (settings.defaultOrderType) {
                "market" -> SettingsGet200Response.DefaultOrderType.market
                "limit" -> SettingsGet200Response.DefaultOrderType.limit
                else -> SettingsGet200Response.DefaultOrderType.market
            },
            riskLevel = when (settings.riskLevel) {
                "low" -> SettingsGet200Response.RiskLevel.low
                "medium" -> SettingsGet200Response.RiskLevel.medium
                "high" -> SettingsGet200Response.RiskLevel.high
                else -> SettingsGet200Response.RiskLevel.medium
            }
        )
    }

    @Transactional
    fun updateSettings(request: SettingsPutRequest) {
        val userId = SecurityUtils.getCurrentUserId()
        val settings = userSettingsRepository.findByUserId(userId)
            ?: UserSettings(userId = userId)

        request.language?.let {
            settings.language = it
        }
        request.theme?.let {
            settings.theme = it
        }
        request.notifications?.let { notifications ->
            if (notifications is Map<*, *>) {
                settings.notificationsEmail = (notifications["email"] as? Boolean) ?: settings.notificationsEmail
                settings.notificationsPush = (notifications["push"] as? Boolean) ?: settings.notificationsPush
                settings.notificationsPriceAlerts = (notifications["priceAlerts"] as? Boolean) ?: settings.notificationsPriceAlerts
            }
        }

        settings.updatedAt = OffsetDateTime.now()
        userSettingsRepository.save(settings)
    }

    private fun mapToApiUser(user: UserEntity): User {
        return User(
            id = user.id,
            username = user.username,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            avatarUrl = user.avatarUrl?.let { URI(it) },
            registrationDate = user.registrationDate,
            accountType = when (user.accountType) {
                "investor" -> User.AccountType.investor
                "trader" -> User.AccountType.trader
                "beginner" -> User.AccountType.beginner
                else -> User.AccountType.investor
            }
        )
    }

    private fun createEmptyStats(userId: Long): UserStatsEntity {
        val stats = UserStatsEntity(userId = userId)
        return userStatsRepository.save(stats)
    }

    private fun createDefaultSettings(userId: Long): UserSettings {
        val settings = UserSettings(userId = userId)
        return userSettingsRepository.save(settings)
    }
}