package co.edu.unicauca.servidorReacciones.capaPagosCliente.models;

/**
 * Estado del pago devuelto por el servidor de pagos.
 *
 * <p>Debe coincidir con los valores definidos en el servidor de pagos
 * para que la deserialización JSON funcione correctamente.</p>
 */
public enum EstadoPago {

    ACEPTADO,
    TOKEN_REPETIDO,
    LIMITE_SUPERADO,
    ERROR_SIMULADO
}
