package co.edu.unicauca.servidorReacciones.capaPagosCliente.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Petición que el servidor de reacciones enviará al servidor de pagos
 * para registrar un nuevo pago asociado a una reacción.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagoRequest {

    /**
     * Token único generado previamente por el servidor de pagos.
     */
    private String token;

    /**
     * Nickname del usuario que genera la reacción.
     */
    private String nickname;

    /**
     * Identificador de la canción asociada a la reacción.
     */
    private String idCancion;

    /**
     * Valor monetario de la reacción (en el requerimiento: 10).
     */
    private int valor;
}
