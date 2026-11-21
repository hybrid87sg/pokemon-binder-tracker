package com.hybrid87sg.bindertracker

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hybrid87sg.bindertracker.auth.AuthManager
import com.hybrid87sg.bindertracker.data.AppDatabase
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.BinderSize
import com.hybrid87sg.bindertracker.data.SpineColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The "brain" behind the screens. Screens only draw things and report
 * taps; this class holds the data and does the work. It survives screen
 * rotation, so nothing is lost when the phone turns sideways.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val binderDao = AppDatabase.get(app).binderDao()
    val auth = AuthManager(app)

    val user = auth.user

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    /** The signed-in person's binders. Switches automatically when the user changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val binders: StateFlow<List<Binder>> = user
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else binderDao.observeBinders(u.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun binder(id: String): Flow<Binder?> = binderDao.observeBinder(id)

    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            _signingIn.value = true
            _signInError.value = auth.signInWithGoogle(activityContext)
            _signingIn.value = false
        }
    }

    fun useLocalOnly() = auth.useLocalOnly()

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    fun createBinder(name: String, color: SpineColor, size: BinderSize) {
        val owner = user.value ?: return
        viewModelScope.launch {
            binderDao.insert(
                Binder(
                    ownerId = owner.uid,
                    name = name.trim(),
                    colorHex = color.hex,
                    columns = size.columns,
                    rows = size.rows,
                ),
            )
        }
    }

    fun deleteBinder(binder: Binder) {
        viewModelScope.launch { binderDao.delete(binder) }
    }
}
