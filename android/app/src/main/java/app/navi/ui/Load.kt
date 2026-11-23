package app.navi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException

/** Result of an async load. Old [data] stays visible while refreshing so lists don't flash. */
data class LoadState<T>(val data: T? = null, val error: String? = null, val loading: Boolean = true)

class Loader<T>(state: androidx.compose.runtime.State<LoadState<T>>, val reload: () -> Unit) {
    val value by state
}

@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    val state = remember(*keys) { mutableStateOf(LoadState<T>()) }
    var tick by remember(*keys) { mutableIntStateOf(0) }
    LaunchedEffect(*keys, tick) {
        state.value = state.value.copy(loading = true)
        state.value = try {
            LoadState(block(), null, false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadState(state.value.data, e.message ?: "Unbekannter Fehler", false)
        }
    }
    return remember(state) { Loader(state) { tick++ } }
}
