// Ubicación: co/edu/unicauca/servidorReacciones/capaConfiguracionWebSocket/UserHandshakeHandler.java

package co.edu.unicauca.servidorReacciones.capaConfiguracionWebSocket;

import co.edu.unicauca.servidorReacciones.capaControladores.StompPrincipal;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * Manejador personalizado para el "apretón de manos" (handshake) de la conexión WebSocket.
 * <p>
 * Esta clase extiende {@link DefaultHandshakeHandler} para interceptar el proceso de
 * establecimiento de la conexión y determinar la identidad del usuario ({@link Principal})
 * antes de que la sesión WebSocket sea completamente establecida.
 * <p>
 * Su principal responsabilidad es extraer el {@code nickname} del usuario, que el cliente
 * envía como un parámetro de consulta en la URL de conexión (ej. {@code /ws?nickname=Juanito}).
 * Luego, crea un {@link StompPrincipal} con ese nickname.
 * <p>
 * Este proceso es fundamental para que Spring pueda enrutar correctamente los mensajes
 * privados dirigidos a destinos {@code /user/...}.
 *
 * @see WebSocketConfig
 * @see StompPrincipal
 */
public class UserHandshakeHandler extends DefaultHandshakeHandler {

    /**
     * Determina el usuario asociado a una sesión WebSocket durante el handshake.
     * <p>
     * Este método se invoca antes de que la conexión se actualice al protocolo WebSocket.
     * Extrae el parámetro de consulta 'nickname' de la URI de la solicitud HTTP
     * y lo utiliza para crear un objeto {@link Principal}. Si no se encuentra el nickname,
     * se asigna un nombre de usuario anónimo y aleatorio.
     *
     * @param request La solicitud de handshake actual.
     * @param wsHandler El manejador de WebSocket.
     * @param attributes Un mapa de atributos que se asociarán con la sesión WebSocket.
     * @return un {@link Principal} que representa al usuario. Spring lo utilizará para
     *         la autenticación y el enrutamiento de mensajes de usuario.
     */
    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        System.out.println("\n--- [DEBUG] Iniciando Handshake para nueva conexión WebSocket ---");
        System.out.println("URI de la petición: " + request.getURI());

        String nickname = null;
        try {
            // Se utiliza UriComponentsBuilder para parsear de forma segura los parámetros de la URL.
            nickname = UriComponentsBuilder.fromUri(request.getURI())
                    .build()
                    .getQueryParams()
                    .getFirst("nickname");
        } catch (Exception e) {
            System.err.println("No se pudo parsear el nickname desde la URI: " + e.getMessage());
        }

        if (nickname == null || nickname.trim().isEmpty()) {
            System.err.println("[DEBUG] Parámetro 'nickname' NO encontrado en la URL. Asignando usuario anónimo.");
            nickname = "anon-" + UUID.randomUUID().toString().substring(0, 8);
        } else {
            System.out.println("[DEBUG] Parámetro 'nickname' encontrado: '" + nickname + "'. Creando Principal.");
        }

        System.out.println("--- [DEBUG] Fin del Handshake ---\n");
        
        // Se crea y devuelve el objeto Principal que Spring utilizará internamente.
        return new StompPrincipal(nickname);
    }
}