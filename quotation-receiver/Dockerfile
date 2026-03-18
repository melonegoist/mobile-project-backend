# --- Этап 1: Сборка (Build) ---
FROM golang:1.21-alpine AS builder

WORKDIR /app

# Устанавливаем git и ca-certificates (нужны для go mod и HTTPS запросов)
RUN apk add --no-cache git ca-certificates

# Копируем файл зависимостей первым (для кэширования)
COPY go.mod ./
RUN go mod download

# Копируем исходный код
COPY . .

# Собираем статический бинарник
# CGO_ENABLED=0 делает бинарь полностью статическим (не нужны libc и др.)
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o main .

# --- Этап 2: Минимальный рантайм ---
# Вариант А: Alpine (рекомендуется, ~15 МБ)
FROM alpine:3.19

# Устанавливаем корневые сертификаты (иначе HTTPS запросы не будут работать)
RUN apk --no-cache add ca-certificates

WORKDIR /app

# Создаем пользователя для безопасности
RUN addgroup -g 1001 -S appgroup && adduser -S appuser -u 1001 -G appgroup

# Копируем бинарник из этапа сборки
COPY --from=builder /app/main .

# Передаем права
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# Запуск
ENTRYPOINT ["./main"]
