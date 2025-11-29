package co.edu.unicauca.servidorReacciones.capaPagosCliente;

import lombok.Data;

/**
 * Representa la respuesta del servidor de pagos cuando se solicita
 * la generación de un nuevo token.
 */
@Data
public class TokenResponse {

    /**
     * Valor único del token generado por el servidor de pagos.
     */
    private String token;
}
