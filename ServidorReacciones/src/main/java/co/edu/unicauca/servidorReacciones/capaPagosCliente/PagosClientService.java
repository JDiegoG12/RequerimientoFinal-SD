// Ubicación: co/edu/unicauca/servidorReacciones/capaPagosCliente/PagosClientService.java

package co.edu.unicauca.servidorReacciones.capaPagosCliente;

import co.edu.unicauca.servidorReacciones.capaModelos.MensajeCancion;
import co.edu.unicauca.servidorReacciones.capaPagosCliente.exceptions.PagoSimuladoFallidoException;
import co.edu.unicauca.servidorReacciones.capaPagosCliente.models.EstadoPago;
import co.edu.unicauca.servidorReacciones.capaPagosCliente.models.PagoRequest;
import co.edu.unicauca.servidorReacciones.capaPagosCliente.models.PagoResponse;
import co.edu.unicauca.servidorReacciones.capaPagosCliente.models.TokenResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Cliente HTTP para comunicarse con el microservicio de Pagos.
 * <p>
 * Esta clase orquesta el procesamiento de pagos para las reacciones de los usuarios,
 * implementando una política de reintentos robusta utilizando Spring Retry ({@code @Retryable})
 * para manejar fallos transitorios, como errores de red o errores simulados por el servidor de pagos.
 * <p>
 * Utiliza un patrón de autoinyección ({@code @Lazy}) para asegurar que las llamadas a métodos
 * anotados con {@code @Retryable} sean interceptadas por el proxy de AOP de Spring,
 * incluso cuando se invocan desde la misma clase, evitando así el problema de la autoinvocación.
 */
@Service
public class PagosClientService {

    private static final String BASE_URL_PAGOS = "http://localhost:6000/api/pagos";
    private static final int MAX_INTENTOS = 4; // 1 intento inicial + 3 reintentos
    private static final long ESPERA_INICIAL_MS = 1500L;
    private static final double MULTIPLICADOR_BACKOFF = 1.5;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Referencia al propio proxy del servicio, inyectado de forma perezosa.
     * Es crucial para evitar dependencias circulares y para asegurar que las llamadas
     * internas a métodos {@code @Retryable} sean interceptadas por Spring AOP.
     */
    @Autowired
    @Lazy
    private PagosClientService self;

    /**
     * Orquesta el proceso completo de pago para una reacción.
     * <p>
     * Este es el punto de entrada principal. Delega la ejecución al método {@code realizarIntentoDePago}
     * a través de la referencia {@code self} para garantizar que la lógica de reintentos de Spring se active.
     *
     * @param mensaje El objeto {@link MensajeCancion} que contiene los detalles de la reacción (usuario, canción).
     * @return Un objeto {@link PagoResponse} con el resultado final de la transacción.
     */
    public PagoResponse procesarPagoParaReaccion(MensajeCancion mensaje) {
        System.out.println("[REACCIONES] Iniciando procesamiento de pago para " + mensaje.getNickname());
        return self.realizarIntentoDePago(mensaje);
    }

