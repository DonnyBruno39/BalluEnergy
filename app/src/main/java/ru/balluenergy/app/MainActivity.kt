package ru.balluenergy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.balluenergy.app.hommyn.HommynApi
import ru.balluenergy.app.hommyn.HommynApiException
import ru.balluenergy.app.hommyn.HommynAuth

private const val APP_VERSION = "0.3.7"

class MainViewModel : ViewModel() {
    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()
    private val _devices = MutableStateFlow<List<HommynApi.Device>>(emptyList())
    val devices = _devices.asStateFlow()
    private var challenge: HommynAuth.Challenge? = null

    fun requestCode(context: android.content.Context, phone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _message.value = "Запрашиваю код…"
                challenge = HommynAuth.init(context.applicationContext, phone)
                _message.value = "Код отправлен. Challenge=${challenge?.challenge}. Введите код из SMS."
            } catch (e: HommynApiException) {
                _message.value = formatApiError("запроса SMS", e)
            } catch (e: Exception) {
                _message.value = "Ошибка запроса SMS: ${e.message ?: "неизвестная ошибка"}"
            }
        }
    }

    fun authorize(code: String) {
        val ch = challenge ?: run { _message.value = "Сначала запросите код."; return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _message.value = "Проверяю SMS-код…"
                val result = HommynAuth.authorize(ch.session, ch.challenge, code)
                _message.value = "Код принят. Загружаю устройства…"
                val list = HommynApi.getDevices(result.accessToken)
                _devices.value = list
                _message.value = if (list.isEmpty()) {
                    "Вход выполнен. Устройств в облаке не найдено."
                } else {
                    "Вход выполнен. Устройств найдено: ${list.size}"
                }
            } catch (e: HommynApiException) {
                _message.value = formatApiError("авторизации/загрузки устройств", e)
            } catch (e: Exception) {
                _message.value = "Ошибка входа в Hommyn: ${e.message ?: "неизвестная ошибка"}"
            }
        }
    }

    private fun formatApiError(stage: String, e: HommynApiException): String {
        val body = e.response.trim().replace("\\n", " ").take(500)
        return "Ошибка $stage: HTTP ${e.code}${if (body.isNotBlank()) ": $body" else ""}"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { BalluEnergyScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalluEnergyScreen(vm: MainViewModel = viewModel()) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val message by vm.message.collectAsState()
    val devices by vm.devices.collectAsState()
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("Ballu Energy v$APP_VERSION") }) }) { pad ->
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Вход в Hommyn", style = MaterialTheme.typography.headlineSmall)
            Text("Авторизация выполняется напрямую с сервером Hommyn.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Телефон") }, singleLine = true)
            Button(onClick = { vm.requestCode(context, phone) }, modifier = Modifier.fillMaxWidth(), enabled = phone.isNotBlank()) { Text("Получить код") }
            OutlinedTextField(value = code, onValueChange = { code = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Код из SMS") }, singleLine = true)
            Button(onClick = { vm.authorize(code) }, modifier = Modifier.fillMaxWidth(), enabled = code.isNotBlank()) { Text("Войти") }
            if (message.isNotBlank()) Text(message)
            if (devices.isNotEmpty()) {
                Text("Мои устройства", style = MaterialTheme.typography.titleLarge)
                devices.forEach { d ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(d.name ?: "Без названия", style = MaterialTheme.typography.titleMedium)
                            Text(d.model ?: "Модель неизвестна")
                            Text("MAC: ${d.mac ?: "—"}")
                            Text("Тип: ${d.deviceType ?: "—"}")
                            Text("MQTT: ${d.host ?: "—"}")
                        }
                    }
                }
            }
        }
    }
}
