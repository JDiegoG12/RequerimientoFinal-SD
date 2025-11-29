package co.edu.unicauca.servidorPagos.Models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa la petición que el servidor de reacciones envía
 * al servidor de pagos para registrar un nuevo pago asociado
 * a una reacción de un usuario.
 *
 * <p>Cada petición debe incluir el token previamente generado,
 * la identificación del usuario y de la canción, así como el
 * valor monetario de la reacción.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagoRequest {

    /**
     * Token único generado previamente por el servidor de pagos.
     * El servidor debe validar que este token no haya sido usado
     * en un pago anterior.
     */
    private String token;

    /**
     * Nickname o identificador del usuario que genera la reacción.
     * Se utilizará para acumular el total de pagos realizados por
     * este usuario y aplicar el límite máximo permitido.
     */
    private String nickname;

    /**
     * Identificador de la canción asociada a la reacción.
     * Permite, si se desea, llevar estadísticas por canción.
     */
    private String idCancion;

    /**
     * Valor monetario del pago asociado a la reacción.
     * En el requerimiento, cada reacción equivale a $10.
     */
    private int valor;
}
