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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio de negocio responsable de gestionar la generación
 * de tokens y el registro de pagos asociados a reacciones.
 *
 * En esta implementación, toda la información se almacena en
 * memoria a través de {@link PagosRepository}, lo cual es suficiente
 * para el laboratorio y permite ejecutar rápidamente las pruebas.
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
     * Contador global de intentos de registro de pago.
     * Cada vez que se procesa un PagoRequest se incrementa.
     * Si el valor es múltiplo de 4 (4, 8, 12, ...), se devuelve
     * un ERROR_SIMULADO para probar los reintentos.
     */
    private final AtomicInteger contadorIntentosPago = new AtomicInteger(0);

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
     * Reglas:
     * - Si el token ya fue usado, TOKEN_REPETIDO.
     * - Si el usuario superaría $50, LIMITE_SUPERADO.
     * - Cada pago global nº 4, 8, 12, ... produce ERROR_SIMULADO.
     * - En caso contrario, se acepta el pago.
     *
     * @param request datos del pago a registrar
     * @return información del resultado del pago
     */
    public PagoResponse registrarPago(PagoRequest request) {
        String token = request.getToken();
        String nickname = request.getNickname();
        int valor = request.getValor();

        int intentoActual = contadorIntentosPago.incrementAndGet();
        System.out.println("[SERVIDOR PAGOS] Procesando intento global de pago #" + intentoActual
                + " para usuario=" + nickname
                + ", cancion=" + request.getIdCancion()
                + ", valor=" + valor);

        // 0. Simular error cada intento múltiplo de 4
        if (intentoActual % 4 == 0) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            int totalActual = pagosRepository.obtenerTotalUsuario(nickname);
            System.out.println("[SERVIDOR PAGOS] Simulando ERROR_SIMULADO en intento #" + intentoActual);
            return new PagoResponse(
                    EstadoPago.ERROR_SIMULADO,
                    "Error simulado en el servidor de pagos (intento #" + intentoActual + ")",
                    totalActual);
        }

        // 1. Validar token repetido
        if (pagosRepository.esTokenUsado(token)) {
            return new PagoResponse(
                    EstadoPago.TOKEN_REPETIDO,
                    "El token ya fue utilizado previamente",
                    pagosRepository.obtenerTotalUsuario(nickname));
        }

        // 2. Validar límite de $50 por usuario
        int totalActual = pagosRepository.obtenerTotalUsuario(nickname);
        int nuevoTotal = totalActual + valor;
        if (nuevoTotal > LIMITE_POR_USUARIO) {
            return new PagoResponse(
                    EstadoPago.LIMITE_SUPERADO,
                    "El usuario alcanzó el límite de $" + LIMITE_POR_USUARIO,
                    totalActual);
        }

        // 3. Registrar pago: marcar token como usado y actualizar total
        pagosRepository.marcarTokenComoUsado(token);
        pagosRepository.actualizarTotalUsuario(nickname, nuevoTotal);

        String mensajeExito = String.format(
                "Pago aceptado. Usuario=%s, Cancion=%s, Valor=%d, Total acumulado=%d",
                nickname, request.getIdCancion(), valor, nuevoTotal);

        // Eco del pago en consola (requerimiento de mostrar pagos)
        System.out.println("[SERVIDOR PAGOS] " + mensajeExito);

        return new PagoResponse(
                EstadoPago.ACEPTADO,
                mensajeExito,
                nuevoTotal);
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
