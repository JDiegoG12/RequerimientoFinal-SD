package co.edu.unicauca.servidorPagos.Models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa la respuesta que envía el servidor de pagos
 * cuando se solicita la generación de un nuevo token.
 *
 * <p>Este token será utilizado posteriormente para registrar
 * un pago asociado a una reacción. El servidor de pagos debe
 * garantizar que cada token solo pueda usarse una vez.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    /**
     * Valor único del token generado por el servidor de pagos.
     * Este valor se enviará luego en la petición de registro
     * de pago y se validará que no haya sido usado previamente.
     */
    private String token;
}
