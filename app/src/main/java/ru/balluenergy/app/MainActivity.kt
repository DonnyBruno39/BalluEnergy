package ru.balluenergy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Period(val title: String) {
    MINUTE("1 минута"), TEN_MIN("10 минут"), HOUR("Час"), DAY("День"), WEEK("Неделя"), MONTH("Месяц")
}

data class Point(val power: Float, val temperature: Float)

data class HeaterState(
    val name: String = "Гостиная",
    val model: String = "Transformer DI 4.0",
    val temperature: Double = 21.0,
    val target: Double = 21.0,
    val percent: Int = 0,
    val watts: Int = 0,
    val kwh: Double = 0.0,
    val cost: Double = 0.0,
    val connected: Boolean = false,
    val period: Period = Period.HOUR,
    val points: List<Point> = listOf(
        Point(0f, 20.4f), Point(0.15f, 20.5f), Point(0.45f, 20.7f), Point(0.75f, 20.8f),
        Point(0.55f, 20.9f), Point(0.9f, 21.0f), Point(0.35f, 21.0f), Point(0.05f, 21.0f)
    )
)

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(HeaterState())
    val state = _state.asStateFlow()
    fun selectPeriod(p: Period) { _state.value = _state.value.copy(period = p) }
    fun demoHeat() { _state.value = _state.value.copy(percent = 50, watts = 1000, connected = true) }
    fun demoStop() { _state.value = _state.value.copy(percent = 0, watts = 0, connected = true) }
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
    val s by vm.state.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Ballu Energy") }) }) { pad ->
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(pad).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(s.name, style = MaterialTheme.typography.headlineSmall)
            Text(s.model, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Текущая мощность", style = MaterialTheme.typography.titleMedium)
                    Text("${s.watts} Вт", style = MaterialTheme.typography.displaySmall)
                    Text("Нагрузка ${s.percent}%")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallInfo(Modifier.weight(1f), "Температура", "${s.temperature} °C", "Цель ${s.target} °C")
                SmallInfo(Modifier.weight(1f), "Сегодня", "%.2f кВт·ч".format(s.kwh), "%.2f ₽".format(s.cost))
            }
            Text(if (s.connected) "● Подключено" else "○ Ожидание подключения", color = if (s.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Статистика", style = MaterialTheme.typography.titleLarge)
            PeriodSelector(s.period, vm::selectPeriod)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Мощность и температура — ${s.period.title}")
                    Spacer(Modifier.height(8.dp))
                    DualChart(s.points)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Расход  %.2f кВт·ч".format(s.kwh))
                        Text("Стоимость  %.2f ₽".format(s.cost))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = vm::demoHeat, Modifier.weight(1f)) { Text("Тест 50%") }
                OutlinedButton(onClick = vm::demoStop, Modifier.weight(1f)) { Text("Стоп") }
            }
        }
    }
}

@Composable
fun SmallInfo(modifier: Modifier, title: String, value: String, sub: String) {
    Card(modifier, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title)
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Period.entries.forEach { p ->
            FilterChip(selected = p == selected, onClick = { onSelect(p) }, label = { Text(p.title) })
        }
    }
}

@Composable
fun DualChart(points: List<Point>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        Modifier.fillMaxWidth().height(190.dp).background(surfaceVariant, RoundedCornerShape(14.dp)).padding(10.dp)
    ) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val powerPath = Path()
        val tempPath = Path()
        points.forEachIndexed { i, p ->
            val x = i.toFloat() / (points.size - 1) * w
            val py = h - p.power * (h * .82f) - h * .08f
            val ty = h - ((p.temperature - 20f) / 2f).coerceIn(0f, 1f) * (h * .82f) - h * .08f
            if (i == 0) { powerPath.moveTo(x, py); tempPath.moveTo(x, ty) }
            else { powerPath.lineTo(x, py); tempPath.lineTo(x, ty) }
        }
        drawPath(powerPath, color = primaryColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
        drawPath(tempPath, color = secondaryColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
    }
}
