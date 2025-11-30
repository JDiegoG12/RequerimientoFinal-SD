# Laboratorio de Sistemas Distribuidos: Streaming gRPC-Web con Reacciones en Tiempo Real

Este proyecto es la implementación del requerimiento final para el curso de Laboratorio de Sistemas Distribuidos de la Universidad del Cauca. La aplicación demuestra un sistema completo que integra streaming de audio vía **gRPC-Web**, un sistema de reacciones en tiempo real con **WebSockets**, y una simulación de microservicios de pago, todo orquestado para funcionar de manera concurrente y tolerante a fallos.


## 👥 Autores

-   **Juan Diego Gómez Garcés**
-   **Ana Sofía Arango Yanza**

---

## ✨ Características Principales

-   **Streaming de Audio gRPC-Web:** El cliente solicita y recibe fragmentos de audio de un servidor de streaming a través de un proxy Envoy que traduce gRPC-Web a gRPC.
-   **Comunicación en Tiempo Real con WebSockets:** Los clientes se conectan a un microservicio de reacciones para:
    -   Ver quiénes están escuchando la misma canción en tiempo real.
    -   Recibir notificaciones cuando un usuario da `play` o `pause`.
    -   Enviar y recibir reacciones (`like`, `love`, `fire`) que se muestran como animaciones en pantalla.
-   **Simulación de Microservicios de Pago:**
    -   Cada reacción tiene un costo simulado de $10.
    -   Un usuario tiene un límite de gasto de $50 (5 reacciones).
    -   El sistema notifica al usuario cuando ha alcanzado su límite de saldo.
-   **Tolerancia a Fallos y Reintentos:**
    -   El servidor de pagos simula fallos periódicos.
    -   El servidor de reacciones implementa una política de reintentos con **backoff exponencial** (usando Spring Retry) para manejar estos fallos de forma robusta.
-   **Interfaz de Usuario Dinámica:**
    -   Animaciones fluidas para las reacciones, que flotan desde los costados de la pantalla.
    -   Notificaciones "toast" para informar al usuario sobre errores o límites alcanzados.
    -   Actualización en vivo de la lista de usuarios activos y una línea de tiempo de eventos.

---

## 🏛️ Arquitectura del Sistema

El proyecto sigue una arquitectura de microservicios donde cada componente tiene una responsabilidad clara. La comunicación se realiza a través de diferentes protocolos según la necesidad.

1.  **Cliente Web (HTML, CSS, JS):** La interfaz con la que interactúa el usuario.
2.  **Proxy Envoy:** Actúa como intermediario, traduciendo las peticiones gRPC-Web (HTTP/1.1) del navegador a gRPC nativo (HTTP/2) que el servidor de streaming entiende.
3.  **Servidor de Streaming (Go):** Provee los fragmentos de audio de la canción solicitada.
4.  **Servidor de Reacciones (Java - Spring Boot):** El núcleo de la lógica de negocio en tiempo real. Gestiona las sesiones WebSocket, los canales por canción y orquesta los pagos.
5.  **Servidor de Pagos (Java - Spring Boot):** Simula un servicio de pagos, generando tokens y validando las transacciones.

---

## 🛠️ Tecnologías Utilizadas

-   **Backend:**
    -   Java 17
    -   Spring Boot 3
    -   Spring Web
    -   Spring WebSocket (con STOMP)
    -   Spring Retry (para tolerancia a fallos)
    -   Maven
-   **Frontend:**
    -   HTML5, CSS3, JavaScript (ES6+)
    -   Stomp.js & SockJS-client
-   **Protocolos y Comunicación:**
    -   gRPC-Web (para streaming)
    -   WebSockets (para tiempo real)
    -   REST (entre Servidor de Reacciones y Servidor de Pagos)
-   **Proxy:**
    -   Envoy
-   **Herramientas de Build:**
    -   Node.js & npm
    -   Webpack

---

## 🚀 Cómo Ejecutar el Proyecto

Sigue estos pasos para levantar el sistema completo en tu entorno local.

### 1. Prerrequisitos

-   **Java 17** o superior y **Maven**.
-   **Node.js** y **npm**.
-   **Go** (para el servidor de streaming).
-   El ejecutable del proxy **Envoy**.

### 2. Configuración del Entorno

1.  Clona este repositorio en tu máquina local.
2.  Abre tres terminales separadas para los componentes del backend y frontend.

### 3. Levantar los Servidores (Backend)

En cada una de las dos terminales, navega a las carpetas correspondientes y ejecuta los servidores de Spring Boot:

-   **Servidor de Pagos:**
    ```bash
    cd ServidorPagos
    mvn spring-boot:run
    ```
    Este servidor se ejecutará en el puerto `6000`.

-   **Servidor de Reacciones:**
    ```bash
    cd ServidorReacciones
    mvn spring-boot:run
    ```
    Este servidor se ejecutará en el puerto `5000`.

### 4. Compilar el Cliente (Frontend)

En una tercera terminal, navega a la carpeta del cliente HTML y compila los módulos de JavaScript con Webpack:

```bash
cd cliente-html
npm install        # Solo la primera vez, para instalar dependencias
npx webpack
```
Esto generará el archivo `bundle.js` que contiene toda la lógica del cliente.

### 5. Ejecutar los Componentes de Streaming

Necesitarás dos terminales más para el servidor de streaming en Go y el proxy Envoy.

-   **Servidor de Streaming (Go):**
    ```bash
    cd servidor-streaming
    go run main/servidor.go
    ```
    El servidor gRPC escuchará en el puerto `50051`.

-   **Proxy Envoy:**
    Asegúrate de tener el ejecutable `envoyServer` y el archivo `envoyConfig2.yaml` en la misma carpeta.
    ```bash
    ./envoyServer -c envoyConfig2.yaml --disable-hot-restart
    ```
    Envoy escuchará en el puerto `8080`.

### 6. Acceder a la Aplicación

Finalmente, abre el archivo `cliente-html/index.html` en tu navegador web. ¡Y listo! Ya puedes pedir una canción, enviar reacciones y ver la magia en acción.

---

## 🧠 Conceptos Clave Implementados

### Tolerancia a Fallos con Spring Retry

Uno de los requisitos clave era simular fallos en el servidor de pagos y manejarlos con reintentos. Esto se implementó usando Spring Retry de forma declarativa:

-   El método que realiza la llamada HTTP está anotado con `@Retryable`.
-   Se configuró una política de **backoff exponencial** (`@Backoff`), que incrementa el tiempo de espera entre cada reintento para no saturar un servicio que podría estar recuperándose.
-   Se resolvió el problema de **autoinvocación** inyectando una referencia perezosa (`@Lazy`) del propio servicio para asegurar que las llamadas internas fueran interceptadas por el proxy de AOP de Spring.

### Identificación de Usuarios en WebSocket

Para que las notificaciones privadas (`/user/...`) funcionaran, era crucial asociar un `nickname` a cada sesión de WebSocket. Esto se logró implementando un `DefaultHandshakeHandler` personalizado:

1.  El cliente envía su `nickname` como un parámetro en la URL de conexión (`/ws?nickname=Juanito`).
2.  El `UserHandshakeHandler` en el backend intercepta esta conexión, extrae el `nickname` de la URL y crea un objeto `Principal` para esa sesión.
3.  Spring utiliza este `Principal` para enrutar correctamente los mensajes enviados con `messagingTemplate.convertAndSendToUser()`.
