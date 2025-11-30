// Ubicación: co/edu/unicauca/servidorReacciones/capaPagosCliente/exceptions/PagoSimuladoFallidoException.java

package co.edu.unicauca.servidorReacciones.capaPagosCliente.exceptions;

/**
 * Excepción específica que se lanza cuando el servidor de pagos
 * devuelve un estado de ERROR_SIMULADO.
 *
 * Esto permite que el mecanismo de @Retryable se active específicamente
 * para este caso de fallo controlado, además de los errores de red.
 */
public class PagoSimuladoFallidoException extends RuntimeException {

    public PagoSimuladoFallidoException(String message) {
        super(message);
    }
}