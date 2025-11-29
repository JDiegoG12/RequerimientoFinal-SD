package co.edu.unicauca.servidorReacciones.capaPagosCliente;

import co.edu.unicauca.servidorReacciones.capaModelos.MensajeCancion;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Cliente HTTP que permite al servidor de reacciones comunicarse
 * con el servidor de pagos.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Solicitar un token de pago al servidor de pagos.</li>
 *   <li>Enviar la petición de registro de pago por cada reacción.</li>
 *   <li>Implementar un mecanismo de reintentos con temporizador
 *       cuando se simula un error en el servidor de pagos.</li>
 * </ul>
 */
@Service
public class PagosClientService {

    /**
     * URL base del servidor de pagos.
     * Ajusta host/puerto si es necesario.
     */
    private static final String BASE_URL_PAGOS = "http://localhost:6000/api/pagos";

    /**
     * Número máximo de reintentos cuando se produce un error simulado.
     */
    private static final int MAX_REINTENTOS = 3;

    /**
     * Tiempo de espera (en milisegundos) entre reintentos.
     */
    private static final long ESPERA_ENTRE_REINTENTOS_MS = 2000L;

    /**
     * Cliente HTTP simple para consumir los endpoints REST
     * del servidor de pagos.
     */
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Procesa el pago asociado a una reacción representada por
     * un {@link MensajeCancion}. Este método:
     * <ol>
     *   <li>Solicita un token al servidor de pagos.</li>
     *   <li>Intenta registrar el pago usando ese token.</li>
     *   <li>Si se produce un {@link EstadoPago#ERROR_SIMULADO},
     *       reintenta hasta {@link #MAX_REINTENTOS} veces, con una
     *       espera de {@link #ESPERA_ENTRE_REINTENTOS_MS} entre intentos.</li>
     * </ol>
     *
     * @param mensaje mensaje de reacción recibido desde el cliente Web
     * @return respuesta final del servidor de pagos después de aplicar
     * los reintentos (si los hubo)
     */
    public PagoResponse procesarPagoParaReaccion(MensajeCancion mensaje) {
        // 1. Solicitar token
        TokenResponse tokenResponse = solicitarToken();
        if (tokenResponse == null || tokenResponse.getToken() == null) {
            // En caso de fallo al obtener token, se simula un error
            PagoResponse error = new PagoResponse();
            error.setEstado(EstadoPago.ERROR_SIMULADO);
            error.setMensaje("No se pudo obtener un token desde el servidor de pagos");
            error.setTotalAcumuladoUsuario(0);
            return error;
        }

        String token = tokenResponse.getToken();

        // 2. Construir la petición de pago
        PagoRequest pagoRequest = new PagoRequest(
                token,
                mensaje.getNickname(),
                mensaje.getIdCancion(),
                10 // Cada reacción vale $10 según el requerimiento
        );

        // 3. Intentar registrar el pago con reintentos en caso de ERROR_SIMULADO
        PagoResponse respuesta = null;
        int intento = 0;

        while (intento <= MAX_REINTENTOS) {
            intento++;

            respuesta = enviarPago(pagoRequest);

            if (respuesta == null) {
                // Error no controlado en la comunicación
                System.out.println("[REACCIONES] Error al comunicarse con servidor de pagos. Intento " + intento);
            } else if (respuesta.getEstado() == EstadoPago.ERROR_SIMULADO) {
                System.out.println("[REACCIONES] Pago con ERROR_SIMULADO. Intento " + intento);
            } else {
                // Estado distinto a ERROR_SIMULADO: devolver respuesta tal cual
                return respuesta;
            }

            // Si aún quedan reintentos, esperar antes del siguiente
            if (intento <= MAX_REINTENTOS) {
                esperarAntesDeReintentar();
            }
        }

        // Si se agotaron los reintentos y siempre hubo ERROR_SIMULADO o fallo de comunicación,
        // devolver la última respuesta recibida (o una genérica si fue null).
        if (respuesta == null) {
            respuesta = new PagoResponse();
            respuesta.setEstado(EstadoPago.ERROR_SIMULADO);
            respuesta.setMensaje("No se pudo completar el pago tras varios reintentos");
            respuesta.setTotalAcumuladoUsuario(0);
        }

        return respuesta;
    }

    /**
     * Solicita un nuevo token al servidor de pagos usando
     * el endpoint POST /api/pagos/token.
     *
     * @return respuesta con el token generado o null si ocurre un error
     */
    private TokenResponse solicitarToken() {
        try {
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    BASE_URL_PAGOS + "/token",
                    null,
                    TokenResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            System.out.println("[REACCIONES] Error solicitando token al servidor de pagos: " + e.getMessage());
            return null;
        }
    }

    /**
     * Envía la petición de registro de pago al servidor de pagos
     * usando el endpoint POST /api/pagos.
     *
     * @param pagoRequest datos del pago a registrar
     * @return respuesta del servidor de pagos o null si ocurre un error
     */
    private PagoResponse enviarPago(PagoRequest pagoRequest) {
        try {
            HttpEntity<PagoRequest> requestEntity = new HttpEntity<>(pagoRequest);
            ResponseEntity<PagoResponse> response = restTemplate.exchange(
                    BASE_URL_PAGOS,
                    HttpMethod.POST,
                    requestEntity,
                    PagoResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            System.out.println("[REACCIONES] Error enviando pago al servidor de pagos: " + e.getMessage());
            return null;
        }
    }

    /**
     * Espera un tiempo fijo antes de realizar un nuevo intento de pago.
     * Este método implementa el temporizador solicitado en el
     * requerimiento para los reintentos.
     */
    private void esperarAntesDeReintentar() {
        try {
            Thread.sleep(ESPERA_ENTRE_REINTENTOS_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
