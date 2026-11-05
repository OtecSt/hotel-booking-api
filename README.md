# Hotel Booking API

Учебный REST API бронирования отелей. Пользователь выбирает номер вручную или
запрашивает автоподбор. Администратор управляет каталогом и пользователями,
а также сравнивает загрузку номеров по числу броней и по числу занятых ночей.

| Приложение | Порт | Назначение |
|---|---:|---|
| `eureka-server` | 8761 | Реестр сервисов |
| `api-gateway` | 8080 | Входная проверка JWT, маршрутизация и идентификатор запроса |
| `hotel-service` | 8081 | Каталог, резервы по датам и статистика. Отдельная H2 |
| `booking-service` | 8082 | Пользователи, JWT, история броней и компенсация. Отдельная H2 |

## Проверка из чистой копии

Нужны JDK 21, Bash, Python 3.8+, curl и OpenSSL. Maven Wrapper загрузит нужную
версию Maven. Первый запуск также загрузит зависимости проекта.

```sh
./mvnw --batch-mode clean verify
./scripts/acceptance.sh
```

Первая команда собирает четыре приложения, запускает тесты JUnit и MockMvc и
проверяет стиль. Вторая поднимает отдельный стенд на портах 18080–18083 и 18761,
создаёт временные секреты, выполняет сценарии через Gateway и останавливает
сервисы. При ошибке выводит последние строки журналов. Этот же сценарий
выполняется в GitHub Actions. Чтобы сохранить журналы успешной проверки,
запустите `KEEP_ACCEPTANCE_LOGS=1 ./scripts/acceptance.sh`.

## Ручной запуск

Из корня проекта после сборки сгенерируйте два значения:

```sh
export HOTEL_BOOKING_JWT_SECRET="$(openssl rand -hex 32)"
export HOTEL_BOOKING_DEMO_ADMIN_PASSWORD="$(openssl rand -hex 16)"
```

Запускайте приложения в отдельных терминалах из корня проекта. Для Gateway,
Hotel и Booking задайте **одинаковый** `HOTEL_BOOKING_JWT_SECRET`. Пароль
`HOTEL_BOOKING_DEMO_ADMIN_PASSWORD` нужен в терминале Booking при запуске
профиля `demo`. В Hotel этот профиль создаёт тестовый отель и два номера.

```sh
java -jar eureka-server/target/eureka-server-0.1.0-SNAPSHOT.jar
java -jar hotel-service/target/hotel-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo
java -jar booking-service/target/booking-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo
java -jar api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar
```

Дождитесь `UP` на `/actuator/health` у каждого приложения. [Панель Eureka](http://localhost:8761/)
показывает регистрацию трёх клиентов. Обновление списка сервисов может занять
до минуты. Для ручной проверки используйте `http://localhost:8080`.

## API и демонстрация

- [Swagger Hotel](http://localhost:8081/swagger-ui/index.html)
- [Swagger Booking](http://localhost:8082/swagger-ui/index.html)
- [Архитектура, схема БД и принятые решения](docs/architecture.md)
- [Сценарий защиты и матрица на 50 баллов](docs/acceptance.md)

| Метод и путь через Gateway | Назначение |
|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | Передать `username` и `password`, получить `accessToken` |
| `GET /api/hotels`, `GET /api/hotels/{id}` | Каталог отелей |
| `GET /api/rooms`, `GET /api/rooms/recommend` | Свободные номера на `startDate` и `endDate`, опционально с фильтром `hotelId`. Рекомендации отсортированы по `timesBooked` и ID |
| `GET /api/rooms/{id}` | Карточка номера |
| `POST /api/bookings` | Бронь с `startDate`, `endDate` и `roomId`. Для автоподбора передайте `autoSelect: true` |
| `GET /api/bookings`, `GET /api/bookings/{id}` | Своя история с параметрами `page`, `size` и просмотр отдельной брони |
| `DELETE /api/bookings/{id}` | Отмена своей брони |
| `POST /api/hotels`, `PUT /api/hotels/{id}`, `DELETE /api/hotels/{id}` | Управление отелями, роль `ADMIN` |
| `POST /api/rooms`, `PUT /api/rooms/{id}`, `DELETE /api/rooms/{id}` | Управление номерами, роль `ADMIN` |
| `GET /api/rooms/occupancy` | Загрузка по ночам за период, роль `ADMIN`. Поддерживается фильтр `hotelId` |
| `GET /api/users`, `GET /api/users/{id}`, `POST /api/users`, `PATCH /api/users/{id}`, `DELETE /api/users/{id}` | Управление пользователями, роль `ADMIN` |

Кроме регистрации и входа, запросы требуют `Authorization: Bearer <accessToken>`.
Тестовый администратор входит под именем `demo-admin` с заданным при запуске
паролем. Для создания брони нужен заголовок `Idempotency-Key`. Повтор с тем же
ключом и теми же параметрами возвращает ту же запись. Повторное использование
ключа с другими параметрами даёт `409`. При автоподборе поле `roomId` игнорируется.
