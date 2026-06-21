package com.rrbrambley.flashcards.practice.discussions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrbrambley.flashcards.shared.AuthResult
import com.rrbrambley.flashcards.shared.AuthService
import com.rrbrambley.flashcards.shared.api.ApiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An action captured when a guest tries to post/report, replayed after the conversion succeeds. */
private sealed interface PendingAuth {
    data class Post(val content: String, val parentMessageId: Long?) : PendingAuth
    data class Report(val messageId: Long, val reason: String?) : PendingAuth
}

/**
 * Drives the per-card discussion thread (FLA-122): loads the thread + first page, paginates, posts
 * messages/replies, and reports messages (FLA-128). Reads are public; posting/reporting require auth,
 * so a guest is shown an inline sign-in/up prompt that — on success — replays the captured action and
 * switches to the signed-in state (mirrors the web conversion + the guest "save progress" flow).
 */
@HiltViewModel
class DiscussionViewModel @Inject constructor(
    private val repository: DiscussionRepository,
    private val authService: AuthService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiscussionUiState())
    val uiState: StateFlow<DiscussionUiState> = _uiState.asStateFlow()

    private var cardUid: String = ""
    private var nextCursor: String? = null
    private var loadJob: Job? = null
    private var loadedKey: Pair<String, Boolean>? = null

    /** Action captured when a guest tries to post/report, replayed after the conversion succeeds. */
    private var pendingAuth: PendingAuth? = null

    fun load(cardUid: String, isGuest: Boolean) {
        val key = cardUid to isGuest
        if (loadedKey == key && loadJob != null) return
        loadedKey = key
        loadJob?.cancel()
        this.cardUid = cardUid
        nextCursor = null
        pendingAuth = null
        _uiState.value = DiscussionUiState(loading = true, isGuest = isGuest)

        loadJob = viewModelScope.launch {
            try {
                val thread = repository.thread(cardUid)
                val page = repository.messages(cardUid, cursor = null)
                nextCursor = page.nextCursor
                _uiState.update {
                    it.copy(
                        loading = false,
                        messages = page.items,
                        isLocked = thread.isLocked,
                        hasMore = page.nextCursor != null,
                    )
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    fun loadMore() {
        val cursor = nextCursor ?: return
        if (_uiState.value.loadingMore) return
        _uiState.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = repository.messages(cardUid, cursor = cursor)
                nextCursor = page.nextCursor
                _uiState.update {
                    it.copy(
                        messages = it.messages + page.items,
                        hasMore = page.nextCursor != null,
                        loadingMore = false,
                    )
                }
            } catch (e: ApiError) {
                // Leave the existing messages; the user can retry.
                _uiState.update { it.copy(loadingMore = false) }
            }
        }
    }

    /**
     * Posts [content] (an optional reply to [parentMessageId]). A guest is intercepted: the message
     * is captured and the sign-in prompt is shown instead.
     */
    fun post(content: String, parentMessageId: Long?) {
        val text = content.trim()
        if (text.isEmpty() || _uiState.value.posting) return
        if (_uiState.value.isGuest) {
            pendingAuth = PendingAuth.Post(text, parentMessageId)
            _uiState.update { it.copy(authPrompt = true, authError = null) }
            return
        }
        _uiState.update { it.copy(posting = true, postError = null) }
        viewModelScope.launch {
            try {
                val message = repository.post(cardUid, text, parentMessageId)
                _uiState.update {
                    it.copy(posting = false, messages = it.messages + message, postedTick = it.postedTick + 1)
                }
            } catch (e: ApiError) {
                _uiState.update { it.copy(posting = false, postError = postErrorFor(e)) }
            }
        }
    }

    /**
     * Reports/flags a message (FLA-128; an optional [reason]). A guest is intercepted: the report is
     * captured and the sign-in prompt is shown instead. Idempotent server-side; on success the
     * message shows a "Reported" state.
     */
    fun report(messageId: Long, reason: String?) {
        val cleaned = reason?.trim()?.ifBlank { null }
        if (_uiState.value.isGuest) {
            pendingAuth = PendingAuth.Report(messageId, cleaned)
            _uiState.update { it.copy(authPrompt = true, authError = null) }
            return
        }
        viewModelScope.launch {
            try {
                repository.report(messageId, cleaned)
                _uiState.update { it.copy(reportedIds = it.reportedIds + messageId) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(postError = postErrorFor(e)) }
            }
        }
    }

    fun dismissAuthPrompt() {
        _uiState.update { it.copy(authPrompt = false, authError = null) }
    }

    fun clearPostError() {
        if (_uiState.value.postError != null) _uiState.update { it.copy(postError = null) }
    }

    /**
     * Guest conversion: register or log in, then replay the captured action (post or report) before
     * reporting success. Authenticating populates the token store, so the replay and all later writes
     * are authed and the app flips to signed-in.
     */
    fun authenticateAndPost(register: Boolean, email: String, password: String) {
        val pending = pendingAuth ?: return
        _uiState.update { it.copy(authSubmitting = true, authError = null) }
        viewModelScope.launch {
            val result = if (register) authService.register(email, password) else authService.login(email, password)
            when (result) {
                AuthResult.Success -> {
                    try {
                        when (pending) {
                            is PendingAuth.Post -> {
                                val message = repository.post(cardUid, pending.content, pending.parentMessageId)
                                _uiState.update {
                                    it.copy(
                                        isGuest = false,
                                        authPrompt = false,
                                        authSubmitting = false,
                                        messages = it.messages + message,
                                        postedTick = it.postedTick + 1,
                                    )
                                }
                            }
                            is PendingAuth.Report -> {
                                repository.report(pending.messageId, pending.reason)
                                _uiState.update {
                                    it.copy(
                                        isGuest = false,
                                        authPrompt = false,
                                        authSubmitting = false,
                                        reportedIds = it.reportedIds + pending.messageId,
                                    )
                                }
                            }
                        }
                        pendingAuth = null
                    } catch (e: ApiError) {
                        // Signed in, but the replay failed — close the prompt and surface why.
                        _uiState.update {
                            it.copy(
                                isGuest = false,
                                authPrompt = false,
                                authSubmitting = false,
                                postError = postErrorFor(e),
                            )
                        }
                    }
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(authSubmitting = false, authError = result.message)
                }
            }
        }
    }

    private fun postErrorFor(e: ApiError): DiscussionPostError = when (e) {
        is ApiError.Validation -> DiscussionPostError.Rejected(e.message)
        is ApiError.Client -> when (e.status) {
            429 -> DiscussionPostError.RateLimit
            403 -> DiscussionPostError.Locked
            400 -> DiscussionPostError.Rejected(e.message)
            else -> DiscussionPostError.Generic
        }
        else -> DiscussionPostError.Generic
    }
}
