package co.edu.unicauca.servidorReacciones.capaPagosCliente.models;

import lombok.Data;

/**
 * Respuesta que el servidor de pagos devuelve al registrar un pago.
 *
 * <p>Se utiliza en el servidor de reacciones para decidir si se debe
 * reenviar la reacción a los demás clientes o activar reintentos.</p>
 */
@Data
public class PagoResponse {

    /**
     * Estado final del pago después de ser procesado.
     */
    private EstadoPago estado;

    /**
     * Mensaje descriptivo con información adicional sobre el resultado.
     */
    private String mensaje;

    /**
     * Monto total acumulado por el usuario después de procesar
     * este pago (solo tiene sentido cuando el estado es ACEPTADO).
     */
    private int totalAcumuladoUsuario;
}
