package co.edu.unicauca.servidorReacciones.capaConfiguracionWebSocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuración central del soporte WebSocket/STOMP del servidor de reacciones.
 *
 * <p>Responsabilidades principales:</p>
 * <ul>
 *   <li>Habilitar el uso de STOMP sobre WebSocket en la aplicación Spring.</li>
 *   <li>Definir el endpoint WebSocket al que se conecta el cliente web.</li>
 *   <li>Registrar un HandshakeHandler para identificar a los usuarios por su nickname.</li>
 *   <li>Configurar el broker de mensajes interno y los prefijos de destino.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Registra los endpoints STOMP a los que se conectarán los clientes
     * para establecer la comunicación WebSocket.
     *
     * @param registry registro de endpoints STOMP
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint principal WebSocket para el cliente web.
        registry.addEndpoint("/ws")
                // **CLAVE**: Registra el manejador que asocia el nickname del usuario
                // con la sesión WebSocket, permitiendo el envío de mensajes privados.
                .setHandshakeHandler(new UserHandshakeHandler())
                // Permite conexiones desde cualquier origen (CORS)
                .setAllowedOriginPatterns("*")
                // Habilita SockJS para compatibilidad con navegadores antiguos.
                .withSockJS();
    }

    /**
     * Configura el broker de mensajes simple en memoria y los prefijos
     * que utilizarán los destinos dentro de la aplicación.
     *
     * @param config objeto de configuración del broker de mensajes
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Habilita un broker de mensajes en memoria para enviar mensajes a los clientes
        // en destinos que comiencen con "/broker" (para canales públicos) y "/queue" (para mensajes privados).
        config.enableSimpleBroker("/broker", "/queue");

        // Define el prefijo "/app" para los mensajes que son enrutados a los métodos
        // @MessageMapping en los controladores. Ejemplo: el cliente envía a /app/reaccionar.
        config.setApplicationDestinationPrefixes("/app");

        // Define el prefijo que se usa para los destinos de usuario.
        // Esto es crucial para que SimpMessagingTemplate.convertAndSendToUser funcione.
        // El cliente se suscribirá a "/user/queue/notificaciones".
        config.setUserDestinationPrefix("/user");
    }
}