package co.edu.unicauca.servidorPagos.Repository;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositorio en memoria para almacenar información relacionada
 * con los pagos procesados por el servidor.
 *
 * <p>Responsabilidades principales:</p>
 * <ul>
 *   <li>Registrar y consultar tokens ya utilizados.</li>
 *   <li>Almacenar y consultar el total acumulado por usuario.</li>
 * </ul>
 *
 * <p>Internamente utiliza estructuras {@link ConcurrentHashMap} y
 * conjuntos concurrentes, adecuadas para un entorno multi-hilo
 * como el que maneja Spring Boot.</p>
 */
@Repository
public class PagosRepository {

    /**
     * Conjunto de tokens que ya han sido utilizados en pagos válidos.
     */
    private final Set<String> tokensUsados = ConcurrentHashMap.newKeySet();

    /**
     * Mapa que almacena el total acumulado por usuario.
     * Clave: nickname del usuario.
     * Valor: total en pesos de sus reacciones.
     */
    private final Map<String, Integer> totalPorUsuario = new ConcurrentHashMap<>();

    /**
     * Marca un token como utilizado, para evitar que sea usado
     * nuevamente en otro pago.
     *
     * @param token valor del token a registrar como usado
     */
    public void marcarTokenComoUsado(String token) {
        tokensUsados.add(token);
    }

    /**
     * Verifica si un token ya fue utilizado previamente.
     *
     * @param token valor del token a verificar
     * @return true si el token ya fue usado, false en caso contrario
     */
    public boolean esTokenUsado(String token) {
        return tokensUsados.contains(token);
    }

    /**
     * Obtiene el total acumulado actual para un usuario.
     *
     * @param nickname nickname del usuario
     * @return total acumulado o 0 si no tiene registros previos
     */
    public int obtenerTotalUsuario(String nickname) {
        return totalPorUsuario.getOrDefault(nickname, 0);
    }

    /**
     * Actualiza el total acumulado de un usuario con un nuevo valor.
     *
     * @param nickname    nickname del usuario
     * @param nuevoTotal  nuevo total acumulado a registrar
     */
    public void actualizarTotalUsuario(String nickname, int nuevoTotal) {
        totalPorUsuario.put(nickname, nuevoTotal);
    }
}
