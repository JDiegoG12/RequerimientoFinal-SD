package co.edu.unicauca.servidorPagos.Models;

/**
 * Enumeración que representa el estado de un pago
 * procesado por el servidor de pagos.
 *
 * <p>Se utiliza para indicar al servidor de reacciones si
 * el pago fue aceptado o rechazado, y en este último caso,
 * el motivo puede detallarse en la respuesta.</p>
 */
public enum EstadoPago {

    /**
     * El pago fue registrado correctamente en el servidor de pagos.
     */
    ACEPTADO,

    /**
     * El pago fue rechazado porque el token ya había sido utilizado
     * previamente en otro pago.
     */
    TOKEN_REPETIDO,

    /**
     * El pago fue rechazado porque el usuario alcanzó o superó
     * el límite máximo permitido (por ejemplo, $50 en reacciones).
     */
    LIMITE_SUPERADO,

    /**
     * El pago falló por una condición simulada de error en el servidor
     * de pagos (usado para probar los mecanismos de reintento).
     */
    ERROR_SIMULADO
}
