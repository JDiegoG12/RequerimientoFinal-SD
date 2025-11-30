// Ubicación: co/edu/unicauca/servidorReacciones/capaControladores/ReaccionesController.java

package co.edu.unicauca.servidorReacciones.capaControladores;

import co.edu.unicauca.servidorReacciones.capaModelos.MensajeCancion;
import co.edu.unicauca.servidorReacciones.capaModelos.NotificacionPrivada;
import co.edu.unicauca.servidorReacciones.capaPagosCliente.PagosClientService;
import co.edu.unicauca.servidorReacciones.capaPagosCliente.models.PagoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controlador principal para gestionar las interacciones en tiempo real a través de WebSockets.
 * <p>
 * Este controlador utiliza STOMP sobre WebSocket para manejar los mensajes enviados por los clientes.
 * Es responsable de:
 * <ul>
 *     <li>Gestionar el estado de los usuarios que escuchan cada canción (play/pause).</li>
 *     <li>Procesar las reacciones enviadas por los usuarios.</li>
 *     <li>Orquestar la validación de pagos para cada reacción a través de {@link PagosClientService}.</li>
 *     <li>Distribuir los eventos (broadcast) a los canales de cada canción.</li>
 *     <li>Enviar notificaciones privadas a usuarios específicos en caso de errores o límites alcanzados.</li>
 * </ul>
 *
 */
@Controller
public class ReaccionesController {

    /**
     * Facilita el envío de mensajes a destinos STOMP (canales del broker).
     */
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Cliente para interactuar con el microservicio de Pagos.
     */
    @Autowired
    private PagosClientService pagosClientService;

    /**
     * Estructura de datos en memoria para mantener un registro de los usuarios
     * activos por cada canal de canción.
     * La clave es el ID de la canción y el valor es un conjunto de nicknames.
     */
    private final Map<String, Set<String>> usuariosPorCancion = new ConcurrentHashMap<>();

    /**
     * Maneja los mensajes enviados por el cliente al destino STOMP {@code /app/reproducir}.
     * <p>
     * Registra al usuario como activo en el canal de la canción y notifica a todos
     * los demás suscriptores de ese canal que un nuevo usuario ha comenzado a escuchar.
     *
     * @param mensaje El {@link MensajeCancion} que contiene el nickname del usuario y el ID de la canción.
     */
    @MessageMapping("/reproducir")
    public void procesarInicioReproduccion(@Payload MensajeCancion mensaje) {
        System.out.println("EVENTO PLAY: " + mensaje);
        registrarUsuarioEnCancion(mensaje.getIdCancion(), mensaje.getNickname());
        broadcastACanalCancion(mensaje);
    }

    /**
     * Maneja los mensajes enviados por el cliente al destino STOMP {@code /app/detener}.
     * <p>
     * Elimina al usuario de la lista de activos para la canción y notifica a los demás
     * suscriptores que el usuario ha pausado la reproducción.
     *
     * @param mensaje El {@link MensajeCancion} que contiene el nickname del usuario y el ID de la canción.
     */
    @MessageMapping("/detener")
    public void procesarFinReproduccion(@Payload MensajeCancion mensaje) {
        System.out.println("EVENTO PAUSE: " + mensaje);
        eliminarUsuarioDeCancion(mensaje.getIdCancion(), mensaje.getNickname());
        broadcastACanalCancion(mensaje);
    }

