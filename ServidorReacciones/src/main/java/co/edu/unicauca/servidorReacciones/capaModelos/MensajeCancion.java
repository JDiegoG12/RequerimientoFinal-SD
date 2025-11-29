package co.edu.unicauca.servidorReacciones.capaModelos;

import lombok.Data;

/**
 * Representa un mensaje intercambiado entre el cliente web y el servidor
 * de reacciones, asociado a una canción específica.
 * 
 * Cada mensaje incluye:
 * <ul>
 *   <li>El nickname del usuario que genera el evento.</li>
 *   <li>El identificador de la canción (por ejemplo, nombre o id interno).</li>
 *   <li>El tipo de evento: PLAY, PAUSE o REACCION.</li>
 *   <li>Un contenido opcional, usado principalmente para el tipo REACCION
 *       (por ejemplo: "like", "heart", "fire").</li>
 * </ul>
 * 
 * Esta clase se serializa/deserializa automáticamente a JSON cuando
 * viaja por WebSocket/STOMP entre el cliente y el servidor.
 */
@Data
public class MensajeCancion {

    /**
     * Nickname del usuario que está reproduciendo la canción
     * o que envía la reacción.
     */
    private String nickname;

    /**
     * Identificador de la canción a la que está asociado el evento.
     * Puede ser un id numérico o una cadena (por ejemplo, el nombre del archivo).
     */
    private String idCancion;

    /**
     * Tipo de evento que se está notificando.
     * Valores esperados:
     * <ul>
     *   <li>"PLAY": el usuario comienza o reanuda la reproducción.</li>
     *   <li>"PAUSE": el usuario pausa la reproducción.</li>
     *   <li>"REACCION": el usuario envía una reacción sobre la canción.</li>
     * </ul>
     */
    private String tipo;

    /**
     * Contenido adicional del mensaje.
     * Para el tipo "REACCION" almacena el tipo de reacción
     * (por ejemplo, "like", "heart", "clap").
     * Para los tipos "PLAY" y "PAUSE" puede ser null o una cadena descriptiva.
     */
    private String contenido;

    /**
     * Constructor por defecto requerido para la deserialización JSON.
     */
    public MensajeCancion() {
    }

    /**
     * Constructor completo para crear mensajes de forma manual
     * desde el servidor o en pruebas.
     *
     * @param nickname  nickname del usuario
     * @param idCancion identificador de la canción
     * @param tipo      tipo de evento (PLAY, PAUSE, REACCION)
     * @param contenido contenido adicional del mensaje
     */
    public MensajeCancion(String nickname, String idCancion, String tipo, String contenido) {
        this.nickname = nickname;
        this.idCancion = idCancion;
        this.tipo = tipo;
        this.contenido = contenido;
    }

    @Override
    public String toString() {
        return "MensajeCancion{nickname='" + nickname +
                "', idCancion='" + idCancion +
                "', tipo='" + tipo +
                "', contenido='" + contenido + "'}";
    }
}
