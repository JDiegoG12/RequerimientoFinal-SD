// Ubicación: co/edu/unicauca/servidorReacciones/capaControladores/StompPrincipal.java

package co.edu.unicauca.servidorReacciones.capaControladores;

import java.security.Principal;

/**
 * Representa a un usuario autenticado dentro del contexto de una sesión WebSocket/STOMP.
 * <p>
 * Esta implementación simple de la interfaz {@link java.security.Principal} se utiliza
 * para asociar un nombre de usuario (en este caso, el {@code nickname}) con una sesión de WebSocket.
 * <p>
 * Spring utiliza este objeto {@code Principal} para resolver los destinos de mensajes privados.
 * Cuando se envía un mensaje a un destino como {@code /user/queue/notificaciones}, Spring
 * identifica el nombre del principal de la sesión (ej. "Juanito") y traduce el destino a
 * uno específico para ese usuario (ej. {@code /queue/notificaciones-user<session-id>}).
 * <p>
 * Una instancia de esta clase es creada y asignada a cada sesión por el
 * {@link co.edu.unicauca.servidorReacciones.capaConfiguracionWebSocket.UserHandshakeHandler}
 * durante el proceso de "handshake" de la conexión.
 *
 * @see co.edu.unicauca.servidorReacciones.capaConfiguracionWebSocket.UserHandshakeHandler
 */
public class StompPrincipal implements Principal {

    /**
     * El nombre del usuario, que corresponde al nickname proporcionado por el cliente.
     */
    private final String name;

    /**
     * Construye una nueva instancia de StompPrincipal.
     *
     * @param name El nombre del usuario (nickname) que será asociado a la sesión WebSocket.
     *             No debe ser nulo.
     */
    public StompPrincipal(String name) {
        this.name = name;
    }

    /**
     * Devuelve el nombre de este principal.
     * <p>
     * Este es el método clave que Spring invocará para identificar al usuario
     * al enrutar mensajes privados.
     *
     * @return El nickname del usuario asociado a esta sesión.
     */
    @Override
    public String getName() {
        return name;
    }
}