    /**
     * Maneja las reacciones enviadas por el cliente al destino STOMP {@code /app/reaccionar}.
     * <p>
     * Orquesta la validación del pago. Si el pago es exitoso, la reacción se distribuye (broadcast)
     * a todos los usuarios del canal. Si falla (por límite de saldo o error técnico), se envía una
     * notificación privada de vuelta al usuario que originó la reacción.
     *
     * @param mensaje El {@link MensajeCancion} que contiene los detalles de la reacción.
     */
    @MessageMapping("/reaccionar")
    public void procesarReaccion(@Payload MensajeCancion mensaje) {
        System.out.println("EVENTO REACCION (" + mensaje.getContenido() + "): " + mensaje);

        try {
            PagoResponse pagoResponse = pagosClientService.procesarPagoParaReaccion(mensaje);

            switch (pagoResponse.getEstado()) {
                case ACEPTADO:
                    System.out.println("[REACCIONES] Pago ACEPTADO. Reenviando reacción al canal.");
                    broadcastACanalCancion(mensaje);
                    break;

                case LIMITE_SUPERADO:
                    System.out.println("[REACCIONES] LIMITE_SUPERADO para " + mensaje.getNickname());
                    enviarNotificacionPrivada(
                        mensaje.getNickname(),
                        "LIMITE_ALCANZADO",
                        "Saldo Insuficiente",
                        "Has alcanzado el límite de $50 en reacciones."
                    );
                    break;
                
                default:
                     System.err.println("[REACCIONES] Error de negocio en pago para " + mensaje.getNickname() +
                                   ". Estado: " + pagoResponse.getEstado() +
                                   ". Mensaje: " + pagoResponse.getMensaje());
                    enviarNotificacionPrivada(
                        mensaje.getNickname(),
                        "ERROR_PAGO",
                        "Error en la Reacción",
                        pagoResponse.getMensaje()
                    );
                    break;
            }
        } catch (Exception e) {
            // Este bloque se activa si todos los reintentos en PagosClientService fallan.
            System.err.println("[REACCIONES] FALLO DEFINITIVO tras reintentos para " + mensaje.getNickname() +
                               ". Error: " + e.getMessage());
            enviarNotificacionPrivada(
                mensaje.getNickname(),
                "ERROR_PAGO",
                "Error en el Servidor",
                "No se pudo procesar tu reacción en este momento. Inténtalo de nuevo más tarde."
            );
        }
    }

    /**
     * Envía un mensaje a todos los clientes suscritos al canal de una canción específica.
     *
     * @param mensaje El {@link MensajeCancion} a difundir.
     */
    private void broadcastACanalCancion(MensajeCancion mensaje) {
        String destino = "/broker/canciones/" + mensaje.getIdCancion();
        System.out.println("Enviando broadcast a: " + destino);
        messagingTemplate.convertAndSend(destino, mensaje);
    }

    /**
     * Envía una notificación privada a un único usuario.
     * <p>
     * Utiliza el {@code nickname} del usuario para resolver el destino privado. El cliente debe estar
     * suscrito a {@code /user/queue/notificaciones} para recibir estos mensajes.
     *
     * @param nickname El nickname del usuario destino.
     * @param tipo Un identificador para que el cliente clasifique la notificación (ej. "ERROR_PAGO").
     * @param titulo Un título breve para la notificación.
     * @param mensaje El cuerpo detallado de la notificación.
     */
    private void enviarNotificacionPrivada(String nickname, String tipo, String titulo, String mensaje) {
        String destino = "/queue/notificaciones";
        NotificacionPrivada notificacion = new NotificacionPrivada(tipo, titulo, mensaje);
        System.out.println("Enviando notificación privada a " + nickname + " en " + destino + ": " + notificacion);
        messagingTemplate.convertAndSendToUser(nickname, destino, notificacion);
    }

    /**
     * Registra un usuario en el conjunto de oyentes de una canción.
     *
     * @param idCancion El identificador del canal de la canción.
     * @param nickname El nickname del usuario a registrar.
     */
    private void registrarUsuarioEnCancion(String idCancion, String nickname) {
        usuariosPorCancion
                .computeIfAbsent(idCancion, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(nickname);
        System.out.println("Usuarios activos en " + idCancion + ": " + usuariosPorCancion.get(idCancion));
    }

    /**
     * Elimina a un usuario del conjunto de oyentes de una canción.
     *
     * @param idCancion El identificador del canal de la canción.
     * @param nickname El nickname del usuario a eliminar.
     */
    private void eliminarUsuarioDeCancion(String idCancion, String nickname) {
        Set<String> usuarios = usuariosPorCancion.get(idCancion);
        if (usuarios != null) {
            usuarios.remove(nickname);
            if (usuarios.isEmpty()) {
                usuariosPorCancion.remove(idCancion);
            }
        }
        System.out.println("Usuarios activos en " + idCancion + ": " + usuariosPorCancion.getOrDefault(idCancion, Collections.emptySet()));
    }
}