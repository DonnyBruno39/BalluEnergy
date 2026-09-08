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

class MainViewModel : ViewModel() {
    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()
    private val _devices = MutableStateFlow<List<HommynApi.Device>>(emptyList())
    val devices = _devices.asStateFlow()
    private var challenge: HommynAuth.Challenge? = null

    fun requestCode(context: android.content.Context, phone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                challenge = HommynAuth.init(context.applicationContext, phone)
                _message.value = "v0.3.4: код отправлен. Challenge=${challenge?.challenge}. Введите код из SMS."
            } catch (e: Exception) {
                _message.value = "Ошибка запроса SMS: ${e.message ?: "неизвестная ошибка"}"
            }
        }
    }

    fun authorize(code: String) {
        val ch = challenge ?: run { _message.value = "Сначала запросите код."; return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _message.value = "v0.3.4: отправляю SMS-код на /auth/${ch.challenge}…"
                val result = try {
                    HommynAuth.authorize(ch.session, ch.challenge, code)
                } catch (e: HommynApiException) {
                    throw HommynStageException("авторизации SMS (/auth/${ch.challenge})", e)
                }
                _message.value = "Код принят. Загружаю устройства…"
                try {
                    val list = HommynApi.getDevices(result.accessToken)
                    _devices.value = list
                    _message.value = if (list.isEmpty()) {
                        "Вход выполнен. Устройств не найдено."
                    } else {
                        "Вход выполнен. Подключено устройств: ${list.size}"
                    }
                } catch (e: HommynApiException) {
                    _message.value = "Вход выполнен, но список устройств не загрузился: HTTP ${e.code}: ${e.response}"
                }
            } catch (e: HommynStageException) {
                _message.value = "Ошибка этапа ${e.stage}: HTTP ${e.cause?.let { (it as? HommynApiException)?.code } ?: "?"}: ${e.cause?.message ?: "неизвестная ошибка"}"
            } catch (e: Exception) {
                _message.value = "Ошибка входа в Hommyn: ${e.message ?: "неизвестная ошибка"}"
            }
        }
    }
}

class HommynStageException(val stage: String, cause: Throwable) : Exception(cause)

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

    Scaffold(topBar = { TopAppBar(title = { Text("Ballu Energy v0.3.4") }) }) { pad ->
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