    /**
     * Realiza un intento completo de procesar un pago, incluyendo la obtención de un nuevo token.
     * <p>
     * Este método contiene la lógica central que será reintentada en caso de fallo. La obtención de un
     * nuevo token en cada ejecución es fundamental para que los reintentos no fallen por usar un token
     * ya visto ({@link EstadoPago#TOKEN_REPETIDO}).
     * <p>
     * La anotación {@code @Retryable} configura a Spring para reintentar este método si lanza
     * {@link RestClientException} o {@link PagoSimuladoFallidoException}.
     *
     * @param mensaje El objeto {@link MensajeCancion} con los datos de la reacción.
     * @return Un {@link PagoResponse} si el pago se procesa (con estado ACEPTADO, LIMITE_SUPERADO, etc.).
     * @throws PagoSimuladoFallidoException Si el servidor de pagos responde explícitamente con {@link EstadoPago#ERROR_SIMULADO}.
     * @throws RestClientException Si ocurre un error de comunicación con el servidor de pagos.
     */
    @Retryable(
        value = { RestClientException.class, PagoSimuladoFallidoException.class },
        maxAttempts = MAX_INTENTOS,
        backoff = @Backoff(delay = ESPERA_INICIAL_MS, multiplier = MULTIPLICADOR_BACKOFF)
    )
    public PagoResponse realizarIntentoDePago(MensajeCancion mensaje) throws PagoSimuladoFallidoException, RestClientException {
        System.out.println("[REACCIONES] Realizando intento de pago para: " + mensaje.getNickname());

        // 1. Solicitar un NUEVO token en CADA intento.
        TokenResponse tokenResponse = solicitarToken();
        if (tokenResponse == null || tokenResponse.getToken() == null) {
            System.err.println("[REACCIONES] No se pudo obtener token. Lanzando excepción para reintento.");
            throw new RestClientException("No se pudo obtener un token desde el servidor de pagos");
        }
        System.out.println("[REACCIONES] Token nuevo para este intento: " + tokenResponse.getToken());

        // 2. Construir la petición de pago con el nuevo token.
        PagoRequest pagoRequest = new PagoRequest(
                tokenResponse.getToken(),
                mensaje.getNickname(),
                mensaje.getIdCancion(),
                10
        );

        // 3. Enviar la petición de pago al servidor.
        HttpEntity<PagoRequest> requestEntity = new HttpEntity<>(pagoRequest);
        ResponseEntity<PagoResponse> response = restTemplate.exchange(
                BASE_URL_PAGOS,
                HttpMethod.POST,
                requestEntity,
                PagoResponse.class
        );

        PagoResponse pagoResponse = response.getBody();

        if (pagoResponse == null) {
            throw new RestClientException("La respuesta del servidor de pagos fue nula.");
        }

        if (pagoResponse.getEstado() == EstadoPago.ERROR_SIMULADO) {
            System.out.println("[REACCIONES] Servidor devolvió ERROR_SIMULADO. Mensaje: " + pagoResponse.getMensaje());
            throw new PagoSimuladoFallidoException(pagoResponse.getMensaje());
        }

        System.out.println("[REACCIONES] Pago procesado. Estado final: " + pagoResponse.getEstado());
        return pagoResponse;
    }

    /**
     * Método de recuperación (fallback) que se ejecuta si todos los reintentos de {@code realizarIntentoDePago} fracasan.
     * <p>
     * Anotado con {@code @Recover}, este método previene que una excepción no controlada se propague,
     * registrando el fallo definitivo y devolviendo una respuesta de error estandarizada.
     *
     * @param e La excepción final que causó el cese de los reintentos.
     * @param mensaje Los argumentos originales del método {@code @Retryable} fallido.
     * @return Un {@link PagoResponse} con estado {@link EstadoPago#ERROR_SIMULADO} indicando el fallo definitivo.
     */
    @Recover
    public PagoResponse recuperarDeFalloDePago(Exception e, MensajeCancion mensaje) {
        System.err.println("[REACCIONES] FALLO DEFINITIVO: Se agotaron los reintentos para el pago de "
                + mensaje.getNickname() + ". Error final: " + e.getMessage());
        return crearRespuestaDeFallo("No se pudo completar el pago tras " + MAX_INTENTOS + " intentos.");
    }

    /**
     * Realiza una llamada HTTP POST para obtener un nuevo token del servidor de pagos.
     *
     * @return Un objeto {@link TokenResponse} si la solicitud es exitosa, o {@code null} si ocurre un error de comunicación.
     */
    private TokenResponse solicitarToken() {
        try {
            System.out.println("[REACCIONES] Solicitando token al servidor de pagos...");
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                    BASE_URL_PAGOS + "/token", null, TokenResponse.class);
            return response.getBody();
        } catch (RestClientException e) {
            System.err.println("[REACCIONES] Error de comunicación al solicitar token: " + e.getMessage());
            return null;
        }
    }

    /**
     * Método de utilidad para crear un objeto {@link PagoResponse} estandarizado para casos de fallo.
     *
     * @param mensaje El mensaje descriptivo del error.
     * @return Un {@link PagoResponse} preconfigurado con estado {@link EstadoPago#ERROR_SIMULADO}.
     */
    private PagoResponse crearRespuestaDeFallo(String mensaje) {
        PagoResponse errorResponse = new PagoResponse();
        errorResponse.setEstado(EstadoPago.ERROR_SIMULADO);
        errorResponse.setMensaje(mensaje);
        errorResponse.setTotalAcumuladoUsuario(0);
        return errorResponse;
    }
}