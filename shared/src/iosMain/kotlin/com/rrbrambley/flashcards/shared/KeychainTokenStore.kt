package com.rrbrambley.flashcards.shared

import com.rrbrambley.flashcards.shared.api.TokenStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Keychain-backed [TokenStore]: the access + refresh tokens are stored as generic-password items
 * (one per account key under [service]), so they survive app restarts and are removed on logout.
 *
 * [tokenFlow] is seeded from the Keychain at construction and updated on every write, so launch
 * auth-gating sees the persisted token immediately. Suspend reads hit the Keychain directly.
 *
 * Note: Keychain access needs an app bundle with an application-identifier — it can't run in a bare
 * Kotlin/Native test runner (`errSecNotAvailable`), so this is verified by running the app, while
 * the token *logic* is covered by fakes (`InMemoryTokenStore`, the shared `TokenRefreshAuthTest`).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainTokenStore(private val service: String) : TokenStore {

    /** No-arg init for Swift (Kotlin default args don't bridge): uses the app's default service. */
    constructor() : this(DEFAULT_SERVICE)

    private val accessTokenFlow = MutableStateFlow(read(ACCOUNT_ACCESS))

    override fun tokenFlow(): Flow<String?> = accessTokenFlow.asStateFlow()

    override suspend fun currentToken(): String? = read(ACCOUNT_ACCESS)

    override suspend fun currentRefreshToken(): String? = read(ACCOUNT_REFRESH)

    override suspend fun setToken(token: String) {
        write(ACCOUNT_ACCESS, token)
        accessTokenFlow.value = token
    }

    override suspend fun setTokens(accessToken: String, refreshToken: String) {
        write(ACCOUNT_ACCESS, accessToken)
        write(ACCOUNT_REFRESH, refreshToken)
        accessTokenFlow.value = accessToken
    }

    override suspend fun clearToken() {
        delete(ACCOUNT_ACCESS)
        delete(ACCOUNT_REFRESH)
        accessTokenFlow.value = null
    }

    private fun write(account: String, value: String) {
        delete(account)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        // The query dict uses null callbacks (no retain/release): every value stays alive until the
        // SecItem call returns, then the ones we +1'd via CFBridgingRetain are released.
        val cfService = CFBridgingRetain(service as NSString)
        val cfAccount = CFBridgingRetain(account as NSString)
        val cfData = CFBridgingRetain(data)
        val query = CFDictionaryCreateMutable(null, 0, null, null)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, cfService)
        CFDictionaryAddValue(query, kSecAttrAccount, cfAccount)
        CFDictionaryAddValue(query, kSecValueData, cfData)
        SecItemAdd(query, null)
        CFRelease(query)
        CFRelease(cfService)
        CFRelease(cfAccount)
        CFRelease(cfData)
    }

    private fun read(account: String): String? = memScoped {
        val cfService = CFBridgingRetain(service as NSString)
        val cfAccount = CFBridgingRetain(account as NSString)
        val query = CFDictionaryCreateMutable(null, 0, null, null)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, cfService)
        CFDictionaryAddValue(query, kSecAttrAccount, cfAccount)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        CFRelease(cfService)
        CFRelease(cfAccount)
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data, NSUTF8StringEncoding) as String?
    }

    private fun delete(account: String) {
        val cfService = CFBridgingRetain(service as NSString)
        val cfAccount = CFBridgingRetain(account as NSString)
        val query = CFDictionaryCreateMutable(null, 0, null, null)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, cfService)
        CFDictionaryAddValue(query, kSecAttrAccount, cfAccount)
        SecItemDelete(query)
        CFRelease(query)
        CFRelease(cfService)
        CFRelease(cfAccount)
    }

    private companion object {
        const val DEFAULT_SERVICE = "com.rrbrambley.flashcards.auth"
        const val ACCOUNT_ACCESS = "accessToken"
        const val ACCOUNT_REFRESH = "refreshToken"
    }
}
