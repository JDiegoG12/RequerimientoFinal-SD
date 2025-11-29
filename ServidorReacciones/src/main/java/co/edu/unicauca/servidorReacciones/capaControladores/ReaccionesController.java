package co.edu.unicauca.servidorReacciones.capaControladores;

import co.edu.unicauca.servidorReacciones.capaModelos.MensajeCancion;
import co.edu.unicauca.servidorReacciones.capaPagosCliente.EstadoPago;
import co.edu.unicauca.servidorReacciones.capaPagosCliente.PagoResponse;
import co.edu.unicauca.servidorReacciones.capaPagosCliente.PagosClientService;
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
 * Controlador encargado de procesar los mensajes relacionados con
 * la reproducción de canciones y las reacciones de los usuarios.
 *
 * <p>Este controlador recibe mensajes enviados por el cliente web a los
 * destinos STOMP con prefijo {@code /app} y los redirige a los tópicos
 * correspondientes del broker (prefijo {@code /broker}), agrupando
 * a los usuarios por canción.</p>
 */
@Controller
public class ReaccionesController {

    /**
     * Plantilla de mensajería utilizada para enviar mensajes a los
     * diferentes tópicos del broker STOMP.
     */
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Cliente HTTP para comunicarse con el servidor de pagos y
     * procesar los pagos asociados a las reacciones.
     */
    @Autowired
    private PagosClientService pagosClientService;

    /**
     * Estructura en memoria que mantiene la lista de usuarios activos
     * por canción.
     *
     * <p>Clave: identificador de la canción (idCancion).<br>
     * Valor: conjunto de nicknames actualmente reproduciendo esa canción.</p>
     */
    private final Map<String, Set<String>> usuariosPorCancion = new ConcurrentHashMap<>();

    @MessageMapping("/reproducir")
    public void procesarInicioReproduccion(@Payload MensajeCancion mensaje) {
        System.out.println("EVENTO PLAY: " + mensaje);
        registrarUsuarioEnCancion(mensaje.getIdCancion(), mensaje.getNickname());
        enviarAlCanalPrivado(mensaje);
    }

    @MessageMapping("/detener")
    public void procesarFinReproduccion(@Payload MensajeCancion mensaje) {
        System.out.println("EVENTO PAUSE: " + mensaje);
        eliminarUsuarioDeCancion(mensaje.getIdCancion(), mensaje.getNickname());
        enviarAlCanalPrivado(mensaje);
    }

    /**
     * Procesa las reacciones que un usuario envía mientras reproduce
     * una canción. Antes de reenviar la reacción:
     * <ul>
     *   <li>Solicita y usa un token en el servidor de pagos.</li>
     *   <li>Aplica la lógica de reintentos ante errores simulados.</li>
     *   <li>Solo reenvía la reacción si el pago es ACEPTADO.</li>
     * </ul>
     *
     * @param mensaje mensaje con la información de la reacción
     */
    @MessageMapping("/reaccionar")
    public void procesarReaccion(@Payload MensajeCancion mensaje) {
        System.out.println("EVENTO REACCION (" + mensaje.getContenido() + "): " + mensaje);

        // Procesar pago asociado a esta reacción en el servidor de pagos
        PagoResponse pagoResponse = pagosClientService.procesarPagoParaReaccion(mensaje);

        if (pagoResponse == null) {
            System.out.println("[REACCIONES] No se obtuvo respuesta del servidor de pagos. No se reenvía la reacción.");
            return;
        }

        EstadoPago estado = pagoResponse.getEstado();
        System.out.println("[REACCIONES] Resultado del pago: " + estado + " - " + pagoResponse.getMensaje());

        // Solo reenviar la reacción si el pago fue aceptado
        if (estado == EstadoPago.ACEPTADO) {
            enviarAlCanalPrivado(mensaje);
        } else {
            // TOKEN_REPETIDO, LIMITE_SUPERADO o ERROR_SIMULADO (tras reintentos)
            System.out.println("[REACCIONES] Reacción no reenviada debido al estado del pago: " + estado);
        }
    }

    private void enviarAlCanalPrivado(MensajeCancion mensaje) {
        String canalDestino = "/broker/canciones/" + mensaje.getIdCancion();
        messagingTemplate.convertAndSend(canalDestino, mensaje);
    }

    private void registrarUsuarioEnCancion(String idCancion, String nickname) {
        usuariosPorCancion
                .computeIfAbsent(idCancion, k ->
                        Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(nickname);

        System.out.println("Usuarios activos en canción " + idCancion + ": "
                + usuariosPorCancion.get(idCancion));
    }

    private void eliminarUsuarioDeCancion(String idCancion, String nickname) {
        Set<String> usuarios = usuariosPorCancion.get(idCancion);
        if (usuarios != null) {
            usuarios.remove(nickname);
            if (usuarios.isEmpty()) {
                usuariosPorCancion.remove(idCancion);
            }
        }

        System.out.println("Usuarios activos en canción " + idCancion + ": "
                + usuariosPorCancion.getOrDefault(idCancion, Collections.emptySet()));
    }
}
