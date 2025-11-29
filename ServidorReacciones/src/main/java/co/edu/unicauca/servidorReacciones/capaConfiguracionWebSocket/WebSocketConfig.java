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
 *   <li>Configurar el broker de mensajes interno y los prefijos de destino.</li>
 * </ul>
 *
 * <p>Flujo general:</p>
 * <ol>
 *   <li>El cliente se conecta al endpoint de>/ws</code> usando SockJS/STOMP.</li>
 *   <li>Los mensajes que el cliente envía con destino de>/app/**</code>
 *       son manejados por los controladores anotados con
 *       de>@MessageMapping</code> (por ejemplo, {@code /app/reproducir}).</li>
 *   <li>El servidor publica mensajes en destinos que empiezan por
 *       de>/broker/**</code>, a los cuales los clientes se pueden suscribir
 *       (por ejemplo, de>/broker/canciones/{idCancion}</code>).</li>
 * </ol>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configura el broker de mensajes simple en memoria y los prefijos
     * que utilizarán los destinos dentro de la aplicación.
     *
     * @param config objeto de configuración del broker de mensajes
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Prefijo para los tópicos a los que los clientes se suscriben.
        // Ejemplo de destino: /broker/canciones/123
        config.enableSimpleBroker("/broker");

        // Prefijo para los destinos a los que los clientes envían mensajes
        // que serán manejados por métodos @MessageMapping.
        // Ejemplo: el cliente envía a /app/reproducir.
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Registra los endpoints STOMP a los que se conectarán los clientes
     * para establecer la comunicación WebSocket.
     *
     * @param registry registro de endpoints STOMP
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint principal WebSocket para el cliente web.
        // Se habilita SockJS para compatibilidad con navegadores
        // que no soportan WebSocket nativo.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
