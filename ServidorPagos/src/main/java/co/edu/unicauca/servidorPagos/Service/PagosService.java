package co.edu.unicauca.servidorPagos.Service;

import co.edu.unicauca.servidorPagos.Models.EstadoPago;
import co.edu.unicauca.servidorPagos.Models.PagoRequest;
import co.edu.unicauca.servidorPagos.Models.PagoResponse;
import co.edu.unicauca.servidorPagos.Models.TokenResponse;
import co.edu.unicauca.servidorPagos.Repository.PagosRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Servicio de negocio responsable de gestionar la generación
 * de tokens y el registro de pagos asociados a reacciones.
 *
 * <p>En esta implementación, toda la información se almacena en
 * memoria a través de {@link PagosRepository}, lo cual es suficiente
 * para el laboratorio y permite ejecutar rápidamente las pruebas.</p>
 *
 * <p>Responsabilidades principales:</p>
 * <ul>
 *   <li>Generar tokens únicos.</li>
 *   <li>Registrar pagos asociados a tokens.</li>
 *   <li>Evitar el uso repetido de un mismo token.</li>
 *   <li>Aplicar el límite de $50 por usuario (5 reacciones).</li>
 *   <li>Simular fallos en algunos pagos para probar reintentos.</li>
 * </ul>
 */
@Service
public class PagosService {

    /**
     * Repositorio en memoria que almacena tokens usados
     * y totales acumulados por usuario.
     */
    @Autowired
    private PagosRepository pagosRepository;

    /**
     * Generador seguro de valores aleatorios para los tokens.
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Límite máximo de dinero que un usuario puede acumular
     * por reacciones. En el requerimiento, 5 reacciones x $10 = $50.
     */
    private static final int LIMITE_POR_USUARIO = 50;

    /**
     * Genera un nuevo token único que será utilizado posteriormente
     * para registrar un pago.
     *
     * @return respuesta con el token generado
     */
    public TokenResponse generarToken() {
        String token = generarTokenAleatorio();
        return new TokenResponse(token);
    }

    /**
     * Registra un nuevo pago asociado a una reacción, aplicando
     * las reglas de negocio definidas en el requerimiento.
     *
     * <p>Reglas:</p>
     * <ul>
     *   <li>Si el token ya fue usado, se rechaza el pago con
     *       estado {@link EstadoPago#TOKEN_REPETIDO}.</li>
     *   <li>Si el usuario superaría el límite de $50 con este pago,
     *       se rechaza con estado {@link EstadoPago#LIMITE_SUPERADO}.</li>
     *   <li>En algunos casos se puede simular un error para probar
     *       el mecanismo de reintentos, usando el estado
     *       {@link EstadoPago#ERROR_SIMULADO}.</li>
     *   <li>Si todo es correcto, se marca el token como usado,
     *       se actualiza el total del usuario y se devuelve
     *       {@link EstadoPago#ACEPTADO}.</li>
     * </ul>
     *
     * @param request datos del pago a registrar
     * @return información del resultado del pago
     */
    public PagoResponse registrarPago(PagoRequest request) {
        String token = request.getToken();
        String nickname = request.getNickname();
        int valor = request.getValor();

        // 1. Validar token repetido
        if (pagosRepository.esTokenUsado(token)) {
            return new PagoResponse(
                    EstadoPago.TOKEN_REPETIDO,
                    "El token ya fue utilizado previamente",
                    pagosRepository.obtenerTotalUsuario(nickname)
            );
        }

        // 2. Validar límite de $50 por usuario
        int totalActual = pagosRepository.obtenerTotalUsuario(nickname);
        int nuevoTotal = totalActual + valor;
        if (nuevoTotal > LIMITE_POR_USUARIO) {
            return new PagoResponse(
                    EstadoPago.LIMITE_SUPERADO,
                    "El usuario alcanzó el límite de $" + LIMITE_POR_USUARIO,
                    totalActual
            );
        }

        // 3. Simular error de pago para probar reintentos
        if (debeSimularError(request)) {
            return new PagoResponse(
                    EstadoPago.ERROR_SIMULADO,
                    "Error simulado en el servidor de pagos",
                    totalActual
            );
        }

        // 4. Registrar pago: marcar token como usado y actualizar total
        pagosRepository.marcarTokenComoUsado(token);
        pagosRepository.actualizarTotalUsuario(nickname, nuevoTotal);

        String mensajeExito = String.format(
                "Pago aceptado. Usuario=%s, Cancion=%s, Valor=%d, Total acumulado=%d",
                nickname, request.getIdCancion(), valor, nuevoTotal
        );

        // Eco del pago en consola (requerimiento de mostrar pagos)
        System.out.println("[SERVIDOR PAGOS] " + mensajeExito);

        return new PagoResponse(
                EstadoPago.ACEPTADO,
                mensajeExito,
                nuevoTotal
        );
    }

    /**
     * Indica si para este pago se debe simular un error.
     * Aquí puedes ajustar la lógica según te convenga para
     * demostrar los reintentos en la sustentación.
     *
     * @param request pago que se está procesando
     * @return true si se debe simular error, false en caso contrario
     */
    private boolean debeSimularError(PagoRequest request) {
        // Ejemplo sencillo: simular error cuando el idCancion termina en "X"
        String idCancion = request.getIdCancion();
        return idCancion != null && idCancion.endsWith("X");
    }

    /**
     * Genera un token aleatorio codificado en Base64 usando
     * un generador seguro de números aleatorios.
     *
     * @return cadena que representa el token
     */
    private String generarTokenAleatorio() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
