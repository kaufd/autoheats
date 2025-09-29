# Архитектура интеграции с automotive_plugin

## Поток управления подогревом сидений

```
UI (нажатие кнопки)
    ↓
ModeCubit.setHeatLevel()
    ↓
ModeService.setHeatLevel() → SharedPreferences (сохранение состояния)
    ↓
SeatHeatService.setSeatHeatLevel()
    ↓
CarHvacManager.setSeatHeatLevel()
    ↓
AndroidAutomotivePlugin.setHvacIntProperty()
    ↓
Android Automotive System → Реальное управление подогревом сидений
```

## Поток получения температуры салона

### Событийная обработка (основной механизм):
```
Android Automotive System → Датчик температуры салона
    ↓
AndroidAutomotivePlugin.onHvacChangeEvent()
    ↓
TemperatureEventService._handleHvacChangeEvent()
    ↓
AutoHeatService._setupTemperatureEvents()
    ↓
AutoHeatService._updateAutoHeatForAllUsers()
    ↓
Автоматическое управление подогревом на основе температуры салона
```

### Получение начальной температуры:
```
При запуске приложения → AutoHeatService._getInitialTemperature()
    ↓
TemperatureSensorService.getCabinTemperatureModel()
    ↓
CarHvacManager.getInsideTemperature()
    ↓
AndroidAutomotivePlugin.getHvacIntProperty()
    ↓
Получение текущей температуры для инициализации
```

## Компоненты системы

### 1. Service Locator (DI)
- **AndroidAutomotivePlugin** - основной плагин для связи с автомобилем
- **CarHvacManager** - менеджер HVAC системы
- **SeatHeatService** - сервис управления подогревом сидений
- **TemperatureSensorService** - сервис получения температуры салона от датчиков
- **TemperatureEventService** - сервис обработки событий изменения температуры салона
- **AutoHeatService** - сервис автоматического подогрева
- **ModeCubit** - бизнес-логика управления режимами

### 2. Обработка ошибок
- **Подключение к автомобилю**: Graceful fallback в режим симуляции
- **Управление подогревом**: Логирование ошибок без прерывания UI
- **Получение состояния**: Возврат значения по умолчанию при ошибках

### 3. Интеграция с automotive_plugin

#### Инициализация (service_locator.dart):
```dart
// Создание и подключение плагина
final androidAutomotivePlugin = AndroidAutomotivePlugin();
await androidAutomotivePlugin.connect();

// Создание менеджера HVAC
final carHvacManager = CarHvacManager(androidAutomotivePlugin);

// Создание сервиса подогрева
final seatHeatService = SeatHeatService(carHvacManager);
```

#### Управление подогревом (ModeCubit):
```dart
void setHeatLevel(UserType userType, int level) async {
  // 1. Сохранение в SharedPreferences
  await _modeService.setHeatLevel(userType, level);

  // 2. Управление реальным подогревом
  await _seatHeatService.setSeatHeatLevel(userType, level);

  // 3. Обновление UI
  emit(ModesState(states: updatedStates));
}
```

#### Реальное управление (SeatHeatService):
```dart
Future<void> setSeatHeatLevel(UserType userType, int level) async {
  final isDriver = userType == UserType.driver;
  await _carHvacManager.setSeatHeatLevel(isDriver, level);
}
```

## Поток данных

### Установка уровня подогрева:
1. **UI** → ModeCubit.setHeatLevel()
2. **ModeCubit** → ModeService.setHeatLevel() (сохранение)
3. **ModeCubit** → SeatHeatService.setSeatHeatLevel()
4. **SeatHeatService** → CarHvacManager.setSeatHeatLevel()
5. **CarHvacManager** → AndroidAutomotivePlugin.setHvacIntProperty()
6. **AndroidAutomotivePlugin** → Android Automotive System

### Получение температуры салона:
1. **Android Automotive System** → Датчик температуры салона
2. **AndroidAutomotivePlugin** → TemperatureEventService
3. **TemperatureEventService** → AutoHeatService
4. **AutoHeatService** → Автоматическое управление подогревом

### Получение текущего состояния подогрева:
1. **UI** → ModeCubit.getCurrentSeatHeatLevel()
2. **ModeCubit** → SeatHeatService.getSeatHeatLevel()
3. **SeatHeatService** → CarHvacManager.getSeatHeatLevel()
4. **CarHvacManager** → AndroidAutomotivePlugin.getHvacIntProperty()
5. **AndroidAutomotivePlugin** → Android Automotive System
