package com.itmo.dbhandler.controller

import com.itmo.dbhandler.api.ProfileApi
import com.itmo.dbhandler.api.SettingsApi
import com.itmo.dbhandler.model.*
import com.itmo.dbhandler.service.ProfileService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class ProfileController(
    private val profileService: ProfileService
) : ProfileApi, SettingsApi {

    override fun profileGet(): ResponseEntity<User> {
        return ResponseEntity.ok(profileService.getProfile())
    }

    override fun profilePut(profilePutRequest: ProfilePutRequest): ResponseEntity<User> {
        return ResponseEntity.ok(profileService.updateProfile(profilePutRequest))
    }

    override fun profileAvatarPost(avatar: MultipartFile?): ResponseEntity<ProfileAvatarPost200Response> {
        if (avatar == null) {
            return ResponseEntity.badRequest().build()
        }
        return ResponseEntity.ok(profileService.uploadAvatar(avatar))
    }

    override fun profileStatsGet(): ResponseEntity<UserStats> {
        return ResponseEntity.ok(profileService.getStats())
    }

    override fun settingsGet(): ResponseEntity<SettingsGet200Response> {
        return ResponseEntity.ok(profileService.getSettings())
    }

    override fun settingsPut(settingsPutRequest: SettingsPutRequest): ResponseEntity<Unit> {
        profileService.updateSettings(settingsPutRequest)
        return ResponseEntity.ok().build()
    }
}