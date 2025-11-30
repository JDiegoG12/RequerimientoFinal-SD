// Ubicación: co/edu/unicauca/servidorReacciones/capaModelos/NotificacionPrivada.java

package co.edu.unicauca.servidorReacciones.capaModelos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa un mensaje privado enviado desde el servidor a un único cliente.
 * Se utiliza para notificar errores (ej. saldo insuficiente) o dar
 * información específica que no debe ser vista por otros usuarios.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionPrivada {

    /**
     * Tipo de notificación para que el cliente pueda identificarla.
     * Ej: "ERROR_PAGO", "LIMITE_ALCANZADO", "INFO".
     */
    private String tipo;

    /**
     * Título breve de la notificación.
     */
    private String titulo;

    /**
     * Mensaje detallado para mostrar al usuario.
     */
    private String mensaje;

    @Override
    public String toString() {
        return "NotificacionPrivada{" +
                "tipo='" + tipo + '\'' +
                ", titulo='" + titulo + '\'' +
                ", mensaje='" + mensaje + '\'' +
                '}';
    }
}