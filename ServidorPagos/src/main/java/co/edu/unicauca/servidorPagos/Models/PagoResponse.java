package co.edu.unicauca.servidorPagos.Models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa la respuesta del servidor de pagos cuando se intenta
 * registrar un nuevo pago asociado a una reacción.
 *
 * <p>Incluye el estado del pago (aceptado o rechazado) y un mensaje
 * descriptivo que puede usarse para logs o para depuración.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagoResponse {

    /**
     * Estado final del pago después de ser procesado por el servidor
     * de pagos (por ejemplo, ACEPTADO, TOKEN_REPETIDO, LIMITE_SUPERADO
     * o ERROR_SIMULADO).
     */
    private EstadoPago estado;

    /**
     * Mensaje descriptivo con información adicional sobre el resultado
     * del pago. Puede utilizarse para mostrar un eco en consola o
     * para facilitar la depuración durante las pruebas.
     */
    private String mensaje;

    /**
     * Monto total acumulado por el usuario después de procesar este
     * pago (solo tiene sentido cuando el estado es ACEPTADO).
     * Permite verificar fácilmente cuándo el usuario alcanza el
     * límite de $50 definido en el requerimiento.
     */
    private int totalAcumuladoUsuario;
}
